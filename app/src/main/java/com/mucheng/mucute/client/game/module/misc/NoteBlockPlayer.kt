package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.SoundEvent
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket

class NoteBlockPlayer : Module("NoteBlockPlayer", ModuleCategory.Misc) {

    // Настройки
    private val playMusic by boolValue("play_music", true)
    private val volume by floatValue("volume", 1.0f, 0.0f..1.0f) // Для будущих расширений

    // Данные нот
    data class Note(val instrument: Byte, val key: Byte, val duration: Int)

    private val nokiaTune = listOf(
        Note(0, 64, 10), // E5
        Note(0, 62, 10), // D5
        Note(0, 54, 5),  // F#4
        Note(0, 56, 5),  // G#4
        Note(0, 61, 10), // C#5
        Note(0, 59, 10), // B4
        Note(0, 50, 5),  // D4
        Note(0, 52, 5),  // E4
        Note(0, 59, 10), // B4
        Note(0, 57, 10), // A4
        Note(0, 49, 5),  // C#4
        Note(0, 52, 5),  // E4
        Note(0, 57, 10)  // A4
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
            val tickDuration = 50L // 1 тик = 50 мс (20 тиков/сек)
            if (currentTime - lastNoteTime >= tickDuration) {
                accumulatedTicks++
                if (currentNoteIndex < nokiaTune.size) {
                    val note = nokiaTune[currentNoteIndex]
                    if (accumulatedTicks >= note.duration) {
                        playNote(note)
                        currentNoteIndex++
                        accumulatedTicks = 0
                    }
                } else {
                    stopPlaying()
                }
                lastNoteTime = currentTime
            }
        }
    }

    private fun playNote(note: Note) {
        // Инструмент (harp для простоты)
        val sound = when (note.instrument.toInt()) {
            0 -> SoundEvent.NOTE
            else -> SoundEvent.NOTE
        }

        // Высота ноты (key 33–57, преобразуем в 0–24)
        val key33 = (note.key - 33).coerceIn(0, 24)

        val packet = LevelSoundEventPacket().apply {
            setSound(sound)
            setPosition(session.localPlayer.vec3Position)
            setExtraData(key33) // Высота ноты (0–24)
            setEntityType(":") // Стандартное значение для звуков без сущности
            setBabySound(false)
            setRelativeVolumeDisabled(false)
        }
        session.serverBound(packet)
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing note: key=${note.key}, extraData=$key33")
    }

    private fun startPlaying() {
        isPlaying = true
        currentNoteIndex = 0
        accumulatedTicks = 0
        lastNoteTime = System.currentTimeMillis()
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
