package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import kotlinx.serialization.Serializable

class NoteBlockPlayer : Module("NoteBlockPlayer", ModuleCategory.Misc) {

    // Настройки
    private val playMusic by boolValue("play_music", true)
    private val tickDuration by intValue("tickDuration", 50, 10..500) // Темп для теста

    // Названия инструментов для логов
    private val instrumentNames = mapOf<Int, String>(
        0 to "Пианино/Арфа",
        1 to "Бас-барабан",
        2 to "Палочки",
        3 to "Малый барабан",
        4 to "Бас",
        5 to "Колокольчик",
        6 to "Флейта",
        7 to "Колокольчики",
        8 to "Гитара",
        9 to "Ксилофон",
        10 to "Железный ксилофон",
        11 to "Коровьи колокольчики",
        12 to "Диджериду",
        13 to "Электронный звук",
        14 to "Банджо",
        15 to "Плиньк"
    )

    // Данные нот
    @Serializable
    data class Song(val name: String, val tempo: Int, val notes: List<List<Note>>)

    @Serializable
    data class Note(val instrument: Int, val pitch: Int, val duration: Int)

    // Тестовый лист для проверки всех 16 инструментов с несколькими нотами
    private val InstrumentTest = listOf(
        listOf(Note(0, 12, 5), Note(0, 15, 5)),  // Пианино/Арфа (F#4, A4)
        listOf(Note(1, 12, 5), Note(1, 15, 5)),  // Бас-барабан
        listOf(Note(2, 12, 5), Note(2, 15, 5)),  // Палочки
        listOf(Note(3, 12, 5), Note(3, 15, 5)),  // Малый барабан
        listOf(Note(4, 12, 5), Note(4, 15, 5)),  // Бас
        listOf(Note(5, 12, 5), Note(5, 15, 5)),  // Колокольчик
        listOf(Note(6, 12, 5), Note(6, 15, 5)),  // Флейта
        listOf(Note(7, 12, 5), Note(7, 15, 5)),  // Колокольчики
        listOf(Note(8, 12, 5), Note(8, 15, 5)),  // Гитара
        listOf(Note(9, 12, 5), Note(9, 15, 5)),  // Ксилофон
        listOf(Note(10, 12, 5), Note(10, 15, 5)), // Железный ксилофон
        listOf(Note(11, 12, 5), Note(11, 15, 5)), // Коровьи колокольчики
        listOf(Note(12, 12, 5), Note(12, 15, 5)), // Диджериду
        listOf(Note(13, 12, 5), Note(13, 15, 5)), // Электронный звук
        listOf(Note(14, 12, 5), Note(14, 15, 5)), // Банджо
        listOf(Note(15, 12, 5), Note(15, 15, 5))  // Плиньк
    )

    private var isPlaying = false
    private var isRepeating = false
    private var currentGroupIndex = 0
    private var lastNoteTime: Long = 0
    private var accumulatedTicks: Int = 0
    private var currentSong: List<List<Note>> = InstrumentTest

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Обработка команды .playnote
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message.trim()
            if (message.startsWith(".playnote")) {
                interceptablePacket.intercept()
                val args = message.split(" ").drop(1) // Убираем .playnote и берём аргументы
                when {
                    args.contains("stop") -> {
                        stopPlaying()
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aInstrument Test stopped")
                    }
                    else -> {
                        isRepeating = args.contains("repeat")
                        startPlaying()
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Instrument Test${if (isRepeating) " with repeat" else " (once)"}")
                    }
                }
            }
        }

        // Проигрывание мелодии
        if (isEnabled && playMusic && isPlaying) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNoteTime >= tickDuration.toLong()) {
                accumulatedTicks++
                if (currentGroupIndex < currentSong.size) {
                    val noteGroup = currentSong[currentGroupIndex]
                    val maxDuration = noteGroup.maxOfOrNull { it.duration } ?: 1
                    if (accumulatedTicks >= maxDuration) {
                        playNoteGroup(noteGroup)
                        currentGroupIndex++
                        accumulatedTicks = 0
                    }
                } else if (isRepeating) {
                    currentGroupIndex = 0 // Начинаем заново
                    accumulatedTicks = 0
                    playNoteGroup(currentSong[0])
                } else {
                    stopPlaying()
                }
                lastNoteTime = currentTime
            }
        }
    }

    private fun playNoteGroup(noteGroup: List<Note>) {
        noteGroup.forEach { note ->
            val instrument = note.instrument.coerceIn(0, 15)
            val pitch = note.pitch.coerceIn(0, 15) // Ограничиваем высоту 0-15 (F#3-F#4)

            // Пробуем две позиции: позиция игрока и позиция под игроком
            val playerPos = Vector3i.from(
                session.localPlayer.vec3Position.x.toInt(),
                session.localPlayer.vec3Position.y.toInt(),
                session.localPlayer.vec3Position.z.toInt()
            )
            val belowPos = Vector3i.from(
                session.localPlayer.vec3Position.x.toInt(),
                session.localPlayer.vec3Position.y.toInt() - 1,
                session.localPlayer.vec3Position.z.toInt()
            )

            // Отправляем пакет для обеих позиций
            listOf(playerPos, belowPos).forEach { pos ->
                val packet = BlockEventPacket().apply {
                    blockPosition = pos
                    eventType = instrument // Инструмент (0-15)
                    eventData = pitch // Высота тона
                }
                session.serverBound(packet)

                // Расширенное логирование для отладки
                val instrumentName = instrumentNames[instrument] ?: "Неизвестный"
                session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing: $instrumentName, pitch: $pitch, position: $pos, eventType: ${packet.eventType}, eventData: ${packet.eventData}")
            }
        }
    }

    private fun startPlaying() {
        isPlaying = true
        currentGroupIndex = 0
        accumulatedTicks = 0
        lastNoteTime = System.currentTimeMillis()
    }

    private fun stopPlaying() {
        isPlaying = false
        isRepeating = false
        currentGroupIndex = 0
        accumulatedTicks = 0
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aInstrument Test stopped")
    }

    override fun onDisabled() {
        super.onDisabled()
        stopPlaying()
    }
}
