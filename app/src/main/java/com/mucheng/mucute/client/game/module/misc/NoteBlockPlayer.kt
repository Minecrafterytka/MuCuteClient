package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.SoundEvent
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import java.io.File

class NoteBlockPlayer : Module("NoteBlockPlayer", ModuleCategory.Misc) {

    // Настройки
    private val playMusic by boolValue("play_music", true)
    private val songFile by stringValue("song_file", "") // Имя JSON-файла (например, "bad_piggies.json")
    private val soundId by intValue("sound_id", 26, 0..100) // 26 = NOTE (Harp)
    private val tickDuration by intValue("tick_duration", 50, 10..500) // 50 мс

    // Данные нот
    @Serializable
    data class Song(val name: String, val tempo: Int, val notes: List<Note>)

    @Serializable
    data class Note(val instrument: Byte, val key: Byte, val duration: Int)

    private val badPiggies = listOf(
        Note(0, 50, 6), Note(0, 52, 3), Note(0, 50, 3), Note(0, 48, 6),
        Note(0, 50, 6), Note(0, 53, 6), Note(0, 52, 3), Note(0, 50, 3),
        Note(0, 48, 6), Note(0, 50, 6), Note(0, 50, 6), Note(0, 52, 3),
        Note(0, 50, 3), Note(0, 48, 6), Note(0, 50, 6), Note(0, 53, 6),
        Note(0, 55, 3), Note(0, 53, 3), Note(0, 52, 6), Note(0, 50, 12)
    )

    private var currentSong: Song? = null
    private var isPlaying = false
    private var currentNoteIndex = 0
    private var lastNoteTime: Long = 0
    private var accumulatedTicks: Int = 0

    // Путь к внутреннему хранилищу (замените на реальный путь, если API доступен)
    private val basePath: String by lazy {
        // Попробуем захардкодить путь для теста (замените на реальный API, если есть)
        "/data/user/0/com.mucheng.mucute/files/MuCute/songs/"
        // В идеале: context.getFilesDir().absolutePath + "/MuCute/songs/"
        // Если есть доступ к context через session или другой API, используйте его
    }

    override fun onEnabled() {
        super.onEnabled()
        loadSong()
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Обработка команды .playnote
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message
            if (message == ".playnote") {
                interceptablePacket.intercept()
                startPlaying()
                session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aStarting ${currentSong?.name ?: "Bad Piggies"}")
            }
        }

        // Проигрывание мелодии
        if (isEnabled && playMusic && isPlaying) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNoteTime >= tickDuration.toLong()) {
                accumulatedTicks++
                val notes = currentSong?.notes ?: badPiggies
                if (currentNoteIndex < notes.size) {
                    val note = notes[currentNoteIndex]
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

    private fun loadSong() {
        if (songFile.isNotEmpty()) {
            // Создаём папку, если её нет (во внутреннем хранилище)
            val songsDir = File(basePath)
            if (!songsDir.exists()) {
                try {
                    songsDir.mkdirs()
                    session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Created directory: $basePath")
                } catch (e: Exception) {
                    session.displayClientMessage("§l§b[NoteBlockPlayer] §r§cFailed to create directory: ${e.message}")
                }
            }

            val file = File(basePath, songFile)
            session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Trying to load: ${file.absolutePath}")
            if (file.exists()) {
                try {
                    val jsonString = file.readText()
                    currentSong = Json.decodeFromString<Song>(jsonString)
                    session.displayClientMessage("§l§b[NoteBlockPlayer] §r§aLoaded song: ${currentSong?.name}")
                } catch (e: Exception) {
                    session.displayClientMessage("§l§b[NoteBlockPlayer] §r§cFailed to load song: ${e.message}")
                }
            } else {
                session.displayClientMessage("§l§b[NoteBlockPlayer] §r§cSong file not found: ${file.absolutePath}")
            }
        } else {
            session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Using built-in Bad Piggies")
            currentSong = null // Использовать badPiggies
        }
    }

    private fun playNote(note: Note) {
        val key33 = (note.key - 33).coerceIn(0, 24)
        val packet = LevelSoundEventPacket().apply {
            sound = SoundEvent.NOTE // Harp, работает
            position = Vector3f.from(
                session.localPlayer.vec3Position.x,
                session.localPlayer.vec3Position.y,
                session.localPlayer.vec3Position.z
            )
            extraData = key33
            identifier = ":" // Работает
            isBabySound = false
            isRelativeVolumeDisabled = false
        }
        session.serverBound(packet)
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§7Playing note: key=${note.key}, pitch=$key33, sound_id=$soundId, tick_duration=$tickDuration")
    }

    private fun startPlaying() {
        isPlaying = true
        currentNoteIndex = 0
        accumulatedTicks = 0
        lastNoteTime = System.currentTimeMillis()
        session.displayClientMessage("Player position: ${session.localPlayer.vec3Position}")
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§a${currentSong?.name ?: "Bad Piggies"} started")
    }

    private fun stopPlaying() {
        isPlaying = false
        currentNoteIndex = 0
        accumulatedTicks = 0
        session.displayClientMessage("§l§b[NoteBlockPlayer] §r§a${currentSong?.name ?: "Bad Piggies"} stopped")
    }

    override fun onDisabled() {
        super.onDisabled()
        stopPlaying()
    }
}
