package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket

class NoteBlockPlayer : Module("NoteBlockPlayer", ModuleCategory.Misc) {

    // Настройки
    private val playMusic by boolValue("play_music", true)
    private val volume by floatValue("volume", 1.0f, 0.0f..1.0f)

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
        val sound = when (note.instrument.toInt()) {
            0 -> "note.harp"
            else -> "note.harp"
        }
        val key33 = (note.key - 33).coerceIn(0, 24) // Ограничиваем диапазон Minecraft (33–57)
        val pitch = when (key33) {
            0 -> 0.5f
            1 -> 0.529732f
            2 -> 0.561231f
            3 -> 0.594604f
            4 -> 0.629961f
            5 -> 0.667420f
            6 -> 0.707107f
            7 -> 0.749154f
            8 -> 0.793701f
            9 -> 0.840896f
            10 -> 0.890899f
            11 -> 0.943874f
            12 -> 1.0f
            13 -> 1.059463f
            14 -> 1.122462f
            15 -> 1.189207f
            16 -> 1.259921f
            17 -> 1.334840f
            18 -> 1.414214f
            19 -> 1.498307f
            20 -> 1.587401f
            21 -> 1.681793f
            22 -> 1.781797f
            23 -> 1.887749f
            24 -> 2.0f
            else -> 1.0f
        }
        val packet = LevelSoundEventPacket().apply {
            this.sound = sound
            position = session.localPlayer.vec3Position
            this.pitch = pitch
            this.volume = this@NoteBlockPlayer.volume
        }
        session.serverBound(packet)
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing note: key=${note.key}, pitch=$pitch")
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
