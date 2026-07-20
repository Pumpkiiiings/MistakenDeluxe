package liric.mistaken.game.logic

import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.game.entities.GeoffreyEXE
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

class GameStateController(private val game: GameSession) {

    // Cache local para el resultado de la partida
    private var lastKillerWon = false

    // Referencia a Geoffrey para poder eliminarlo cuando acaba la partida
    private var geoffreyEntity: GeoffreyEXE? = null

    fun startBreakProcess() {
        val venimosDePartida = game.currentState == GameState.ENDING

        if (venimosDePartida) {
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.limpiarMapa()
            
            // Usamos leaveSession para cada jugador para que maneje el teleport, la visibilidad
            // y, en caso de GAME_SERVER, el envío al proxy.
            val playersToLeave = game.getPlayers().toList()
            playersToLeave.forEach { player ->
                game.plugin.sessionManager.leaveSession(player)
            }
            
            // Programar la destrucción de la sesión
            game.plugin.server.globalRegionScheduler.runDelayed(game.plugin, { _ ->
                game.plugin.sessionManager.destroySession(game.id)
            }, 80L)
            return
        }

        if (game.currentState == GameState.BREAK) return
        game.currentState = GameState.BREAK

        game.timer = game.plugin.config.getInt("settings.break-duration", 10)

        // --- LIMPIEZA POST-CINEMÃTICA ---
        if (venimosDePartida) {
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.limpiarMapa()
            game.playerController.teleportAllToLobby()
        }

        game.broadcastLocalized("game.break-start")
    }

    fun startVotingProcess() {
        if (game.currentState == GameState.VOTING) return
        game.currentState = GameState.VOTING
        game.timer = game.plugin.config.getInt("settings.voting-duration", 30)

        game.broadcastLocalized("voting.started")
        game.plugin.arenaManager.getArenas().keys.forEach { map ->
            game.broadcastLocalized("voting.map-option", Placeholder.parsed("map", map))
        }
    }

    fun handleStartingSequence() {
        val online = game.plugin.server.onlinePlayers
        when (game.timer) {
            12 -> online.forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 0.5f) }
            10 -> {
                game.broadcastLocalized("game.mode-reveal-start")
                online.forEach { it.playSound(it.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.1f) }
            }
            8 -> {
                // Revelar modo
                game.broadcastLocalized("game.mode-selected", Placeholder.parsed("mode", game.currentMode.name))
                game.uiController.playModeTitle(online)

                // 🔥 REPRODUCIR INTRO DEL ASESINO 🔥
                val killer = game.getCurrentAsesino()
                if (killer != null && killer.isOnline) {
                    val killerClass = game.plugin.asesinoManager.getKillerOfPlayer(killer)
                    if (killerClass != null) {
                        game.plugin.cinematicManager.playKillerIntro(killer, killerClass)
                    }
                }
            }
            0 -> {
                game.currentState = GameState.INGAME
                game.timer = game.plugin.config.getInt("settings.game-duration", 300)
                game.broadcastLocalized("game.hunt-start")

                // 🔥 RESTAURAR VISTAS
                online.forEach { p ->
                    if (p.gameMode == GameMode.SPECTATOR && !game.plugin.spectatorManager.isSpectator(p)) {
                        p.spectatorTarget = null // Limpiamos primero en modo SPECTATOR
                        p.gameMode = GameMode.SURVIVAL // Y luego lo pasamos a SURVIVAL
                    }
                }
            }
        }
    }

    // 🔥 NUEVA FUNCIÓN: Invocación Programada de Geoffrey
    fun checkGeoffreySpawn() {
        // Solo ocurre en el modo INITIALIZES y exactamente a los 290 segundos (10s después del inicio)
        if (game.currentMode == MistakenMode.INITIALIZES && game.timer == 290) {

            // 1. Títulos de Terror a todos los jugadores (Incluyendo el Killer)
            val title = pumpking.lib.color.ColorTranslator.translate("<dark_red><bold><obfuscated>||</obfuscated> ¡GEOFFREY ESTÃ  AQUÃ ! <obfuscated>||</obfuscated>")
            val subtitle = pumpking.lib.color.ColorTranslator.translate("<dark_gray>Nadie sobrevivirá...")
            val times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(500))

            game.plugin.server.onlinePlayers.forEach { p ->
                p.showTitle(Title.title(title, subtitle, times))
                p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f)
                p.playSound(p.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.5f)

                // Efecto de Estática (Ceguera + Nausea fugaz)
                p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 1, false, false, false))
            }

            // 2. Invocar la entidad en el centro (Spawn de Survivors o del Killer)
            val spawnLoc = game.getCurrentAsesino()?.location ?: game.plugin.server.onlinePlayers.firstOrNull()?.location

            if (spawnLoc != null) {
                // Hacemos que spawnee en el aire para que baje volando/cazando
                val geoffreyLoc = spawnLoc.clone().add(0.0, 15.0, 0.0)

                // Efecto de aparición en el cielo
                geoffreyLoc.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, geoffreyLoc, 2)
                geoffreyLoc.world.playSound(geoffreyLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.5f)

                geoffreyEntity = GeoffreyEXE(game.plugin)
                geoffreyEntity?.spawn(geoffreyLoc)
            }
        }
    }

    fun startInGame() {
        val arenas = game.plugin.arenaManager.getArenas()
        val winner = game.voteManager.getWinningMap(arenas) ?: run { resetToLobby(null); return }
        val arena = game.plugin.arenaManager.getArena(winner) ?: run { resetToLobby(null); return }

        game.currentState = GameState.STARTING
        game.currentMapName = winner

        game.plugin.mapManager.loadArenaWorld(winner).thenAccept { aspWorld ->
            game.plugin.server.globalRegionScheduler.run(game.plugin) { _ ->
                if (aspWorld == null) {
                    resetToLobby(null)
                    return@run
                }

                game.timer = 15
                determineGameMode()

                arena.asesinoSpawn?.world = aspWorld
                arena.survivorSpawns.forEach { it.world = aspWorld }

                val genLocations = arena.generators.map { it.clone().apply { world = aspWorld } }
                game.plugin.generatorManager.prepareArenaGenerators(genLocations)

                game.playerController.setupPlayers(arena)
                game.broadcastLocalized("game.map-loaded", Placeholder.parsed("map", winner))
            }
        }
    }

    private fun determineGameMode() {
        if (game.modeForced) return
        val onlineCount = game.plugin.server.onlinePlayers.count { !game.plugin.isIgnored(it) }

        if (onlineCount < 3) {
            game.currentMode = MistakenMode.CLASSIC
        } else {
            val chance = ThreadLocalRandom.current().nextInt(1, 101)
            var selected = when {
                chance <= 50 -> MistakenMode.CLASSIC
                chance <= 65 -> MistakenMode.ONE_BOUNCE
                chance <= 70 -> MistakenMode.DOUBLE_KILLER
                chance <= 80 -> MistakenMode.INFECTION
                chance <= 90 -> MistakenMode.FREEZE_TAG
                else -> MistakenMode.INITIALIZES // 🔥 10% de probabilidad
            }
            if (selected == MistakenMode.DOUBLE_KILLER && onlineCount < 4) selected = MistakenMode.CLASSIC
            game.currentMode = selected
        }
    }

    fun endGame(configPath: String, killerWon: Boolean) {
        if (game.currentState == GameState.ENDING) return
        game.currentState = GameState.ENDING

        // 🔥 Limpieza de Anomalía
        geoffreyEntity?.remove()
        geoffreyEntity = null

        this.lastKillerWon = killerWon
        game.lastKillerWon = killerWon

        game.timer = 12

        val mapName = game.currentMapName
        val killer = game.getCurrentAsesino()

        val defaultAssassinWord = pumpking.lib.service.PumpkingServiceManager.messages.getRawString(null, "words.assassin", "El Killer", "messages")
        val defaultSurvivorsWord = pumpking.lib.service.PumpkingServiceManager.messages.getRawString(null, "words.survivors", "Survivors", "messages")

        val ganadorNombre = if (killerWon) (killer?.name ?: defaultAssassinWord) else defaultSurvivorsWord
        val razon = if (killerWon) {
            pumpking.lib.service.PumpkingServiceManager.messages.getRawString(null, "discord.reason_killer_won", "¡El asesino ganó!", "messages")
        } else {
            pumpking.lib.service.PumpkingServiceManager.messages.getRawString(null, "discord.reason_survivors_won", "¡Los supervivientes escaparon!", "messages")
        }

        val escapados = game.plugin.server.onlinePlayers.filter {
            !game.isKiller(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
        }.map { it.name }

        game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
            game.plugin.webHook.sendGameEnd(mapName, ganadorNombre, razon, escapados)
            game.plugin.server.onlinePlayers.forEach { p ->
                val uuid = p.uniqueId
                if (killerWon) {
                    if (game.isKiller(uuid)) game.plugin.statsManager.incrementStat(uuid, "wins_assassin")
                    else game.plugin.statsManager.incrementStat(uuid, "losses_survivor")
                } else {
                    if (game.isKiller(uuid)) game.plugin.statsManager.incrementStat(uuid, "losses_assassin")
                    else if (p.gameMode != GameMode.SPECTATOR) game.plugin.statsManager.incrementStat(uuid, "wins_survivor")
                }
            }
        }

        if (killerWon && killer != null) {
            val killerClass = game.plugin.asesinoManager.getKillerOfPlayer(killer)
            if (killerClass != null) {
                game.plugin.cinematicManager.playKillerOutro(killer, killerClass)
            } else {
                game.broadcastLocalized(configPath)
            }
        } else {
            game.broadcastLocalized(configPath)
            game.plugin.server.onlinePlayers.forEach { it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f) }
        }

        game.plugin.combatManager.giveWinRewards(killerWon, game)
        game.modeForced = false
        game.forceStart = false
    }

    fun resetToLobby(path: String?) {
        path?.let { game.broadcastLocalized(it) }

        // 🔥 Limpieza de Anomalía por Abandono
        geoffreyEntity?.remove()
        geoffreyEntity = null

        if (game.currentState == GameState.INGAME || game.currentState == GameState.STARTING || game.currentState == GameState.ENDING) {
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.limpiarMapa()
            game.playerController.teleportAllToLobby()
        }

        game.currentState = GameState.LOBBY
        game.timer = 0
        game.currentKillerUUID = null
        game.asesinosUUIDs.clear()
        game.modeForced = false
        game.forceStart = false
        game.ambientManager.stopAll()
        game.combatManager.clearAll()
        game.uiController.clearBossBars()
    }
}


