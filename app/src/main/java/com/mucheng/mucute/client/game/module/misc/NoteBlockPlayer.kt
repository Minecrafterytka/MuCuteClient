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

    private val nokiaTune = listOf(
        Note(0, 52, 10), // E4
        Note(0, 50, 10), // D4
        Note(0, 42, 5),  // F#3
        Note(0, 44, 5),  // G#3
        Note(0, 49, 10), // C#4
        Note(0, 47, 10), // B3
        Note(0, 38, 5),  // D3
        Note(0, 40, 5),  // E3
        Note(0, 47, 10), // B3
        Note(0, 45, 10), // A3
        Note(0, 37, 5),  // C#3
        Note(0, 40, 5),  // E3
        Note(0, 45, 10)  // A3
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
            val tickDuration = 200L // 200 мс для снижения частоты пакетов
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
        // Высота ноты (key 33–57, преобразуем в 0–24)
        val key33 = (note.key - 33).coerceIn(0, 24)

        // Отправляем LevelSoundEventPacket для воспроизведения звука
        val packet = LevelSoundEventPacket().apply {
            sound = SoundEvent.NOTE
            position = Vector3f.from(
                session.localPlayer.vec3Position.x,
                session.localPlayer.vec3Position.y,
                session.localPlayer.vec3Position.z
            )
            extraData = key33 // Высота ноты (0–24)
            identifier = ":" // Стандартный идентификатор
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
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aNokia Tune started")
        session.displayClientMessage("Player position: ${session.localPlayer.vec3Position}")
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
