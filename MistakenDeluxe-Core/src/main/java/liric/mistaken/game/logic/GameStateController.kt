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
import org.bukkit.Particle
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService

class GameStateController(private val game: GameSession) {

    
    private var lastKillerWon = false

    
    internal var geoffreyEntity: GeoffreyEXE? = null

    fun startBreakProcess() {
        val venimosDePartida = game.currentState == GameState.ENDING

        if (venimosDePartida) {
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.clearMapa()
            
            
            
            val playersToLeave = game.getPlayers().toList()
            playersToLeave.forEach { player ->
                game.plugin.sessionManager.leaveSession(player)
            }
            
            
            game.plugin.server.globalRegionScheduler.runDelayed(game.plugin, { _ ->
                game.plugin.sessionManager.destroySession(game.id)
            }, 80L)
            return
        }

        if (game.currentState == GameState.BREAK) return
        game.currentState = GameState.BREAK

        game.timer = game.plugin.config.getInt("settings.break-duration", 10)

        
        if (venimosDePartida) {
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.clearMapa()
            game.playerController.teleportAllToLobby()
        }

        game.broadcastLocalized("game.break-start")
    }

    fun startVotingProcess() {
        if (game.currentState == GameState.VOTING) return
        game.currentState = GameState.VOTING
        game.timer = game.plugin.config.getInt("settings.voting-duration", 30)

        game.broadcastLocalized("voting.started")
        game.plugin.arenaManager.getArenasMap().keys.forEach { map ->
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
                
                game.broadcastLocalized("game.mode-selected", Placeholder.parsed("mode", game.currentMode.name))
                game.uiController.playModeTitle(online)

                
                val killer = game.getCurrentKiller()
                if (killer != null && killer.isOnline) {
                    val killerClass = game.plugin.killerManager.getKillerOfPlayer(killer)
                    if (killerClass != null) {
                        game.plugin.cinematicManager.playKillerIntro(killer, killerClass, game.getPlayers())
                    }
                }
            }
            0 -> {
                game.currentState = GameState.INGAME
                game.timer = game.settings?.gameDuration ?: game.plugin.config.getInt("settings.game-duration", 300)
                game.broadcastLocalized("game.hunt-start")

                
                online.forEach { p ->
                    if (p.gameMode == GameMode.SPECTATOR && !game.plugin.spectatorManager.isSpectator(p)) {
                        p.spectatorTarget = null 
                        p.gameMode = GameMode.SURVIVAL 
                    }

                    
                    if (!game.isKiller(p.uniqueId) && !game.plugin.spectatorManager.isSpectator(p)) {
                        liric.mistaken.utils.hooks.ObserverHook.setTrueDarkness(p, true)
                    }
                }
            }
        }
    }

    
    fun checkGeoffreySpawn() {
        
        if (game.currentMode == MistakenMode.INITIALIZES && game.timer == 290) {

            
            val title = ColorTranslator.translate("<dark_red><bold><obfuscated>||</obfuscated> ¡GEOFFREY ESTÃ AQUÃ! <obfuscated>||</obfuscated>")
            val subtitle = ColorTranslator.translate("<dark_gray>Nadie sobrevivirá...")
            val times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(500))

            game.plugin.server.onlinePlayers.forEach { p ->
                p.showTitle(Title.title(title, subtitle, times))
                p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f)
                p.playSound(p.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.5f)

                
                p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 1, false, false, false))
            }

            
            val spawnLoc = game.getCurrentKiller()?.location ?: game.plugin.server.onlinePlayers.firstOrNull()?.location

            if (spawnLoc != null) {
                val geoffreyLoc = spawnLoc.clone().add(0.0, 15.0, 0.0)
                geoffreyLoc.world.spawnParticle(Particle.EXPLOSION_EMITTER, geoffreyLoc, 2)
                geoffreyLoc.world.playSound(geoffreyLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.5f)

                geoffreyEntity = GeoffreyEXE(game.plugin).apply { assignedSession = game }
                geoffreyEntity?.spawn(geoffreyLoc)
            }
        }
    }

    fun startInGame() {
        val arenas = game.plugin.arenaManager.getArenasMap()
        val winner = game.settings?.forcedMap ?: game.voteManager.getWinningMap(arenas) ?: run { resetToLobby(null); return }
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

                val timeMode = arena.timeMode
                aspWorld.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false)
                when (timeMode.lowercase()) {
                    "day" -> aspWorld.time = 6000
                    "night" -> aspWorld.time = 18000
                    "afternoon" -> aspWorld.time = 12000
                    "morning" -> aspWorld.time = 0
                    "dynamic" -> aspWorld.time = 0
                    else -> aspWorld.time = 0
                }

                arena.killerSpawn?.world = aspWorld
                arena.survivorSpawns.forEach { it.world = aspWorld }

                val genLocations = arena.generators.map { it.clone().apply { world = aspWorld } }
                game.plugin.generatorManager.prepareArenaGenerators(genLocations)

                game.playerController.setupPlayers(arena)
                game.broadcastLocalized("game.map-loaded", Placeholder.parsed("map", winner))
            }
        }
    }

    private fun determineGameMode() {
        if (game.settings?.forcedMode != null) {
            game.currentMode = game.settings!!.forcedMode!!
            return
        }
        if (game.modeForced) return
        val onlineCount = game.plugin.server.onlinePlayers.count { !game.plugin.isIgnored(it) }

        if (onlineCount < 3) {
            game.currentMode = MistakenMode.CLASSIC
        } else {
            val chance = ThreadLocalRandom.current().nextInt(1, 101)
            var selected = when {
                chance <= 50 -> MistakenMode.CLASSIC
                chance <= 65 -> MistakenMode.ONE_BOUNCE
                chance <= 73 -> MistakenMode.DOUBLE_KILLER
                chance <= 83 -> MistakenMode.INFECTION
                chance <= 92 -> MistakenMode.FREEZE_TAG
                chance <= 98 -> MistakenMode.HIDE_AND_SEEK
                else         -> MistakenMode.INITIALIZES    
            }
            if (selected == MistakenMode.DOUBLE_KILLER && onlineCount < 4) selected = MistakenMode.CLASSIC
            game.currentMode = selected
        }
    }

    fun endGame(configPath: String, killerWon: Boolean, forceDebugEnd: Boolean = false) {
        if (game.isDebugStart && !forceDebugEnd) return
        if (game.currentState == GameState.ENDING) return
        game.currentState = GameState.ENDING

        
        
        game.getPlayers().forEach { game.plugin.flashlightManager.disable(it) }

        
        geoffreyEntity?.remove()
        geoffreyEntity = null

        this.lastKillerWon = killerWon
        game.lastKillerWon = killerWon
        
        game.activeModeHandler.onGameEnd(killerWon)

        game.timer = 12

        val mapName = game.currentMapName
        val killer = game.getCurrentKiller()

        val defaultAssassinWord = MessageService.getRawString(null, "words.assassin", "El Killer", "messages")
        val defaultSurvivorsWord = MessageService.getRawString(null, "words.survivors", "Survivors", "messages")

        val ganadorNombre = if (killerWon) (killer?.name ?: defaultAssassinWord) else defaultSurvivorsWord
        val razon = if (killerWon) {
            MessageService.getRawString(null, "discord.reason_killer_won", "¡El asesino ganó!", "messages")
        } else {
            MessageService.getRawString(null, "discord.reason_survivors_won", "¡Los supervivientes escaparon!", "messages")
        }

        val escapados = game.plugin.server.onlinePlayers.filter {
            !game.isKiller(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
        }.map { it.name }

        val titlePair: Pair<String, String> = if (killerWon) {
            Pair("game.killer-title", "game.killer-subtitle")
        } else {
            Pair("game.killer-defeat-title", "game.killer-defeat-subtitle")
        }
        val survivorTitlePair: Pair<String, String> = if (!killerWon) {
            Pair("game.survivor-title", "game.survivor-subtitle")
        } else {
            Pair("game.survivor-defeat-title", "game.survivor-defeat-subtitle")
        }

        val tK1 = ColorTranslator.translate(MessageService.getRawString(null, titlePair.first, "", "messages"))
        val tK2 = ColorTranslator.translate(MessageService.getRawString(null, titlePair.second, "", "messages"))
        val tS1 = ColorTranslator.translate(MessageService.getRawString(null, survivorTitlePair.first, "", "messages"))
        val tS2 = ColorTranslator.translate(MessageService.getRawString(null, survivorTitlePair.second, "", "messages"))

        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(1000))
        
        game.plugin.server.onlinePlayers.forEach { p ->
            val isK = game.isKiller(p.uniqueId)
            val t1 = if (isK) tK1 else tS1
            val t2 = if (isK) tK2 else tS2
            
            p.showTitle(Title.title(
                t1,
                t2,
                times
            ))
        }

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
            val killerClass = game.plugin.killerManager.getKillerOfPlayer(killer)
            if (killerClass != null) {
                game.plugin.cinematicManager.playKillerOutro(killer, killerClass, game.getPlayers())
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

        
        geoffreyEntity?.remove()
        geoffreyEntity = null

        if (game.currentState == GameState.INGAME || game.currentState == GameState.STARTING || game.currentState == GameState.ENDING) {
            game.playerController.cleanupAllPlayers(lastKillerWon)
            game.worldController.clearMapa()
            game.playerController.teleportAllToLobby()
        }

        game.currentState = GameState.LOBBY
        game.timer = 0
        game.currentKillerUUID = null
        game.killersUUIDs.clear()
        game.modeForced = false
        game.forceStart = false
        game.ambientManager.stopAll()
        game.combatManager.clearAll()
        game.uiController.clearBossBars()
    }
}
