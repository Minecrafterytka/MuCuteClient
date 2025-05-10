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
    private val tickDuration by intValue("tickDuration", 50, 10..500) // Темп для теста

    // Данные нот
    @Serializable
    data class Song(val name: String, val tempo: Int, val notes: List<List<Note>>)

    @Serializable
    data class Note(val sound: SoundEvent, val identifier: String, val pitch: Int, val duration: Int)

    // Тестовый лист для экспериментов с разными identifier
    private val SoundTest = listOf(
        // Тестируем только SoundEvent.NOTE с разными identifier
        listOf(Note(SoundEvent.NOTE, "", 12, 10)),               // Пустой identifier (ожидаем пианино/арфу)
        listOf(Note(SoundEvent.NOTE, "harp", 12, 10)),           // Прямое название инструмента
        listOf(Note(SoundEvent.NOTE, "bass", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "bd", 12, 10)),             // Бас-барабан (сокращение)
        listOf(Note(SoundEvent.NOTE, "snare", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "hat", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "bell", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "flute", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "chime", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "guitar", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "xylophone", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "iron_xylophone", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "cow_bell", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "didgeridoo", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "bit", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "banjo", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "pling", 12, 10)),
        // Тестируем identifier в формате minecraft:block
        listOf(Note(SoundEvent.NOTE, "minecraft:planks", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "minecraft:stone", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "minecraft:sand", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "minecraft:glass", 12, 10)),
        listOf(Note(SoundEvent.NOTE, "minecraft:gold_block", 12, 10))
    )

    private var isPlaying = false
    private var isRepeating = false
    private var currentGroupIndex = 0
    private var lastNoteTime: Long = 0
    private var accumulatedTicks: Int = 0
    private var currentSong: List<List<Note>> = SoundTest

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
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aSound Test stopped")
                    }
                    else -> {
                        isRepeating = args.contains("repeat")
                        startPlaying()
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Sound Test${if (isRepeating) " with repeat" else " (once)"}")
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
            val pitch = note.pitch.coerceIn(0, 24) // Ограничиваем высоту 0-24

            val packet = LevelSoundEventPacket().apply {
                sound = note.sound
                position = Vector3f.from(
                    session.localPlayer.vec3Position.x,
                    session.localPlayer.vec3Position.y,
                    session.localPlayer.vec3Position.z
                )
                extraData = pitch
                identifier = note.identifier
                isBabySound = false
                isRelativeVolumeDisabled = false
            }
            session.serverBound(packet)

            // Логирование для теста
            session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing identifier: ${note.identifier}, pitch: $pitch")
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
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aSound Test stopped")
    }

    override fun onDisabled() {
        super.onDisabled()
        stopPlaying()
    }
}
