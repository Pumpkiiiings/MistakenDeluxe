package liric.mistaken.game.managers.audio

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
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
import java.util.concurrent.TimeUnit
import kotlin.collections.get

class MusicManager(private val plugin: Mistaken) {

    // En vez de Coroutines (que crashean al hacer Hot-Reload en Paper)
    // Usamos el AsyncScheduler nativo del servidor
    private var musicTask: ScheduledTask? = null
    private val playlist = mutableListOf<Track>()
    private var currentLobbyTrack: Track? = null

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
        val musicFile = File(plugin.dataFolder, "music.yml")
        if (!musicFile.exists()) plugin.saveResource("music.yml", false)

        val config = YamlConfiguration.loadConfiguration(musicFile)
        playlist.clear()

        cachedVolume = config.getDouble("music.volume", 0.6).toFloat()
        cachedPitch = config.getDouble("music.pitch", 1.0).toFloat()

        val list = config.getMapList("music.playlist")
        for (item in list) {
            val id = item["id"] as? String ?: continue
            val duration = (item["duration"] as? Number)?.toInt() ?: 60
            playlist.add(Track(id, duration))
        }

        plugin.componentLogger.info(plugin.mm.deserialize("<gray>[Music] Sistema cargado con <white>${playlist.size}</white> pistas.</gray>"))
    }

    private fun startMusicLoop() {
        // Corre cada segundo de manera asíncrona segura
        musicTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate

            // 1. Decidir la canción actual para los que están en "espera"
            if (currentLobbyTrack == null && playlist.isNotEmpty()) {
                currentLobbyTrack = playlist.random()
            }

            // 2. Evaluar a cada jugador individualmente
            for (player in plugin.server.onlinePlayers) {
                val session = plugin.sessionManager?.getSession(player)

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

            // Limpiar rastro de jugadores desconectados
            playersPlaying.removeIf { plugin.server.getPlayer(it) == null }

        }, 1L, 1L, TimeUnit.SECONDS)
    }

    private fun playTrackForPlayer(player: Player, track: Track?) {
        if (track == null) return

        playersPlaying.add(player.uniqueId)
        val soundPacket = Sound.sound(Key.key(track.id), Sound.Source.RECORD, cachedVolume, cachedPitch)

        // Enviar sonido de forma asíncrona mediante Kyori es 100% thread-safe
        player.playSound(soundPacket)

        // Programar de manera individual cuándo se termina la canción
        plugin.server.asyncScheduler.runDelayed(plugin, { _ ->
            if (currentLobbyTrack == track) {
                playersPlaying.remove(player.uniqueId)
            }
        }, track.duration.toLong(), TimeUnit.SECONDS)
    }

    private fun stopMusicForPlayer(player: Player) {
        playersPlaying.remove(player.uniqueId)
        player.stopSound(SoundStop.source(Sound.Source.RECORD))
    }

    fun syncPlayer(player: Player) {
        val session = plugin.sessionManager?.getSession(player)
        val state = session?.currentState ?: GameState.LOBBY

        if (state == GameState.LOBBY || state == GameState.VOTING || state == GameState.BREAK) {
            playTrackForPlayer(player, currentLobbyTrack)
        }
    }

    fun skipTrack() {
        if (playlist.isEmpty()) return
        currentLobbyTrack = playlist.random()

        plugin.server.onlinePlayers.forEach { p ->
            if (playersPlaying.contains(p.uniqueId)) {
                stopMusicForPlayer(p)
            }
        }
    }

    fun shutdown() {
        musicTask?.cancel()
        plugin.server.onlinePlayers.forEach { stopMusicForPlayer(it) }
        playersPlaying.clear()
    }
}