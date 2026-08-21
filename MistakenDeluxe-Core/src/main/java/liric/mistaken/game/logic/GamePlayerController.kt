package liric.mistaken.game.logic

import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.utils.misc.BungeeUtils
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.min
import liric.mistaken.game.Arena

import net.kyori.adventure.text.minimessage.MiniMessage
import liric.mistaken.config.engine.core.MessageService

class GamePlayerController(private val game: GameSession) {

    companion object {
        val globalRecentKillers = mutableListOf<String>()
    }

    var lmsActivado = false
    private var activeLmsMusic = "mistaken:lms"

    fun setupPlayers(arena: Arena) {
        
        val sessionPlayers = game.getPlayers().filter { !game.plugin.isIgnored(it) }.toMutableList()
        if (sessionPlayers.isEmpty()) return

        game.killersUUIDs.clear()

        
        val candidatos = sessionPlayers.filter { 
            !globalRecentKillers.contains(it.uniqueId.toString()) && 
            !game.forcedSurvivorUUIDs.contains(it.uniqueId)
        }.toMutableList()

        val killersToSelect = when (game.currentMode) {
            MistakenMode.DOUBLE_KILLER -> if (sessionPlayers.size >= 4) 2 else 1
            MistakenMode.ONE_BOUNCE -> (sessionPlayers.size - 1).coerceAtLeast(1)
            else -> 1
        }
        
        var selectedCount = 0
        
        
        game.forcedKillerUUID?.let { forcedUuid ->
            if (sessionPlayers.any { it.uniqueId == forcedUuid }) {
                game.killersUUIDs.add(forcedUuid)
                candidatos.removeAll { it.uniqueId == forcedUuid }
                selectedCount++
                game.forcedKillerUUID = null 
            }
        }

        
        game.settings?.let { rules ->
            rules.allowedKillers.forEach { killerName ->
                val p = sessionPlayers.find { it.name.equals(killerName, ignoreCase = true) }
                if (p != null && selectedCount < killersToSelect && !game.killersUUIDs.contains(p.uniqueId)) {
                    game.killersUUIDs.add(p.uniqueId)
                    candidatos.removeAll { it.uniqueId == p.uniqueId }
                    selectedCount++
                }
            }
            rules.allowedSurvivors.forEach { survName ->
                val p = sessionPlayers.find { it.name.equals(survName, ignoreCase = true) }
                if (p != null) {
                    candidatos.removeAll { it.uniqueId == p.uniqueId }
                }
            }
        }

        
        if (candidatos.isEmpty() && selectedCount < killersToSelect) {
            val backup = sessionPlayers.filter { p -> 
                !game.killersUUIDs.contains(p.uniqueId) && 
                !(game.settings?.allowedSurvivors?.any { it.equals(p.name, true) } ?: false) &&
                !game.forcedSurvivorUUIDs.contains(p.uniqueId)
            }
            candidatos.addAll(backup)
            globalRecentKillers.clear()
        }

        candidatos.shuffle()

        for (i in 0 until min(killersToSelect - selectedCount, candidatos.size)) {
            val uuid = candidatos[i].uniqueId
            game.killersUUIDs.add(uuid)
            globalRecentKillers.add(uuid.toString())
        }
        
        while(globalRecentKillers.size > 50) globalRecentKillers.removeAt(0)

        game.currentKillerUUID = game.killersUUIDs.firstOrNull()

        var survivorIndex = 0
        val survivorsSolo = mutableListOf<Player>()

        for (p in sessionPlayers) {
            game.plugin.spectatorManager.removeCustomSpectator(p)

            val isKiller = game.isKiller(p.uniqueId)
            p.inventory.clear()
            game.combatManager.resetHealth(p)

            if (p.gameMode == GameMode.SPECTATOR) p.spectatorTarget = null
            p.gameMode = GameMode.SURVIVAL

            if (isKiller) {
                game.uiController.setLuckPermsPrefix(p, "<red>")
                val spawnLoc = arena.killerSpawn ?: p.world.spawnLocation

                p.teleportAsync(spawnLoc).thenAccept { success ->
                    if (success && p.isOnline) {
                        p.scheduler.run(game.plugin, Consumer { _ ->
                            p.removePotionEffect(PotionEffectType.DARKNESS)
                        }, null)

                        var claseID = game.plugin.playerDataManager.getSelectedKiller(p.uniqueId)
                        if (game.settings?.disabledClasses?.contains(claseID.lowercase()) == true) {
                            claseID = "slasher"
                            p.sendMessage(liric.mistaken.utils.color.ColorTranslator.translate("<red>Tu clase fue deshabilitada por el Host, usando Slasher."))
                        }
                        game.plugin.killerManager.equipKiller(p, claseID)

                        if (game.currentMode == MistakenMode.HIDE_AND_SEEK) {
                            p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 1200, 0, false, false, false))
                            p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 1200, 255, false, false, false))
                            p.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 1200, 250, false, false, false))
                            p.addPotionEffect(PotionEffect(PotionEffectType.MINING_FATIGUE, 1200, 255, false, false, false))
                            
                            p.sendMessage(liric.mistaken.utils.color.ColorTranslator.translate("<red>¡Espera 1 minuto mientras los supervivientes se esconden!"))
                            
                            p.scheduler.runDelayed(game.plugin, Consumer { _ ->
                                if (p.isOnline && game.currentState == GameState.INGAME) {
                                    p.removePotionEffect(PotionEffectType.BLINDNESS)
                                    p.removePotionEffect(PotionEffectType.SLOWNESS)
                                    p.removePotionEffect(PotionEffectType.JUMP_BOOST)
                                    p.removePotionEffect(PotionEffectType.MINING_FATIGUE)
                                    p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                                    p.showTitle(Title.title(
                                        MessageService.getComponent(p, "game.killer-released-title"),
                                        MessageService.getComponent(p, "game.killer-released-subtitle")
                                    ))
                                }
                            }, null, 1200L)
                        }
                    }
                }
            } else {
                survivorsSolo.add(p)
                game.uiController.setLuckPermsPrefix(p, "<green>")

                val spawns = arena.survivorSpawns
                val spawnLoc = if (spawns.isEmpty()) arena.killerSpawn ?: p.world.spawnLocation else spawns[survivorIndex % spawns.size]
                val delayTicks = (survivorIndex / 2).toLong()
                survivorIndex++

                p.scheduler.runDelayed(
                    game.plugin,
                    Consumer { _ ->
                        p.teleportAsync(spawnLoc).thenAccept { success ->
                            if (success && p.isOnline) {
                                var idElegido = game.plugin.playerDataManager.getSelectedSurvivor(p.uniqueId)
                                if (game.settings?.disabledClasses?.contains(idElegido.lowercase()) == true) {
                                    idElegido = "civil"
                                    p.sendMessage(liric.mistaken.utils.color.ColorTranslator.translate("<red>Tu clase fue deshabilitada por el Host, usando Civil."))
                                }
                                val clase = game.plugin.survivorManager.getClassById(idElegido) ?: game.plugin.survivorManager.getClassById("civil") ?: game.plugin.survivorManager.availableClasses.values.firstOrNull()
                                if (clase != null) {
                                    game.plugin.survivorManager.registrarSurvivor(p, clase as liric.mistaken.roles.survivors.Survivor)
                                }

                                if (game.currentMode == MistakenMode.ONE_BOUNCE) {
                                    p.addPotionEffect(PotionEffect(PotionEffectType.SPEED, Int.MAX_VALUE, 1, false, false, false))
                                    p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 100.0
                                    p.health = 100.0
                                } else if (game.currentMode == MistakenMode.HIDE_AND_SEEK) {
                                    p.sendMessage(liric.mistaken.utils.color.ColorTranslator.translate("<green>¡Tienes 1 minuto para esconderte antes de que el asesino sea liberado!"))
                                    p.scheduler.runDelayed(game.plugin, Consumer { _ ->
                                        if (p.isOnline && game.currentState == GameState.INGAME) {
                                            p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.8f)
                                            p.showTitle(Title.title(
                                                MessageService.getComponent(p, "game.killer-released-title"),
                                                MessageService.getComponent(p, "game.killer-released-subtitle")
                                            ))
                                        }
                                    }, null, 1200L)
                                }
                            }
                        }
                    },
                    null,
                    delayTicks.coerceAtLeast(1L)
                )
            }

            game.uiController.playRoleTitle(p, isKiller)
            game.plugin.observerHUDManager.updatePlayerRole(p)

            
            game.settings?.let { rules ->
                rules.speedMultiplier?.let { lvl ->
                    p.addPotionEffect(PotionEffect(PotionEffectType.SPEED, Int.MAX_VALUE, lvl, false, false, false))
                }
                rules.jumpMultiplier?.let { lvl ->
                    p.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, Int.MAX_VALUE, lvl, false, false, false))
                }
                if ((rules.blindnessRole == "KILLER" && isKiller) || (rules.blindnessRole == "SURVIVOR" && !isKiller)) {
                    p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, Int.MAX_VALUE, 0, false, false, false))
                }
                if (rules.glowingEnabled) {
                    p.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, Int.MAX_VALUE, 0, false, false, false))
                }
            }
        }

        game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
            game.getCurrentKiller()?.let { killer ->
                game.plugin.webHook.sendGameStart(game.currentMapName, game.currentMode.name, survivorsSolo, killer)
            }
        }
    }

    fun handleInGameTick(players: Collection<Player>, ticks: Int) {
        if (game.killersUUIDs.isEmpty()) {
            game.stateController.endGame("game.killer-disconnected", false)
            return
        }

        
        val killersOnline = game.killersUUIDs.mapNotNull { game.plugin.server.getPlayer(it) }.filter { it.isOnline }

        for (p in players) {
            if (game.plugin.isIgnored(p) || game.isKiller(p.uniqueId) || p.gameMode == GameMode.SPECTATOR || game.plugin.spectatorManager.isSpectator(p)) continue

            if (p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                if (p.hasPotionEffect(PotionEffectType.GLOWING)) {
                    p.removePotionEffect(PotionEffectType.GLOWING)
                }
            } else if (game.settings?.glowingEnabled == true) {
                if (!p.hasPotionEffect(PotionEffectType.GLOWING)) {
                    p.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, Int.MAX_VALUE, 0, false, false, false))
                }
            }

            if ((ticks + (p.uniqueId.hashCode() and 0xFFFF)) % 5 == 0) {
                game.uiController.playAmbientForPlayer(p, killersOnline)
            }

            if (ticks % 10 == 0 && killersOnline.isNotEmpty() && game.currentMode != MistakenMode.HIDE_AND_SEEK) {
                val closestKiller = killersOnline[0]
                game.uiController.checkHeartbeat(p, closestKiller)

                if (p.world == closestKiller.world && p.location.distanceSquared(closestKiller.location) <= 100.0) {
                    p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false))
                }
            }

            if (ticks % 2 == 0 && game.currentMode != MistakenMode.FREEZE_TAG && game.combatManager.getHealth(p) == 1 && p.vehicle == null) {
                if (!p.isSwimming) p.isSwimming = true
                if (ticks % 40 == 0) {
                    p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 45, 0, false, false, false))
                    p.playSound(p.location, Sound.ENTITY_PLAYER_BREATH, 0.8f, 0.8f)
                }
            }

            if (ticks % 5 == 0 && p.passengers.any { !liric.mistaken.utils.misc.EntityUtils.isHUDEntity(it) } && p.isSprinting) {
                game.plugin.playerDataManager.consumeStamina(p.uniqueId, 0.4)
            }

            
            if (ticks % 10 == 0) {
                val health = p.health
                if (health <= 10.0) { 
                    
                    val alpha = (1.0f - (health.toFloat() / 10.0f)) * 0.7f + 0.15f
                    liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 255, 0, 0, alpha, 20)
                } else if (lmsActivado) {
                    liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 255, 0, 0, 0.2f, 20) 
                }
            }
        }

        if (ticks % 20 == 0) {
            if (game.timer <= 0) {
                game.stateController.endGame("game.victory-survivors", false)
            } else {
                checkWinCondition()
            }
        }
    }

    fun checkWinCondition() {
        if (game.currentState != GameState.INGAME) return

        
        val sessionPlayers = game.getPlayers()


        val allSurvivors = sessionPlayers.filter { !game.isKiller(it.uniqueId) && it.gameMode == GameMode.SURVIVAL && !game.plugin.spectatorManager.isSpectator(it) }

        if (allSurvivors.isEmpty()) {
            game.stateController.endGame("game.victory-killer", true)
            return
        }

        val freeSurvivors = allSurvivors.count { !game.combatManager.isFrozen(it) }
        if (freeSurvivors == 0) {
            game.stateController.endGame("game.victory-killer", true)
        }
    }

    private fun checkLastManStanding() {
        if (game.currentState != GameState.INGAME || lmsActivado) return

        
        val survivorsVivos = game.getPlayers().filter {
            !game.isKiller(it.uniqueId) && it.gameMode == GameMode.SURVIVAL && !game.plugin.spectatorManager.isSpectator(it)
        }

        if (survivorsVivos.size == 1 && game.currentMode != MistakenMode.FREEZE_TAG) {
            lmsActivado = true
            val ultimoHeroe = survivorsVivos[0]
            triggerLMS(ultimoHeroe)
        }
    }

    private fun triggerLMS(player: Player) {
        val killerPlayer = game.getCurrentKiller()
        val killerClass = killerPlayer?.let { game.plugin.killerManager.getKillerOfPlayer(it) }
        val customMusic = killerClass?.let { killer ->
            game.plugin.configManager.getKillerConfig(killer.id).getString("lms_music") ?: killer.defaultMusic
        }
        activeLmsMusic = customMusic ?: "mistaken:lms"

        game.uiController.broadcastLMS(player, activeLmsMusic)
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 60, 0))

        
        liric.mistaken.utils.hooks.ObserverHook.playScreenTint(player, 255, 255, 255, 0.9f, 30)
        liric.mistaken.utils.hooks.ObserverHook.playScreenshake(player, 1.5f, 40)

        if (game.timer > 90) {
            game.timer = 90
        }
    }

    fun handlePlayerDeath(player: Player) {
        
        
        game.plugin.flashlightManager.disable(player)

        if (game.currentState == GameState.ENDING || player.gameMode == GameMode.SPECTATOR || game.plugin.spectatorManager.isSpectator(player)) return


        if (game.isKiller(player.uniqueId)) {
            game.killersUUIDs.remove(player.uniqueId)
            game.plugin.spectatorManager.setCustomSpectator(player)

            if (game.killersUUIDs.isEmpty() && game.currentState == GameState.INGAME) {
                game.stateController.endGame("game.victory-survivors", false)
            }
            return
        }

        if (game.currentMode == MistakenMode.INFECTION) {
            game.plugin.survivorManager.getSurvivorClass(player)?.cleanup(player)
            game.killersUUIDs.add(player.uniqueId)
            player.isSwimming = false
            game.ambientManager.stopAmbience(player)
            game.combatManager.resetHealth(player)

            
            liric.mistaken.utils.hooks.ObserverHook.setTrueDarkness(player, false)

            game.uiController.setLuckPermsPrefix(player, "<red>")

            game.plugin.lobbyLocation?.let { loc ->
                player.teleportAsync(loc)
            }

            player.scheduler.runDelayed(
                game.plugin,
                Consumer { _ ->
                    val claseID = game.plugin.playerDataManager.getSelectedKiller(player.uniqueId)
                    game.plugin.killerManager.equipKiller(player, claseID)
                    game.uiController.playRoleTitle(player, true)
                },
                null,
                5L
            )

            game.getPlayers().forEach { it.sendMessage(MiniMessage.miniMessage().deserialize("<dark_red>Infección</dark_red> <dark_gray>»</dark_gray> <red>¡${player.name} ha sido infectado y ahora es un asesino!</red>")) }
            player.world.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 1f)

            game.getCurrentKiller()?.let { killer ->
                game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
                    game.plugin.statsManager.incrementStat(killer.uniqueId, "kills")
                }
            }

            checkWinCondition()
            return
        }

        game.plugin.spectatorManager.setCustomSpectator(player)
        player.isSwimming = false
        game.ambientManager.stopAmbience(player)

        game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
            game.plugin.statsManager.incrementStat(player.uniqueId, "deaths")
        }

        player.vehicle?.let { if (it is Player) game.combatManager.soltarPasajero(it) }

        game.broadcastLocalized("game.player-died", Placeholder.parsed("player", player.name))
        player.playSound(player.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)

        game.getCurrentKiller()?.let { killer ->
            game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
                game.plugin.statsManager.incrementStat(killer.uniqueId, "kills")
            }
            val extra = ThreadLocalRandom.current().nextInt(10, 21)
            game.timer = min(game.timer + extra, 900)
            game.broadcastLocalized("game.time-extended", Placeholder.parsed("seconds", extra.toString()))

            killer.getAttribute(Attribute.MAX_HEALTH)?.let {
                killer.health = min(it.value, killer.health + 40.0)
            }
            killer.playSound(killer.location, Sound.ENTITY_WITCH_DRINK, 1f, 0.8f)
        }

        checkLastManStanding()
        checkWinCondition()
    }

    fun cleanupAllPlayers(killerWon: Boolean) {
        lmsActivado = false

        val winSound = if (killerWon) Sound.ENTITY_WITHER_SPAWN else Sound.UI_TOAST_CHALLENGE_COMPLETE
        val type = if (killerWon) "killer" else "survivor"

        
        game.getPlayers().forEach { p ->
            p.stopSound(activeLmsMusic, SoundCategory.RECORDS)
            game.plugin.flashlightManager.disable(p)

            p.passengers.filter { !liric.mistaken.utils.misc.EntityUtils.isHUDEntity(it) }.forEach { p.removePassenger(it) }
            if (!liric.mistaken.utils.misc.EntityUtils.isHUDEntity(p.vehicle)) p.vehicle?.removePassenger(p)
            p.fireTicks = 0
            p.inventory.clear()
            p.inventory.armorContents = arrayOfNulls(4)
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 20.0
            p.health = 20.0

            if (game.isKiller(p.uniqueId)) {
                game.plugin.killerManager.getKillerOfPlayer(p)?.cleanup(p)
            } else {
                game.plugin.survivorManager.getSurvivorClass(p)?.cleanup(p)
            }

            game.combatManager.removePlayerData(p.uniqueId)

            if (p.isInvisible || game.plugin.spectatorManager.isSpectator(p)) {
                p.isInvisible = false
                p.isCollidable = true
                p.isInvulnerable = false
                p.allowFlight = false
                p.isFlying = false

                
                game.getPlayers().forEach { online -> online.showPlayer(game.plugin, p) }
            }

            p.showTitle(Title.title(
                MessageService.getComponent(p, "game.$type-title"),
                MessageService.getComponent(p, "game.$type-subtitle")
            ))

            p.playSound(p.location, winSound, 1f, 1f)
        }

        game.combatManager.clearAll()
        game.killersUUIDs.clear()

        
        game.plugin.killerManager.removeAllKillers()
        game.plugin.survivorManager.cleanAll()
    }

    fun teleportAllToLobby() {
        val serverMode = game.plugin.serverMode

        game.getPlayers().forEach { p ->
            p.gameMode = GameMode.SURVIVAL

            if (serverMode == "GAME_SERVER") {
                
                val lobbyName = game.plugin.config.getString("proxy-lobby-server", "lobby") ?: "lobby"
                BungeeUtils.sendToServer(game.plugin, p, lobbyName)
            } else {
                
                game.plugin.lobbyLocation?.let { loc ->
                    p.teleportAsync(loc)
                }
            }
        }
    }
}
