package liric.mistaken.game.managers.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.get
import pumpking.lib.color.ColorTranslator
import pumpking.lib.config.ConfigManager


class MusicManager(private val plugin: Mistaken) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var musicJob: Job? = null

    private val playlist = mutableListOf<Track>()
    private var currentLobbyTrack: Track? = null
    private var trackStartTime: Long = 0

    // Registro de qué canción está escuchando cada jugador (para no solapar)
    private val playersPlaying = ConcurrentHashMap.newKeySet<UUID>()

    private var cachedVolume: Float = 0.6f
    private var cachedPitch: Float = 1.0f

    data class Track(val id: String, val duration: Int)

    init {
        loadMusicConfig()
        startMusicLoop()
    }

    fun loadMusicConfig() {
        val configProvider = ConfigManager.get("config.yml")
        val config = configProvider.getRaw()
        playlist.clear()

        cachedVolume = config.getDouble("music.volume", 0.6).toFloat()
        cachedPitch = config.getDouble("music.pitch", 1.0).toFloat()

        val list = config.getMapList("music.playlist")
        for (item in list) {
            val id = item["id"] as? String ?: continue
            val duration = (item["duration"] as? Number)?.toInt() ?: 60
            playlist.add(Track(id, duration))
        }

        plugin.componentLogger.info(ColorTranslator.translate("[INFO] [Music] Multiarena System loaded with ${playlist.size} tracks."))
    }

    private fun startMusicLoop() {
        musicJob = scope.launch {
            while (isActive && !plugin.isReady) delay(1000L)

            while (isActive) {
                // 1. Decidir la canción actual para los que están en "espera" (Lobby/Votación/Break)
                if (currentLobbyTrack == null && playlist.isNotEmpty()) {
                    currentLobbyTrack = playlist.random()
                    trackStartTime = System.currentTimeMillis()
                }

                // 2. Evaluar a cada jugador individualmente
                for (player in plugin.server.onlinePlayers) {
                    val session = plugin.sessionManager.getSession(player)

                    // Si no tiene sesión (Lobby) o la sesión está en fase de espera
                    val state = session?.currentState ?: GameState.LOBBY
                    val shouldHearMusic = state == GameState.LOBBY || state == GameState.VOTING || state == GameState.BREAK

                    if (shouldHearMusic) {
                        if (!playersPlaying.contains(player.uniqueId)) {
                            playTrackForPlayer(player, currentLobbyTrack)
                        }
                    } else {
                        
                        if (playersPlaying.contains(player.uniqueId)) {
                            stopMusicForPlayer(player)
                        }
                    }
                }

                // 3. Manejar el tiempo de la canción actual (Global)
                currentLobbyTrack?.let { track ->
                    val elapsed = (System.currentTimeMillis() - trackStartTime) / 1000
                    if (elapsed >= track.duration) {
                        // Cambiar la canción para todos
                        currentLobbyTrack = playlist.random()
                        trackStartTime = System.currentTimeMillis()
                        // Vaciamos el set para que en el siguiente tick todos empiecen a escuchar la nueva
                        playersPlaying.clear()
                    }
                }
                
                delay(1000L)

                // Limpiar rastro de jugadores desconectados
                playersPlaying.removeIf { plugin.server.getPlayer(it) == null }
            }
        }
    }

    private fun playTrackForPlayer(player: Player, track: Track?) {
        if (track == null) return

        playersPlaying.add(player.uniqueId)
        val soundPacket = Sound.sound(Key.key(track.id), Sound.Source.RECORD, cachedVolume, cachedPitch)

        player.playSound(soundPacket)

        // Ya no removemos al jugador de playersPlaying asincrónicamente aquí,
        // ya que el bucle principal se encarga de reiniciar todo el set 
        // cuando la canción global termina.
    }

    fun stopMusicForPlayer(player: Player) {
        playersPlaying.remove(player.uniqueId)

        // Kyori detiene todos los sonidos de la categoría RECORD para este jugador
        player.stopSound(SoundStop.source(Sound.Source.RECORD))
    }

    /**
     * Llamado desde PlayerListener al entrar al server.
     */
    fun syncPlayer(player: Player) {
        val session = plugin.sessionManager.getSession(player)
        val state = session?.currentState ?: GameState.LOBBY

        if (state == GameState.LOBBY || state == GameState.VOTING || state == GameState.BREAK) {
            playTrackForPlayer(player, currentLobbyTrack)
        }
    }

    /**
     * Fuerza el cambio de canción (útil para comandos de admin).
     */
    fun skipTrack() {
        val oldTrack = currentLobbyTrack
        currentLobbyTrack = null
        playersPlaying.clear()

        plugin.server.onlinePlayers.forEach { p ->
            if (playersPlaying.contains(p.uniqueId)) {
                stopMusicForPlayer(p)
                // El bucle principal lo volverá a poner en el siguiente segundo
            }
        }
    }

    fun shutdown() {
        musicJob?.cancel()
        plugin.server.onlinePlayers.forEach { stopMusicForPlayer(it) }
        playersPlaying.clear()
    }
}
