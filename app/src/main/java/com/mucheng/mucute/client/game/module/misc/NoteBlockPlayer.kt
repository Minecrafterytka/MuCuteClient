package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.LevelEvent
import org.cloudburstmc.protocol.bedrock.data.SoundEvent
import org.cloudburstmc.protocol.bedrock.packet.*
import kotlinx.serialization.Serializable
import java.util.UUID

class NoteBlockPlayer : Module("NoteBlockPlayer", ModuleCategory.Misc) {

    // Настройки
    private val playMusic by boolValue("play_music", true)
    private val tickDuration by intValue("tickDuration", 250, 100..500) // Интервал 250 мс

    // Данные нот
    @Serializable
    data class Song(val name: String, val tempo: Int, val notes: List<List<Note>>)

    @Serializable
    data class Note(val soundEvent: SoundEvent, val pitch: Int, val duration: Int)

    // Расширенная версия "Waiting" (первые ~15 секунд)
    private val Waiting = listOf(
        // 0–4 сек: Аккорды Cmaj → Fmaj → Gmaj → Am
        listOf(
            Note(SoundEvent.NOTE, 0, 25),   // C4
            Note(SoundEvent.NOTE, 4, 25),   // E4
            Note(SoundEvent.NOTE, 7, 25)    // G4
        ),
        listOf(),
        listOf(
            Note(SoundEvent.NOTE, 5, 25),   // F4
            Note(SoundEvent.NOTE, 9, 25),   // A4
            Note(SoundEvent.NOTE, 12, 25)   // C5
        ),
        listOf(),
        listOf(
            Note(SoundEvent.NOTE, 7, 25),   // G4
            Note(SoundEvent.NOTE, 11, 25),  // B4
            Note(SoundEvent.NOTE, 14, 25)   // D5
        ),
        listOf(),
        listOf(
            Note(SoundEvent.NOTE, 9, 25),   // A4
            Note(SoundEvent.NOTE, 12, 25),  // C5
            Note(SoundEvent.NOTE, 16, 25)   // E5
        ),
        listOf(),

        // 4–8 сек: Повторение аккордов с мелодией (G4, A4, F4, E4)
        listOf(
            Note(SoundEvent.NOTE, 0, 25),   // C4
            Note(SoundEvent.NOTE, 4, 25),   // E4
            Note(SoundEvent.NOTE, 7, 25),   // G4
            Note(SoundEvent.NOTE, 7, 12)    // G4 (мелодия)
        ),
        listOf(Note(SoundEvent.NOTE, 9, 12)),  // A4
        listOf(
            Note(SoundEvent.NOTE, 5, 25),   // F4
            Note(SoundEvent.NOTE, 9, 25),   // A4
            Note(SoundEvent.NOTE, 12, 25),  // C5
            Note(SoundEvent.NOTE, 5, 12)    // F4 (мелодия)
        ),
        listOf(Note(SoundEvent.NOTE, 4, 12)),  // E4
        listOf(
            Note(SoundEvent.NOTE, 7, 25),   // G4
            Note(SoundEvent.NOTE, 11, 25),  // B4
            Note(SoundEvent.NOTE, 14, 25)   // D5
        ),
        listOf(),
        listOf(
            Note(SoundEvent.NOTE, 9, 25),   // A4
            Note(SoundEvent.NOTE, 12, 25),  // C5
            Note(SoundEvent.NOTE, 16, 25)   // E5
        ),
        listOf(),

        // 8–12 сек: Вторая часть мелодии (C5, D5, B4, G4)
        listOf(
            Note(SoundEvent.NOTE, 0, 25),   // C4
            Note(SoundEvent.NOTE, 4, 25),   // E4
            Note(SoundEvent.NOTE, 7, 25),   // G4
            Note(SoundEvent.NOTE, 12, 12)   // C5 (мелодия)
        ),
        listOf(Note(SoundEvent.NOTE, 14, 12)),  // D5
        listOf(
            Note(SoundEvent.NOTE, 5, 25),   // F4
            Note(SoundEvent.NOTE, 9, 25),   // A4
            Note(SoundEvent.NOTE, 12, 25),  // C5
            Note(SoundEvent.NOTE, 11, 12)   // B4 (мелодия)
        ),
        listOf(Note(SoundEvent.NOTE, 7, 12)),  // G4
        listOf(
            Note(SoundEvent.NOTE, 7, 25),   // G4
            Note(SoundEvent.NOTE, 11, 25),  // B4
            Note(SoundEvent.NOTE, 14, 25)   // D5
        ),
        listOf(),
        listOf(
            Note(SoundEvent.NOTE, 9, 25),   // A4
            Note(SoundEvent.NOTE, 12, 25),  // C5
            Note(SoundEvent.NOTE, 16, 25)   // E5
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

                        // Назначаем эмоцию игроку
                        val emoteListPacket = EmoteListPacket()
                        emoteListPacket.runtimeEntityId = session.localPlayer.runtimeEntityId
                        emoteListPacket.pieceIds.add(UUID.fromString("8b6e1390-6622-4f9a-b9d2-2db9d8b6d7d4")) // Эмоция "Wave"
                        session.serverBound(emoteListPacket)

                        // Активируем эмоцию
                        val emotePacket = EmotePacket()
                        emotePacket.runtimeId = session.localPlayer.runtimeEntityId
                        emotePacket.emoteID = "8b6e1390-6622-4f9a-b9d2-2db9d8b6d7d4" // Эмоция "Wave"
                        emotePacket.flags = 0
                        session.serverBound(emotePacket)
                    }
                    else -> {
                        isRepeating = args.contains("repeat")
                        currentSong = listOf(
                            listOf(Note(SoundEvent.NOTE, 12, 25)),  // C5 (harp)
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
                        if (currentGroupIndex >= currentSong.size && !isRepeating) {
                            stopPlaying()
                        }
                    }
                } else if (isRepeating) {
                    currentGroupIndex = 0
                    accumulatedTicks = 0
                    playNoteGroup(currentSong[0])
                }
                lastNoteTime = currentTime
            }
        }
    }

    private fun playNoteGroup(noteGroup: List<Note>) {
        noteGroup.forEach { note ->
            // Воспроизведение звука
            val soundPacket = LevelSoundEventPacket()
            soundPacket.setSound(note.soundEvent)
            soundPacket.setPosition(Vector3f.from(
                session.localPlayer.vec3Position.x,
                session.localPlayer.vec3Position.y,
                session.localPlayer.vec3Position.z
            ))
            soundPacket.setExtraData(note.pitch.coerceIn(0, 24)) // Высота (0-24)
            soundPacket.setIdentifier("")
            soundPacket.setBabySound(false)
            soundPacket.setRelativeVolumeDisabled(false)
            soundPacket.setEntityUniqueId(-1L)
            session.serverBound(soundPacket)

            // Добавление частиц нот
            val particlePacket = LevelEventPacket()
            particlePacket.setType(LevelEvent.PARTICLE_NOTE)
            particlePacket.setPosition(Vector3f.from(
                session.localPlayer.vec3Position.x,
                session.localPlayer.vec3Position.y + 1.5f, // Чуть выше головы игрока
                session.localPlayer.vec3Position.z
            ))
            particlePacket.setData(note.pitch.coerceIn(0, 24)) // Цвет частицы зависит от высоты
            session.serverBound(particlePacket)

            // Отладка
            session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing: ${note.soundEvent}, pitch=${note.pitch}")
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
