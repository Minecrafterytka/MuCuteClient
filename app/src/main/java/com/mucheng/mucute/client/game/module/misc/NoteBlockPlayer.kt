package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector3i // Добавляем импорт для Vector3i
import org.cloudburstmc.protocol.bedrock.data.SoundEvent
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket // Добавляем импорт для BlockEventPacket

class NoteBlockPlayer : Module("NoteBlockPlayer", ModuleCategory.Misc) {

    // Настройки
    private val playMusic by boolValue("play_music", true)
    // instrument setting не будем использовать напрямую в playNote,
    // берем instrument из самой Note
    // private val instrumentSetting by intValue("instrument", 0, 0..15) // Настройка для выбора инструмента по умолчанию/теста?

    // Данные нот
    // instrument: 0-15 (Согласно вашему утверждению о 16 инструментах)
    // key: высота ноты, например, по стандарту Minecraft (F#2=33)
    data class Note(val instrument: Byte, val key: Byte, val duration: Int)

    private val AnySong = listOf(
        Note(0, 45, 20), // Инструмент 0 (Арфа?), A3, длительность 2 секунды
    Note(1, 45, 20), // Инструмент 1, A3
    Note(2, 45, 20), // Инструмент 2, A3
    Note(3, 45, 20), // Инструмент 3, A3
    Note(4, 45, 20), // Инструмент 4, A3
    Note(5, 45, 20), // Инструмент 5, A3
    Note(6, 45, 20), // Инструмент 6, A3
    Note(7, 45, 20), // Инструмент 7, A3
    Note(8, 45, 20), // Инструмент 8, A3
    Note(9, 45, 20), // Инструмент 9, A3
    Note(10, 45, 20),// Инструмент 10, A3
    Note(11, 45, 20),// Инструмент 11, A3
    Note(12, 45, 20),// Инструмент 12, A3
    Note(13, 45, 20),// Инструмент 13, A3
    Note(14, 45, 20),// Инструмент 14, A3
    Note(15, 45, 20) // Инструмент 15, A3
    )

    private var isPlaying = false
    private var currentNoteIndex = 0
    private var lastNoteTime: Long = 0
    private var accumulatedTicks: Int = 0

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Обработка команды .playnote
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message
            if (message == ".playnote") {
                interceptablePacket.intercept()
                startPlaying()
                session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Nokia Tune")
            }
        }

        // Проигрывание мелодии
        if (isEnabled && playMusic && isPlaying) {
            val currentTime = System.currentTimeMillis()
            val tickDuration = 100L // Используем 100мс для "вашего" тика
            if (currentTime - lastNoteTime >= tickDuration) {
                accumulatedTicks++

                if (currentNoteIndex < AnySong.size) {
                     val note = AnySong[currentNoteIndex]

                    // Проверяем, достаточно ли тиков прошло для этой ноты
                    if (accumulatedTicks * tickDuration >= note.duration * 100L) {
                         playNote(note) // Играем ноту
                         currentNoteIndex++ // Переходим к следующей
                         accumulatedTicks = 0 // Сбрасываем счетчик тиков
                    }

                } else {
                    // Мелодия закончилась
                    stopPlaying()
                }
                lastNoteTime = currentTime // Обновляем время для следующего цикла проверки
            }
        }
    }

    private fun playNote(note: Note) {
        // Позиция виртуального блока (например, позиция игрока)
        val playerPosition = session.localPlayer.vec3Position
        val blockPosition = Vector3i.from(playerPosition.x.toInt(), playerPosition.y.toInt(), playerPosition.z.toInt())

        // ---------- НОВАЯ ГИПОТЕЗА ----------
        // Согласно вашему утверждению: 16 инструментов = 0-15.
        // BlockEventPacket имеет поле eventData с диапазоном 0-15.
        // Попробуем использовать eventData для ИНСТРУМЕНТА (0-15).
        val instrumentEventData = note.instrument.toInt().coerceIn(0, 15) // Инструмент 0-15

        // Высота ноты: key 33 (F#2) соответствует pitch 0 в Minecraft (0-24 для SoundEvent.NOTE)
        // BlockEventPacket имеет поле eventType (0-4) и eventData (0-15).
        // Если eventData теперь инструмент, куда девать высоту?
        // Вариант 1: eventType (0-4) используется для высоты? (Очень грубое отображение 0-24 -> 0-4)
        // Вариант 2: Высота игнорируется в этом пакете? (Маловероятно)
        // Вариант 3: eventData кодирует И инструмент И высоту? (Возможно, но сложно без док)
        // Вариант 4: Высота все еще в LevelSoundEventPacket с SoundEvent.NOTE после BlockEventPacket? (Странно)

        // Давайте попробуем Вариант 1: Используем eventType (0-4) для высоты.
        // Это очень неточно, но это единственное оставшееся поле с маленьким диапазоном.
        // Отобразим pitch 0-24 в eventType 0-4. Например, делением.
        val basePitch0_24 = note.key - 33
        val pitchEventType = (basePitch0_24 / 5).coerceIn(0, 4) // Грубое отображение 0-24 на 0-4

        // Если это не сработает, возможно, высота остается в eventData, а инструмент
        // кодируется как-то иначе или в другом поле, или ваша информация относится не к этому пакету.
        // Но пробуем эту гипотезу, так как она соответствует вашему "0-15 инструментов".

        // Создание и отправка BlockEventPacket
        val blockEventPacket = BlockEventPacket().apply {
            setBlockPosition(blockPosition)
            setEventType(pitchEventType)     // Попытка использовать eventType для высоты (0-4)
            setEventData(instrumentEventData) // Использовать eventData для ИНСТРУМЕНТА (0-15)
        }
        session.serverBound(blockEventPacket)

        // Отладка
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Sending BlockEvent: pos=$blockPosition, instrument(eventData)=$instrumentEventData (from note.instrument ${note.instrument}), pitch_mapped(eventType)=$pitchEventType (from key ${note.key})")

        // В этой гипотезе LevelSoundEventPacket, вероятно, не нужен для контроля инструмента/высоты
        // поскольку мы пытаемся сделать это через BlockEventPacket.

    }

    private fun startPlaying() {
        isPlaying = true
        currentNoteIndex = 0
        accumulatedTicks = 0
        lastNoteTime = System.currentTimeMillis() // Инициализация для первого тика
        session.displayClientMessage("Player position: ${session.localPlayer.vec3Position}")
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aNokia Tune started")
    }

    private fun stopPlaying() {
        isPlaying = false
        currentNoteIndex = 0
        accumulatedTicks = 0
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aNokia Tune stopped")
    }

    override fun onDisabled() {
        super.onDisabled()
        stopPlaying()
    }
}
