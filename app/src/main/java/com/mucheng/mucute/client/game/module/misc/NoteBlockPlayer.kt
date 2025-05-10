package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket
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
    data class Note(val soundName: String, val pitch: Float, val duration: Int)

    // Тестовый лист для экспериментов с PlaySoundPacket
    private val SoundTest = listOf(
        listOf(Note("block.note_block.harp", 1.0f, 10)),
        listOf(Note("block.note_block.bass", 1.0f, 10)),
        listOf(Note("block.note_block.basedrum", 1.0f, 10)),
        listOf(Note("block.note_block.snare", 1.0f, 10)),
        listOf(Note("block.note_block.hat", 1.0f, 10)),
        listOf(Note("block.note_block.bell", 1.0f, 10)),
        listOf(Note("block.note_block.flute", 1.0f, 10)),
        listOf(Note("block.note_block.chime", 1.0f, 10)),
        listOf(Note("block.note_block.guitar", 1.0f, 10)),
        listOf(Note("block.note_block.xylophone", 1.0f, 10)),
        listOf(Note("block.note_block.iron_xylophone", 1.0f, 10)),
        listOf(Note("block.note_block.cow_bell", 1.0f, 10)),
        listOf(Note("block.note_block.didgeridoo", 1.0f, 10)),
        listOf(Note("block.note_block.bit", 1.0f, 10)),
        listOf(Note("block.note_block.banjo", 1.0f, 10)),
        listOf(Note("block.note_block.pling", 1.0f, 10))
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
                val args = message.split(" ").drop(1)
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
                    currentGroupIndex = 0
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
            val packet = PlaySoundPacket().apply {
                name = note.soundName // Исправлено с soundName на name
                position = Vector3f.from(
                    session.localPlayer.vec3Position.x,
                    session.localPlayer.vec3Position.y,
                    session.localPlayer.vec3Position.z
                )
                volume = 1.0f
                pitch = note.pitch // Пробуем менять высоту (1.0f — оригинальная высота)
            }
            session.serverBound(packet)

            // Логирование
            session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing sound: ${note.soundName}, pitch: ${note.pitch}")
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
