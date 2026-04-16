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

    private var lastKillerWon = false
    private var geoffreyEntity: GeoffreyEXE? = null

    fun startBreakProcess() {
        val venimosDePartida = game.currentState == GameState.ENDING

        if (game.currentState == GameState.BREAK) return
        game.currentState = GameState.BREAK
        game.timer = game.plugin.config.getInt("settings.break-duration", 10)

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

        // Uso seguro del arenaManager
        game.plugin.arenaManager?.getArenas()?.keys?.forEach { map ->
            game.broadcastLocalized("voting.map-option", Placeholder.parsed("map", map))
        }
    }

    fun handleStartingSequence() {
        val sessionPlayers = game.getPlayers()
        when (game.timer) {
            12 -> sessionPlayers.forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 0.5f) }
            10 -> {
                game.broadcastLocalized("game.mode-reveal-start")
                sessionPlayers.forEach { it.playSound(it.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.1f) }
            }
            8 -> {
                game.broadcastLocalized("game.mode-selected", Placeholder.parsed("mode", game.currentMode.name))
                game.uiController.playModeTitle(sessionPlayers)

                val killer = game.getCurrentAsesino()
                if (killer != null && killer.isOnline) {
                    val claseAsesino = game.plugin.asesinoManager?.getAsesinoDelJugador(killer)
                    if (claseAsesino != null) {
                        game.plugin.cinematicManager?.playKillerIntro(killer, claseAsesino)
                    }
                }
            }
            0 -> {
                game.currentState = GameState.INGAME
                game.timer = game.plugin.config.getInt("settings.game-duration", 300)
                game.broadcastLocalized("game.hunt-start")

                sessionPlayers.forEach { p ->
                    p.scheduler.run(game.plugin, { _ ->
                        if (p.gameMode == GameMode.SPECTATOR && game.plugin.spectatorManager?.isSpectator(p) == false) {
                            p.spectatorTarget = null
                            p.gameMode = GameMode.SURVIVAL
                        }
                    }, null)
                }
            }
        }
    }

    fun checkGeoffreySpawn() {
        if (game.currentMode == MistakenMode.INITIALIZES && game.timer == 290) {
            val title = game.plugin.mm.deserialize("<dark_red><bold><obfuscated>||</obfuscated> ¡GEOFFREY ESTÁ AQUÍ! <obfuscated>||</obfuscated>")
            val subtitle = game.plugin.mm.deserialize("<dark_gray>Nadie sobrevivirá...")
            val times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(500))

            game.getPlayers().forEach { p ->
                p.scheduler.run(game.plugin, { _ ->
                    p.showTitle(Title.title(title, subtitle, times))
                    p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f)
                    p.playSound(p.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.5f)

                    p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false))
                    p.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 1, false, false, false))
                }, null)
            }

            val spawnLoc = game.getCurrentAsesino()?.location ?: game.getPlayers().firstOrNull()?.location

            if (spawnLoc != null) {
                game.plugin.server.regionScheduler.run(game.plugin, spawnLoc, { _ ->
                    val geoffreyLoc = spawnLoc.clone().add(0.0, 15.0, 0.0)
                    geoffreyLoc.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, geoffreyLoc, 2)
                    geoffreyLoc.world.playSound(geoffreyLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.5f)

                    geoffreyEntity = GeoffreyEXE(game.plugin)
                    geoffreyEntity?.spawn(geoffreyLoc)
                })
            }
        }
    }

    fun startInGame() {
        val arenas = game.plugin.arenaManager?.getArenas() ?: return resetToLobby(null)
        val winner = game.voteManager.getWinningMap(arenas) ?: return resetToLobby(null)
        val arena = game.plugin.arenaManager?.getArena(winner) ?: return resetToLobby(null)

        game.currentState = GameState.STARTING
        game.currentMapName = winner

        game.plugin.mapManager?.loadArenaWorld(winner)?.thenAccept { aspWorld ->
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
                game.plugin.generatorManager?.prepareArenaGenerators(genLocations)

                game.playerController.setupPlayers(arena)
                game.broadcastLocalized("game.map-loaded", Placeholder.parsed("map", winner))
            }
        }
    }

    private fun determineGameMode() {
        if (game.modeForced) return
        val onlineCount = game.getPlayers().count { !game.plugin.isIgnored(it) }

        if (onlineCount < 3) {
            game.currentMode = MistakenMode.CLASSIC
        } else {
            val chance = ThreadLocalRandom.current().nextInt(1, 101)
            var selected = when {
                chance <= 50 -> MistakenMode.CLASSIC
                chance <= 65 -> MistakenMode.ONE_BOUNCE
                chance <= 80 -> MistakenMode.DOUBLE_KILLER
                chance <= 90 -> MistakenMode.ASSASSIN_PVP
                else -> MistakenMode.INITIALIZES
            }
            if (selected == MistakenMode.DOUBLE_KILLER && onlineCount < 4) selected = MistakenMode.CLASSIC
            game.currentMode = selected
        }
    }

    fun endGame(configPath: String, killerWon: Boolean) {
        if (game.currentState == GameState.ENDING) return
        game.currentState = GameState.ENDING

        geoffreyEntity?.remove()
        geoffreyEntity = null

        this.lastKillerWon = killerWon
        game.lastKillerWon = killerWon

        game.timer = 12

        val mapName = game.currentMapName
        val killer = game.getCurrentAsesino()

        val defaultAssassinWord = game.plugin.messageConfig.getRawString(null, "words.assassin", "El Asesino", "messages")
        val defaultSurvivorsWord = game.plugin.messageConfig.getRawString(null, "words.survivors", "Supervivientes", "messages")

        val ganadorNombre = if (killerWon) (killer?.name ?: defaultAssassinWord) else defaultSurvivorsWord
        val razon = if (killerWon) {
            game.plugin.messageConfig.getRawString(null, "discord.reason_killer_won", "¡El asesino ganó!", "messages")
        } else {
            game.plugin.messageConfig.getRawString(null, "discord.reason_survivors_won", "¡Los supervivientes escaparon!", "messages")
        }

        val escapados = game.getPlayers().filter {
            !game.esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
        }.map { it.name }

        game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
            game.plugin.discordHook.sendGameEnd(mapName, ganadorNombre, razon, escapados)
            game.getPlayers().forEach { p ->
                val uuid = p.uniqueId
                if (killerWon) {
                    if (game.esAsesino(uuid)) game.plugin.statsManager.incrementStat(uuid, "wins_assassin")
                    else game.plugin.statsManager.incrementStat(uuid, "losses_survivor")
                } else {
                    if (game.esAsesino(uuid)) game.plugin.statsManager.incrementStat(uuid, "losses_assassin")
                    else if (p.gameMode != GameMode.SPECTATOR) game.plugin.statsManager.incrementStat(uuid, "wins_survivor")
                }
            }
        }

        if (killerWon && killer != null) {
            val claseAsesino = game.plugin.asesinoManager?.getAsesinoDelJugador(killer)
            if (claseAsesino != null) {
                game.plugin.cinematicManager?.playKillerOutro(killer, claseAsesino)
            } else {
                game.broadcastLocalized(configPath)
            }
        } else {
            game.broadcastLocalized(configPath)
            game.getPlayers().forEach { it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f) }
        }

        game.combatManager.giveWinRewards(killerWon, game)
        game.modeForced = false
    }

    fun resetToLobby(path: String?) {
        path?.let { game.broadcastLocalized(it) }

        geoffreyEntity?.remove()
        geoffreyEntity = null

        if (game.currentState == GameState.INGAME || game.currentState == GameState.STARTING || game.currentState == GameState.ENDING) {
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.limpiarMapa()
            game.playerController.teleportAllToLobby()
        }

        game.currentState = GameState.LOBBY
        game.timer = 0
        game.currentAsesinoUUID = null
        game.asesinosUUIDs.clear()
        game.ambientManager.stopAll()
        game.combatManager.clearAll()
    }
}
