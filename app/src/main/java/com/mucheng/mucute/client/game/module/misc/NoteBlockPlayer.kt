package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.SoundEvent
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import kotlinx.serialization.Serializable

class NoteBlockPlayer : Module("NoteBlockPlayer", ModuleCategory.Misc) {

    // Настройки
    private val playMusic by boolValue("play_music", true)
    private val tickDuration by intValue("tickDuration", 50, 10..500) // Настраиваемый темп (50 мс по умолчанию)

    // Карта инструментов
    private val instrumentMap = mapOf<Byte, String>(
        0.toByte() to ":",                          // 0: Пианино/Арфа
        1.toByte() to "minecraft:planks",           // 1: Бас-гитара
        2.toByte() to "minecraft:stone",            // 2: Бас-барабан
        3.toByte() to "minecraft:sand",             // 3: Малый барабан
        4.toByte() to "minecraft:glass",            // 4: Щелчки
        5.toByte() to "minecraft:gold_block",       // 5: Колокольчик
        6.toByte() to "minecraft:clay",             // 6: Флейта
        7.toByte() to "minecraft:packed_ice",       // 7: Колокольчики
        8.toByte() to "minecraft:wool",             // 8: Гитара
        9.toByte() to "minecraft:bone_block",       // 9: Ксилофон
        10.toByte() to "minecraft:iron_block",      // 10: Железный ксилофон
        11.toByte() to "minecraft:soul_sand",       // 11: Коровьи колокольчики
        12.toByte() to "minecraft:pumpkin",         // 12: Диджериду
        13.toByte() to "minecraft:emerald_block",   // 13: Электронный звук
        14.toByte() to "minecraft:hay_block",       // 14: Банджо
        15.toByte() to "minecraft:glowstone"        // 15: Плиньк
    )

    // Данные нот
    @Serializable
    data class Song(val name: String, val tempo: Int, val notes: List<List<Note>>)

    @Serializable
    data class Note(val instrument: Byte, val key: Byte, val duration: Int)

    // Расширенная версия Megalovania с группами нот
    private val Megalovania = listOf(
        // Интро (D-D-F-D-G-F-D-A#-A) x2
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(2, 33, 2)),  // D4, D3, Бас-барабан
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(3, 35, 2)),  // D4, D3, Малый барабан
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(2, 33, 2)),  // F4, F3, Бас-барабан
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(3, 35, 2)),  // D4, D3, Малый барабан
        listOf(Note(8, 55, 5), Note(1, 43, 5), Note(2, 33, 2)),  // G4, G3, Бас-барабан
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(3, 35, 2)),  // F4, F3, Малый барабан
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(2, 33, 2)),  // D4, D3, Бас-барабан
        listOf(Note(8, 58, 5), Note(1, 46, 5), Note(3, 35, 2)),  // A#4, A#3, Малый барабан
        listOf(Note(8, 57, 5), Note(1, 45, 5), Note(2, 33, 2)),  // A4, A3, Бас-барабан
        // Повтор интро
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(3, 35, 2)),
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(2, 33, 2)),
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(3, 35, 2)),
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(2, 33, 2)),
        listOf(Note(8, 55, 5), Note(1, 43, 5), Note(3, 35, 2)),
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(2, 33, 2)),
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(3, 35, 2)),
        listOf(Note(8, 58, 5), Note(1, 46, 5), Note(2, 33, 2)),
        listOf(Note(8, 57, 5), Note(1, 45, 5), Note(3, 35, 2)),
        // Основная тема (D-D-F-D-G-F-D-F-E-D)
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(2, 33, 2)),
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(3, 35, 2)),
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(2, 33, 2)),
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(3, 35, 2)),
        listOf(Note(8, 55, 5), Note(1, 43, 5), Note(2, 33, 2)),
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(3, 35, 2)),
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(2, 33, 2)),
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(3, 35, 2)),
        listOf(Note(8, 52, 5), Note(1, 40, 5), Note(2, 33, 2)),
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(3, 35, 2)),
        // Соло (C-A#-A-G-A#-A-G-F)
        listOf(Note(8, 48, 5), Note(1, 36, 5), Note(2, 33, 2)),
        listOf(Note(8, 58, 5), Note(1, 46, 5), Note(3, 35, 2)),
        listOf(Note(8, 57, 5), Note(1, 45, 5), Note(2, 33, 2)),
        listOf(Note(8, 55, 5), Note(1, 43, 5), Note(3, 35, 2)),
        listOf(Note(8, 58, 5), Note(1, 46, 5), Note(2, 33, 2)),
        listOf(Note(8, 57, 5), Note(1, 45, 5), Note(3, 35, 2)),
        listOf(Note(8, 55, 5), Note(1, 43, 5), Note(2, 33, 2)),
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(3, 35, 2)),
        // Продолжение соло (D-F-G-A-A#-A-G-F)
        listOf(Note(8, 50, 5), Note(1, 38, 5), Note(2, 33, 2)),
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(3, 35, 2)),
        listOf(Note(8, 55, 5), Note(1, 43, 5), Note(2, 33, 2)),
        listOf(Note(8, 57, 5), Note(1, 45, 5), Note(3, 35, 2)),
        listOf(Note(8, 58, 5), Note(1, 46, 5), Note(2, 33, 2)),
        listOf(Note(8, 57, 5), Note(1, 45, 5), Note(3, 35, 2)),
        listOf(Note(8, 55, 5), Note(1, 43, 5), Note(2, 33, 2)),
        listOf(Note(8, 53, 5), Note(1, 41, 5), Note(3, 35, 2))
    )

    private var isPlaying = false
    private var isRepeating = false
    private var currentGroupIndex = 0
    private var lastNoteTime: Long = 0
    private var accumulatedTicks: Int = 0

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Обработка команды .playnote
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message.trim()
            if (message.startsWith(".playnote")) {
                interceptablePacket.intercept()
                val args = message.split(" ").drop(1) // Убираем .playnote и берем аргументы
                when {
                    args.contains("repeat") -> {
                        isRepeating = true
                        startPlaying()
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Megalovania with repeat")
                    }
                    args.contains("stop") -> {
                        stopPlaying()
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aMegalovania stopped")
                    }
                    else -> {
                        isRepeating = false
                        startPlaying()
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Megalovania (once)")
                    }
                }
            }
        }

        // Проигрывание мелодии
        if (isEnabled && playMusic && isPlaying) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNoteTime >= tickDuration.toLong()) {
                accumulatedTicks++
                if (currentGroupIndex < Megalovania.size) {
                    val noteGroup = Megalovania[currentGroupIndex]
                    val maxDuration = noteGroup.maxOfOrNull { it.duration } ?: 1
                    if (accumulatedTicks >= maxDuration) {
                        playNoteGroup(noteGroup)
                        currentGroupIndex++
                        accumulatedTicks = 0
                    }
                } else if (isRepeating) {
                    currentGroupIndex = 0 // Начинаем заново
                    accumulatedTicks = 0
                    playNoteGroup(Megalovania[0]) // Проигрываем первую группу сразу
                } else {
                    stopPlaying()
                }
                lastNoteTime = currentTime
            }
        }
    }

    private fun playNoteGroup(noteGroup: List<Note>) {
        noteGroup.forEach { note ->
            val key33 = (note.key - 33).coerceIn(0, 24)
            val instrumentIdentifier = instrumentMap[note.instrument] ?: instrumentMap[0.toByte()] // По умолчанию Арфа

            val packet = LevelSoundEventPacket().apply {
                sound = SoundEvent.NOTE
                position = Vector3f.from(
                    session.localPlayer.vec3Position.x,
                    session.localPlayer.vec3Position.y,
                    session.localPlayer.vec3Position.z
                )
                extraData = key33
                identifier = instrumentIdentifier
                isBabySound = false
                isRelativeVolumeDisabled = false
            }
            session.serverBound(packet)
            session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing: key=${note.key}, pitch=$key33, instrument=${instrumentMap[note.instrument] ?: "Harp"}")
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
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aMegalovania stopped")
    }

    override fun onDisabled() {
        super.onDisabled()
        stopPlaying()
    }
}
