package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File

/**
 *[LIRIC-MISTAKEN 2.0]
 * MusicManager: Motor musical unificado.
 * FIX: Cero Disk I/O (Variables en caché) y 100% Async-Safe con Kyori Adventure.
 */
class MusicManager(private val plugin: Mistaken) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var musicJob: Job? = null

    private val playlist = mutableListOf<Track>()
    private var currentTrack: Track? = null
    private var isPlaying = false

    // 🔥 FIX 1: Variables en Caché (RAM). ¡Nunca vuelvas a leer el disco duro en medio del juego!
    private var cachedVolume: Float = 0.6f
    private var cachedPitch: Float = 1.0f

    data class Track(val id: String, val duration: Int)

    init {
        loadMusicConfig()
        startMusicLoop()
    }

    fun loadMusicConfig() {
        val musicFile = File(plugin.dataFolder, "music.yml")
        if (!musicFile.exists()) plugin.saveResource("music.yml", false)

        val config = YamlConfiguration.loadConfiguration(musicFile)
        playlist.clear()

        // Guardamos el volumen y pitch en memoria RAM una sola vez
        cachedVolume = config.getDouble("music.volume", 0.6).toFloat()
        cachedPitch = config.getDouble("music.pitch", 1.0).toFloat()

        val list = config.getMapList("music.playlist")
        for (item in list) {
            val id = item["id"] as? String ?: continue
            val duration = (item["duration"] as? Number)?.toInt() ?: 60
            playlist.add(Track(id, duration))
        }

        plugin.componentLogger.info(plugin.mm.deserialize("<gray>[Music] <white>${playlist.size}</white> canciones listas.</gray>"))
    }

    private fun startMusicLoop() {
        musicJob = scope.launch {
            while (isActive && !plugin.isReady) delay(1000L)

            while (isActive) {
                val state = plugin.gameManager.currentState

                if (state == GameState.LOBBY || state == GameState.VOTING) {
                    if (!isPlaying && playlist.isNotEmpty()) playRandomTrack()
                } else {
                    if (isPlaying) stopAllMusic()
                }

                delay(1000L)
            }
        }
    }

    private suspend fun playRandomTrack() {
        val track = playlist.random()
        currentTrack = track
        isPlaying = true

        // 🔥 FIX 2: Usamos Kyori Adventure. No requiere 'Location', suena directo en el cliente.
        // Y como no usa Location, es 100% seguro enviarlo desde la corrutina sin laguear a Bukkit.
        val soundPacket = Sound.sound(Key.key(track.id), Sound.Source.RECORD, cachedVolume, cachedPitch)

        plugin.server.onlinePlayers.forEach { p ->
            p.playSound(soundPacket)
        }

        var remaining = track.duration
        while (remaining > 0 && isPlaying) {
            val state = plugin.gameManager.currentState
            if (state != GameState.LOBBY && state != GameState.VOTING) {
                stopAllMusic()
                break
            }
            delay(1000L)
            remaining--
        }

        isPlaying = false
    }

    private fun stopAllMusic() {
        isPlaying = false
        val trackToStop = currentTrack ?: return

        // Kyori Adventure también tiene detención de sonido asíncrona segura.
        val stopPacket = SoundStop.named(Key.key(trackToStop.id))

        plugin.server.onlinePlayers.forEach { p ->
            p.stopSound(stopPacket)
        }
        currentTrack = null
    }

    fun syncPlayer(player: Player) {
        val state = plugin.gameManager.currentState
        if (state != GameState.LOBBY && state != GameState.VOTING) return

        val track = currentTrack ?: return
        if (isPlaying) {
            // Se usa el caché, instantáneo y sin lag.
            val soundPacket = Sound.sound(Key.key(track.id), Sound.Source.RECORD, cachedVolume, cachedPitch)
            player.playSound(soundPacket)
        }
    }

    fun shutdown() {
        stopAllMusic()
        musicJob?.cancel()
    }
}
