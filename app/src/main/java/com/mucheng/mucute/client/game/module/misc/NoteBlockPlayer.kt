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
    // private val instrumentSetting by intValue("instrument", 0, 0..15) // Настройка для выбора инструмента по умолчанию/теста?

    // Данные нот
    // instrument: 0-4 (согласно старой схеме eventType BlockEventPacket)
    // key: высота ноты, например, по стандарту Minecraft (F#2=33), для преобразования в 0-24
    data class Note(val instrument: Byte, val key: Byte, val duration: Int)

    private val nokiaTune = listOf(
        // Пример: все ноты пока с инструментом 0 (Harp).
        // Чтобы проверить другие инструменты, меняйте первое значение (0-4)
        Note(0, 52, 6), // E4
        Note(0, 50, 6), // D4
        Note(0, 42, 3), // F#3
        Note(0, 44, 3), // G#3
        Note(0, 49, 6), // C#4
        Note(0, 47, 6), // B3
        Note(0, 38, 3), // D3
        Note(0, 40, 3), // E3
        Note(0, 47, 6), // B3
        Note(0, 45, 6), // A3
        Note(0, 37, 3), // C#3
        Note(0, 40, 3), // E3
        Note(0, 45, 6), // A3
        // Вторая часть (расширение мелодии)
        Note(0, 52, 6), // E4
        Note(0, 50, 6), // D4
        Note(0, 42, 3), // F#3
        Note(0, 44, 3), // G#3
        Note(0, 49, 6), // C#4
        Note(0, 47, 6), // B3
        Note(0, 38, 3), // D3
        Note(0, 40, 3), // E3
        Note(0, 47, 6), // B3
        Note(0, 45, 6), // A3
        Note(0, 45, 6)  // A3 (финал)
    )

    private var isPlaying = false
    private var currentNoteIndex = 0
    private var lastNoteTime: Long = 0
    private var accumulatedTicks: Int = 0

    // Используем переменную currentSong для выбора проигрываемой мелодии
    private var currentSong: List<Note> = nokiaTune // Инициализируем по умолчанию

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Обработка команды .playnote
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message.trim() // Убираем пробелы по краям
             if (message.startsWith(".playnote")) {
                interceptablePacket.intercept()
                val args = message.split(" ").drop(1) // Получаем аргументы после .playnote

                when {
                    args.contains("stop") -> {
                        stopPlaying()
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aPlayback stopped")
                    }
                     args.contains("nokia") -> {
                         currentSong = nokiaTune // Выбираем мелодию Nokia
                         startPlaying()
                         session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Nokia Tune")
                     }
                     args.contains("test") -> {
                         // Временно создаем тестовый список прямо здесь или используем ранее созданный
                          val instrumentTestSequence = listOf(
                              Note(0, 45, 10), // Инстр 0, A3
                              Note(1, 45, 10), // Инстр 1, A3
                              Note(2, 45, 10), // Инстр 2, A3
                              Note(3, 45, 10), // Инстр 3, A3
                              Note(4, 45, 10), // Инстр 4, A3
                              // Добавьте больше, если хотите проверить, что происходит с instrument > 4
                              // Note(5, 45, 10), // Инстр 5, A3 - возможно, не будет работать или даст странный звук
                              // Note(15, 45, 10) // Инстр 15, A3
                          )
                         currentSong = instrumentTestSequence // Выбираем тестовый список
                         startPlaying()
                         session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Instrument Test Sequence (0-4)")
                     }
                    else -> {
                        // По умолчанию, если нет аргументов, можно проигрывать Nokia или выдать помощь
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Usage: .playnote [stop|nokia|test]")
                    }
                }
            }
        }

        // Проигрывание мелодии
        if (isEnabled && playMusic && isPlaying) {
            val currentTime = System.currentTimeMillis()
            val tickDuration = 100L // Используем 100мс для "вашего" тика
            if (currentTime - lastNoteTime >= tickDuration) {
                accumulatedTicks++

                if (currentNoteIndex < currentSong.size) {
                     val note = currentSong[currentNoteIndex]

                    // Проверяем, достаточно ли тиков прошло для этой ноты
                    // accumulatedTicks * tickDuration - сколько времени прошло с момента игры предыдущей ноты
                    // note.duration * 100L - сколько времени ДОЛЖНО пройти до следующей ноты
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

        // --- Отправляем ОБА пакета: BlockEventPacket и LevelSoundEventPacket ---

        // 1. BlockEventPacket: Для симуляции действия блока и установки инструмента/начальной высоты
        // Используем старую, более вероятную из документации, схему для BlockEventPacket:
        // eventType для инструмента (0-4), eventData для высоты (0-15).
        // Ваш Note.instrument должен быть в диапазоне 0-4 для этого пакета.
        val instrumentEventType = note.instrument.toInt().coerceIn(0, 4) // Инструмент 0-4 через eventType
        // Преобразуем key (33-57) в высоту 0-15 для eventData BlockEventPacket'а
        val pitchEventData = (note.key - 33).coerceIn(0, 15) // Высота 0-15 через eventData

        val blockEventPacket = BlockEventPacket().apply {
            setBlockPosition(blockPosition)
            setEventType(instrumentEventType) // Инструмент (0-4)
            setEventData(pitchEventData)     // Высота тона (0-15)
        }
        session.serverBound(blockEventPacket)

        // Отладка BlockEventPacket
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Sent BlockEvent: pos=$blockPosition, instrument(eventType)=$instrumentEventType, pitch(eventData)=$pitchEventData")


        // 2. LevelSoundEventPacket: Для фактического воспроизведения звука с полной высотой тона (0-24)
        // Это стандартный способ играть звуки в мире.
        val fullPitch0_24 = (note.key - 33).coerceIn(0, 24) // Полная высота 0-24 для SoundEvent.NOTE

        val soundPacket = LevelSoundEventPacket().apply {
            setSound(SoundEvent.NOTE) // Используем универсальный SoundEvent.NOTE
            setPosition(playerPosition) // Позиция, где слышен звук
            setExtraData(fullPitch0_24) // Высота тона (0-24) через extraData
            setIdentifier("") // Можно оставить пустым или ":"
            setBabySound(false)
            setRelativeVolumeDisabled(false)
            setEntityUniqueId(-1L) // -1L или уникальный ID сущности
        }
        session.serverBound(soundPacket)

        // Отладка LevelSoundEventPacket
         session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Sent LevelSoundEvent: sound=NOTE, pitch(extraData)=$fullPitch0_24 (from key ${note.key})")
    }

    private fun startPlaying() {
        isPlaying = true
        currentNoteIndex = 0
        accumulatedTicks = 0
        lastNoteTime = System.currentTimeMillis() // Инициализация для первого тика
        session.displayClientMessage("Player position: ${session.localPlayer.vec3Position}")
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aPlayback started")
    }

    private fun stopPlaying() {
        isPlaying = false
        currentNoteIndex = 0
        accumulatedTicks = 0
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aPlayback stopped")
    }

    override fun onDisabled() {
        super.onDisabled()
        stopPlaying()
    }
}
