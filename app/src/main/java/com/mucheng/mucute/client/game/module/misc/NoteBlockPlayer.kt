package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.SoundEvent
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket

class NoteBlockPlayer : Module("NoteBlockPlayer", ModuleCategory.Misc) {

    // Настройки
    private val playMusic by boolValue("play_music", true)

    // Данные нот
    data class Note(val instrument: Byte, val key: Byte, val duration: Int)

private val AnySong = listOf(
    //Spider Dance
    Note(0, 36, 2), // C3
    Note(0, 40, 2), // E3
    Note(0, 43, 2), // G3
    Note(0, 48, 3), // C4
    Note(0, 36, 2), // C3
    Note(0, 40, 2), // E3
    Note(0, 43, 2), // G3
    Note(0, 48, 3), // C4
    Note(0, 52, 3), // E4
    Note(0, 52, 3), // E4
    Note(0, 52, 2), // E4
    Note(0, 53, 4), // F4
    Note(0, 52, 3), // E4
    Note(0, 50, 3), // D4
    Note(0, 48, 3), // C4
    Note(0, 48, 2), // C4
    Note(0, 48, 2), // C4
    Note(0, 50, 4), // D4
    Note(0, 48, 3), // C4
    Note(0, 47, 3), // B3
    Note(0, 45, 3), // A3
    Note(0, 45, 2), // A3
    Note(0, 45, 2), // A3
    Note(0, 47, 4), // B3
    Note(0, 45, 3), // A3
    Note(0, 43, 3), // G3
    Note(0, 42, 3), // F#3
    Note(0, 42, 2), // F#3
    Note(0, 40, 2), // E3
    Note(0, 38, 6), // D3
    Note(0, 52, 3), // E4
    Note(0, 52, 3), // E4
    Note(0, 52, 2), // E4
    Note(0, 53, 4), // F4
    Note(0, 52, 3), // E4
    Note(0, 50, 3), // D4
    Note(0, 48, 3), // C4
    Note(0, 48, 2), // C4
    Note(0, 48, 2), // C4
    Note(0, 50, 4), // D4
    Note(0, 48, 3), // C4
    Note(0, 47, 3), // B3
    Note(0, 45, 3), // A3
    Note(0, 45, 2), // A3
    Note(0, 45, 2), // A3
    Note(0, 47, 4), // B3
    Note(0, 45, 3), // A3
    Note(0, 43, 3), // G3
    Note(0, 48, 6), // C4
    Note(0, 48, 2), // C4
    Note(0, 50, 2), // D4
    Note(0, 52, 2), // E4
    Note(0, 53, 2), // F4
    Note(0, 52, 2), // E4
    Note(0, 50, 2), // D4
    Note(0, 48, 3), // C4
    Note(0, 47, 2), // B3
    Note(0, 48, 2), // C4
    Note(0, 50, 2), // D4
    Note(0, 52, 2), // E4
    Note(0, 50, 2), // D4
    Note(0, 48, 2), // C4
    Note(0, 47, 3), // B3
    Note(0, 45, 2), // A3
    Note(0, 47, 2), // B3
    Note(0, 48, 2), // C4
    Note(0, 50, 2), // D4
    Note(0, 48, 2), // C4
    Note(0, 47, 2), // B3
    Note(0, 45, 3), // A3
    Note(0, 43, 2), // G3
    Note(0, 45, 2), // A3
    Note(0, 47, 2), // B3
    Note(0, 48, 2), // C4
    Note(0, 47, 2), // B3
    Note(0, 45, 2), // A3
    Note(0, 43, 4), // G3
    Note(0, 48, 3), // C4
    Note(0, 48, 3), // C4
    Note(0, 48, 2), // C4
    Note(0, 48, 3), // C4
    Note(0, 45, 3), // A3
    Note(0, 45, 3), // A3
    Note(0, 45, 2), // A3
    Note(0, 45, 3), // A3
    Note(0, 43, 3), // G3
    Note(0, 43, 3), // G3
    Note(0, 43, 2), // G3
    Note(0, 43, 3), // G3
    Note(0, 42, 3), // F#3
    Note(0, 42, 3), // F#3
    Note(0, 42, 2), // F#3
    Note(0, 42, 6)  // F#3
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
                session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting Bad Piggies Theme")
            }
        }

        // Проигрывание мелодии
        if (isEnabled && playMusic && isPlaying) {
            val currentTime = System.currentTimeMillis()
            val tickDuration = 50L // 50 мс для более быстрого воспроизведения
            if (currentTime - lastNoteTime >= tickDuration) {
                accumulatedTicks++
                if (currentNoteIndex < AnySong.size) {
                    val note = AnySong[currentNoteIndex]
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
        // Высота ноты (key 33–57, преобразуем в 0–24)
        val key33 = (note.key - 33).coerceIn(0, 24)

        // Отправляем LevelSoundEventPacket для воспроизведения звука
        val packet = LevelSoundEventPacket().apply {
            sound = SoundEvent.NOTE // Harp, работает
            position = Vector3f.from(
                session.localPlayer.vec3Position.x,
                session.localPlayer.vec3Position.y,
                session.localPlayer.vec3Position.z
            )
            extraData = key33 // Высота ноты (0–24)
            identifier = ":" // Работает, оставляем
            isBabySound = false
            isRelativeVolumeDisabled = false
        }
        session.serverBound(packet)
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing note: key=${note.key}, pitch=$key33")
    }

    private fun startPlaying() {
        isPlaying = true
        currentNoteIndex = 0
        accumulatedTicks = 0
        lastNoteTime = System.currentTimeMillis()
        session.displayClientMessage("Player position: ${session.localPlayer.vec3Position}")
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aBad Piggies Theme started")
    }

    private fun stopPlaying() {
        isPlaying = false
        currentNoteIndex = 0
        accumulatedTicks = 0
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aBad Piggies Theme stopped")
    }

    override fun onDisabled() {
        super.onDisabled()
        stopPlaying()
    }
}
