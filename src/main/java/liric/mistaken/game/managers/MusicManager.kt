package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.mainThread
import org.bukkit.Bukkit
import org.bukkit.SoundCategory
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * MusicManager: Motor musical unificado para fases de espera.
 * FIX: Carga de configuración desde la raíz del plugin (music.yml).
 */
class MusicManager(private val plugin: Mistaken) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var musicJob: Job? = null

    private val playlist = mutableListOf<Track>()
    private var currentTrack: Track? = null
    private var isPlaying = false

    data class Track(val id: String, val duration: Int)

    init {
        loadMusicConfig()
        startMusicLoop()
    }

    /**
     * Carga la lista de canciones desde el archivo music.yml en la raíz.
     */
    fun loadMusicConfig() {
        val musicFile = File(plugin.dataFolder, "music.yml")

        // Si por alguna razón no existe, intentamos extraerlo del JAR
        if (!musicFile.exists()) {
            plugin.saveResource("music.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(musicFile)
        playlist.clear()

        // Leemos la lista de mapas del YAML
        val list = config.getMapList("music.playlist")
        for (item in list) {
            val id = item["id"] as? String ?: continue
            val duration = (item["duration"] as? Number)?.toInt() ?: 60
            playlist.add(Track(id, duration))
        }

        plugin.componentLogger.info(plugin.mm.deserialize("<gray>[Music] <white>${playlist.size}</white> canciones cargadas desde la raíz correctamente.</gray>"))
    }

    private fun startMusicLoop() {
        musicJob = scope.launch {
            // Esperar a que el core esté listo (Base de datos, etc.)
            while (isActive && !plugin.isReady) delay(1000L)

            while (isActive) {
                val state = plugin.gameManager.currentState

                // --- LÓGICA DE ACTIVACIÓN ---
                if (state == GameState.LOBBY || state == GameState.VOTING) {
                    if (!isPlaying) {
                        playRandomTrack()
                    }
                } else {
                    // Si ya empezó la partida (STARTING o INGAME), matamos el sonido
                    if (isPlaying) stopAllMusic()
                }

                delay(1000L) // Chequeo ligero cada segundo (0 lag)
            }
        }
    }

    private suspend fun playRandomTrack() {
        if (playlist.isEmpty()) return

        val track = playlist.random()
        currentTrack = track
        isPlaying = true

        // Volvemos a leer volumen/pitch por si el admin los cambió en caliente
        val musicFile = File(plugin.dataFolder, "music.yml")
        val config = YamlConfiguration.loadConfiguration(musicFile)

        val volume = config.getDouble("music.volume", 0.6).toFloat()
        val pitch = config.getDouble("music.pitch", 1.0).toFloat()

        // Enviar sonido en el hilo principal (API de Bukkit)
        withContext(plugin.mainThread) {
            Bukkit.getOnlinePlayers().forEach { p ->
                p.playSound(p.location, track.id, SoundCategory.RECORDS, volume, pitch)
            }
        }

        // --- SISTEMA DE ESPERA INTELIGENTE ---
        var remaining = track.duration
        while (remaining > 0 && isPlaying) {
            val state = plugin.gameManager.currentState
            // Si el estado ya no es de espera, cortamos el sonido en seco
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

        // Usamos el dispatcher para tocar la API de Bukkit
        scope.launch(plugin.mainThread) {
            Bukkit.getOnlinePlayers().forEach { p ->
                p.stopSound(trackToStop.id, SoundCategory.RECORDS)
            }
            currentTrack = null
        }
    }

    fun syncPlayer(player: Player) {
        val state = plugin.gameManager.currentState
        if (state != GameState.LOBBY && state != GameState.VOTING) return

        val track = currentTrack ?: return
        if (isPlaying) {
            val musicFile = File(plugin.dataFolder, "music.yml")
            val config = YamlConfiguration.loadConfiguration(musicFile)
            val volume = config.getDouble("music.volume", 0.6).toFloat()
            val pitch = config.getDouble("music.pitch", 1.0).toFloat()

            player.playSound(player.location, track.id, SoundCategory.RECORDS, volume, pitch)
        }
    }

    fun shutdown() {
        stopAllMusic()
        musicJob?.cancel()
    }
}
