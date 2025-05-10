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
    private val tickDuration by intValue("tickDuration", 100, 50..200) // Темп для "Waiting" (~60 BPM)

    // Данные нот
    @Serializable
    data class Song(val name: String, val tempo: Int, val notes: List<List<Note>>)

    @Serializable
    data class Note(val soundName: String, val pitch: Float, val duration: Int)

    // Расширенная версия "Waiting" (первые ~15 секунд)
    private val Waiting = listOf(
        // 0–4 сек: Аккорды Cmaj → Fmaj → Gmaj → Am
        listOf(
            Note("note.harp", 0.5f, 20),   // C4
            Note("note.harp", 0.63f, 20),  // E4
            Note("note.harp", 0.79f, 20)   // G4
        ),
        listOf(),
        listOf(
            Note("note.harp", 0.67f, 20),  // F4
            Note("note.harp", 0.89f, 20),  // A4
            Note("note.harp", 1.0f, 20)    // C5
        ),
        listOf(),
        listOf(
            Note("note.harp", 0.79f, 20),  // G4
            Note("note.harp", 0.94f, 20),  // B4
            Note("note.harp", 1.06f, 20)   // D5
        ),
        listOf(),
        listOf(
            Note("note.harp", 0.89f, 20),  // A4
            Note("note.harp", 1.0f, 20),   // C5
            Note("note.harp", 1.26f, 20)   // E5
        ),
        listOf(),

        // 4–8 сек: Повторение аккордов с мелодией (G4, A4, F4, E4)
        listOf(
            Note("note.harp", 0.5f, 20),   // C4
            Note("note.harp", 0.63f, 20),  // E4
            Note("note.harp", 0.79f, 20),  // G4
            Note("note.bell", 0.79f, 10)   // G4 (мелодия)
        ),
        listOf(Note("note.bell", 0.89f, 10)),  // A4
        listOf(
            Note("note.harp", 0.67f, 20),  // F4
            Note("note.harp", 0.89f, 20),  // A4
            Note("note.harp", 1.0f, 20),   // C5
            Note("note.bell", 0.67f, 10)   // F4 (мелодия)
        ),
        listOf(Note("note.bell", 0.63f, 10)),  // E4
        listOf(
            Note("note.harp", 0.79f, 20),  // G4
            Note("note.harp", 0.94f, 20),  // B4
            Note("note.harp", 1.06f, 20)   // D5
        ),
        listOf(),
        listOf(
            Note("note.harp", 0.89f, 20),  // A4
            Note("note.harp", 1.0f, 20),   // C5
            Note("note.harp", 1.26f, 20)   // E5
        ),
        listOf(),

        // 8–12 сек: Вторая часть мелодии (C5, D5, B4, G4)
        listOf(
            Note("note.harp", 0.5f, 20),   // C4
            Note("note.harp", 0.63f, 20),  // E4
            Note("note.harp", 0.79f, 20),  // G4
            Note("note.bell", 1.0f, 10)    // C5 (мелодия)
        ),
        listOf(Note("note.bell", 1.06f, 10)),  // D5
        listOf(
            Note("note.harp", 0.67f, 20),  // F4
            Note("note.harp", 0.89f, 20),  // A4
            Note("note.harp", 1.0f, 20),   // C5
            Note("note.bell", 0.94f, 10)   // B4 (мелодия)
        ),
        listOf(Note("note.bell", 0.79f, 10)),  // G4
        listOf(
            Note("note.harp", 0.79f, 20),  // G4
            Note("note.harp", 0.94f, 20),  // B4
            Note("note.harp", 1.06f, 20)   // D5
        ),
        listOf(),
        listOf(
            Note("note.harp", 0.89f, 20),  // A4
            Note("note.harp", 1.0f, 20),   // C5
            Note("note.harp", 1.26f, 20)   // E5
        ),
        listOf()
    )

    private var isPlaying = false
    private var isRepeating = false
    private var currentGroupIndex = 0
    private var lastNoteTime: Long = 0
    private var accumulatedTicks: Int = 0
    private var currentSong: List<List<Note>> = Waiting

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
                    args.contains("waiting") -> {
                        isRepeating = args.contains("repeat")
                        currentSong = Waiting
                        startPlaying()
                        val repeatMessage = if (isRepeating) " with repeat" else " (once)"
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Waiting by C418$repeatMessage")
                    }
                    else -> {
                        isRepeating = args.contains("repeat")
                        currentSong = listOf(
                            listOf(Note("note.harp", 1.0f, 10)),
                            listOf(Note("note.bass", 1.0f, 10)),
                            listOf(Note("note.bell", 1.0f, 10)),
                            listOf(Note("note.snare", 1.0f, 10)),
                            listOf(Note("note.hat", 1.0f, 10))
                        )
                        startPlaying()
                        val repeatMessage = if (isRepeating) " with repeat" else " (once)"
                        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Sound Test$repeatMessage")
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
            val packet = PlaySoundPacket()
            packet.setSound(note.soundName)
            packet.setPosition(Vector3f.from(
                session.localPlayer.vec3Position.x,
                session.localPlayer.vec3Position.y,
                session.localPlayer.vec3Position.z
            ))
            packet.setVolume(1.0f)
            packet.setPitch(note.pitch)
            session.serverBound(packet)
            session.clientBound(packet)

            // Отладка
            session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing: ${note.soundName}, pitch=${note.pitch}")
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
