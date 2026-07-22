package liric.mistaken.game.logic

import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.utils.proxy.BungeeUtils
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
import liric.mistaken.roles.survivors.clases.Civilian
import net.kyori.adventure.text.minimessage.MiniMessage
import pumpking.lib.service.PumpkingServiceManager

class GamePlayerController(private val game: GameSession) {

    private var lmsActivado = false
    private var activeLmsMusic = "mistaken:lms"

    fun setupPlayers(arena: Arena) {
        // 🔥 FIX: Solo tomamos los jugadores de ESTA sesión
        val sessionPlayers = game.getPlayers().filter { !game.plugin.isIgnored(it) }.toMutableList()
        if (sessionPlayers.isEmpty()) return

        game.asesinosUUIDs.clear()
        val recentConfig = pumpking.lib.config.ConfigManager.get("recent_killers.yml")
        val recentList = recentConfig.getStringList("recent").toMutableList()

        // --- 2. MODOS CLÁSICOS ---
        val candidatos = sessionPlayers.filter { !recentList.contains(it.uniqueId.toString()) }.toMutableList()

        val killersToSelect = when (game.currentMode) {
            MistakenMode.DOUBLE_KILLER -> if (sessionPlayers.size >= 4) 2 else 1
            MistakenMode.ONE_BOUNCE -> (sessionPlayers.size - 1).coerceAtLeast(1)
            else -> 1
        }
        
        var selectedCount = 0
        
        // Asignar el killer forzado si existe y esta en la partida
        game.forcedKillerUUID?.let { forcedUuid ->
            if (sessionPlayers.any { it.uniqueId == forcedUuid }) {
                game.asesinosUUIDs.add(forcedUuid)
                candidatos.removeAll { it.uniqueId == forcedUuid }
                selectedCount++
                game.forcedKillerUUID = null // Solo sirve para 1 partida
            }
        }

        // Asignar killers de la partida privada
        game.settings?.let { rules ->
            rules.allowedKillers.forEach { killerName ->
                val p = sessionPlayers.find { it.name.equals(killerName, ignoreCase = true) }
                if (p != null && selectedCount < killersToSelect && !game.asesinosUUIDs.contains(p.uniqueId)) {
                    game.asesinosUUIDs.add(p.uniqueId)
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

        // Si todos los disponibles ya jugaron, reseteamos el historial reciente
        if (candidatos.isEmpty() && selectedCount < killersToSelect) {
            val backup = sessionPlayers.filter { p -> 
                !game.asesinosUUIDs.contains(p.uniqueId) && 
                !(game.settings?.allowedSurvivors?.any { it.equals(p.name, true) } ?: false)
            }
            candidatos.addAll(backup)
            recentConfig.set("recent", emptyList<String>())
            recentConfig.save()
            recentList.clear()
        }

        candidatos.shuffle()

        for (i in 0 until min(killersToSelect - selectedCount, candidatos.size)) {
            val uuid = candidatos[i].uniqueId
            game.asesinosUUIDs.add(uuid)
            recentList.add(uuid.toString())
        }
        
        while(recentList.size > 50) recentList.removeAt(0)
        recentConfig.set("recent", recentList)
        recentConfig.save()

        game.currentKillerUUID = game.asesinosUUIDs.firstOrNull()

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
                val spawnLoc = arena.asesinoSpawn ?: p.world.spawnLocation

                p.teleportAsync(spawnLoc).thenAccept { success ->
                    if (success && p.isOnline) {
                        p.scheduler.run(game.plugin, Consumer { _ ->
                            p.removePotionEffect(PotionEffectType.DARKNESS)
                        }, null)

                        var claseID = game.plugin.playerDataManager.getSelectedKiller(p.uniqueId)
                        if (game.settings?.disabledClasses?.contains(claseID.lowercase()) == true) {
                            claseID = "slasher"
                            p.sendMessage(pumpking.lib.color.ColorTranslator.translate("<red>Tu clase fue deshabilitada por el Host, usando Slasher."))
                        }
                        game.plugin.asesinoManager.equipKiller(p, claseID)
                    }
                }
            } else {
                survivorsSolo.add(p)
                game.uiController.setLuckPermsPrefix(p, "<green>")

                val spawns = arena.survivorSpawns
                val spawnLoc = if (spawns.isEmpty()) arena.asesinoSpawn ?: p.world.spawnLocation else spawns[survivorIndex % spawns.size]
                val delayTicks = (survivorIndex / 2).toLong()
                survivorIndex++

                p.scheduler.runDelayed(
                    game.plugin,
                    Consumer { _ ->
                        p.teleportAsync(spawnLoc).thenAccept { success ->
                            if (success && p.isOnline) {
                                var idElegido = game.plugin.playerDataManager.getSelectedSurvivor(p.uniqueId)
                                if (game.settings?.disabledClasses?.contains(idElegido.lowercase()) == true) {
                                    idElegido = "civilian"
                                    p.sendMessage(pumpking.lib.color.ColorTranslator.translate("<red>Tu clase fue deshabilitada por el Host, usando Civilian."))
                                }
                                val clase = game.plugin.supervivienteManager.getClassById(idElegido) ?: liric.mistaken.roles.survivors.clases.Civilian()
                                game.plugin.supervivienteManager.registrarSurvivor(p, clase)

                                if (game.currentMode == MistakenMode.ONE_BOUNCE) {
                                    p.addPotionEffect(PotionEffect(PotionEffectType.SPEED, Int.MAX_VALUE, 0, false, false, false))
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

            // --- APLICAR REGLAS PRIVADAS ---
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
            game.getCurrentAsesino()?.let { killer ->
                game.plugin.webHook.sendGameStart(game.currentMapName, game.currentMode.name, survivorsSolo, killer)
            }
        }
    }

    fun handleInGameTick(players: Collection<Player>, ticks: Int) {
        if (game.asesinosUUIDs.isEmpty()) {
            game.stateController.endGame("game.killer-disconnected", false)
            return
        }

        // Solo evalúa a los asesinos de esta sesión
        val killersOnline = game.asesinosUUIDs.mapNotNull { game.plugin.server.getPlayer(it) }.filter { it.isOnline }

        for (p in players) {
            if (game.plugin.isIgnored(p) || game.isKiller(p.uniqueId) || p.gameMode == GameMode.SPECTATOR || p.isInvisible) continue

            if ((ticks + (p.uniqueId.hashCode() and 0xFFFF)) % 5 == 0) {
                game.uiController.playAmbientForPlayer(p, killersOnline)
            }

            if (ticks % 10 == 0 && killersOnline.isNotEmpty()) {
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

            if (ticks % 5 == 0 && p.passengers.isNotEmpty() && p.isSprinting) {
                game.plugin.playerDataManager.consumeStamina(p.uniqueId, 0.4)
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

        // 🔥 FIX: Obtenemos solo los jugadores de esta sesión
        val sessionPlayers = game.getPlayers()


        val allSurvivors = sessionPlayers.filter { !game.isKiller(it.uniqueId) && it.gameMode == GameMode.SURVIVAL && !it.isInvisible }

        if (allSurvivors.isEmpty()) {
            game.stateController.endGame("game.victory-killer", true)
            return
        }

        val freeSurvivors = allSurvivors.count { !game.combatManager.isFrozen(it) }
        if (freeSurvivors == 0) {
            if (game.currentMode == MistakenMode.FREEZE_TAG && allSurvivors.size > 1) {
                game.stateController.endGame("game.victory-killer", true)
            } else if (game.currentMode != MistakenMode.FREEZE_TAG) {
                game.stateController.endGame("game.victory-killer", true)
            }
        }
    }

    private fun checkLastManStanding() {
        if (game.currentState != GameState.INGAME || lmsActivado) return

        // 🔥 FIX: Solo evaluamos en esta sesión
        val supervivientesVivos = game.getPlayers().filter {
            !game.isKiller(it.uniqueId) && it.gameMode == GameMode.SURVIVAL && !it.isInvisible
        }

        if (supervivientesVivos.size == 1 && game.currentMode != MistakenMode.FREEZE_TAG) {
            lmsActivado = true
            val ultimoHeroe = supervivientesVivos[0]
            triggerLMS(ultimoHeroe)
        }
    }

    private fun triggerLMS(player: Player) {
        val asesinoPlayer = game.getCurrentAsesino()
        val killerClass = asesinoPlayer?.let { game.plugin.asesinoManager.getKillerOfPlayer(it) }
        val customMusic = killerClass?.let { killer ->
            game.plugin.configManager.getKillerConfig(killer.id).getString("lms_music") ?: killer.defaultMusic
        }
        activeLmsMusic = customMusic ?: "mistaken:lms"

        game.uiController.broadcastLMS(player, activeLmsMusic)
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 60, 0))
        if (game.timer > 90) {
            game.timer = 90
        }
    }

    fun handlePlayerDeath(player: Player) {
        if (game.currentState == GameState.ENDING || player.gameMode == GameMode.SPECTATOR || player.isInvisible) return


        if (game.isKiller(player.uniqueId)) {
            game.asesinosUUIDs.remove(player.uniqueId)
            game.plugin.spectatorManager.setCustomSpectator(player)

            if (game.asesinosUUIDs.isEmpty() && game.currentState == GameState.INGAME) {
                game.stateController.endGame("game.victory-survivors", false)
            }
            return
        }

        if (game.currentMode == MistakenMode.INFECTION) {
            game.plugin.supervivienteManager.getSurvivorClass(player)?.cleanup(player)
            game.asesinosUUIDs.add(player.uniqueId)
            player.isSwimming = false
            game.ambientManager.stopAmbience(player)
            game.combatManager.resetHealth(player)

            game.uiController.setLuckPermsPrefix(player, "<red>")

            game.plugin.lobbyLocation?.let { loc ->
                player.teleportAsync(loc)
            }

            player.scheduler.runDelayed(
                game.plugin,
                Consumer { _ ->
                    val claseID = game.plugin.playerDataManager.getSelectedKiller(player.uniqueId)
                    game.plugin.asesinoManager.equipKiller(player, claseID)
                    game.uiController.playRoleTitle(player, true)
                },
                null,
                5L
            )

            game.getPlayers().forEach { it.sendMessage(MiniMessage.miniMessage().deserialize("<dark_red>Infección</dark_red> <dark_gray>»</dark_gray> <red>¡${player.name} ha sido infectado y ahora es un asesino!</red>")) }
            player.world.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 1f)

            game.getCurrentAsesino()?.let { killer ->
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

        game.getCurrentAsesino()?.let { killer ->
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

        // 🔥 FIX: Solo limpiamos a los jugadores de ESTA sesión
        game.getPlayers().forEach { p ->
            p.stopSound(activeLmsMusic, SoundCategory.RECORDS)

            p.passengers.forEach { p.removePassenger(it) }
            p.vehicle?.removePassenger(p)
            p.fireTicks = 0
            p.inventory.clear()
            p.inventory.armorContents = arrayOfNulls(4)
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }

            if (game.isKiller(p.uniqueId)) {
                game.plugin.asesinoManager.getKillerOfPlayer(p)?.cleanup(p)
            } else {
                game.plugin.supervivienteManager.getSurvivorClass(p)?.cleanup(p)
            }

            game.combatManager.removePlayerData(p.uniqueId)

            if (p.isInvisible) {
                p.isInvisible = false
                p.isCollidable = true
                p.isInvulnerable = false
                p.allowFlight = false
                p.isFlying = false

                // Mostrar jugador nuevamente solo a los de esta sesión
                game.getPlayers().forEach { online -> online.showPlayer(game.plugin, p) }
            }

            p.showTitle(Title.title(
                PumpkingServiceManager.messages.getComponent(p, "game.$type-title"),
                PumpkingServiceManager.messages.getComponent(p, "game.$type-subtitle")
            ))

            p.playSound(p.location, winSound, 1f, 1f)
        }

        game.combatManager.clearAll()
        game.asesinosUUIDs.clear()

        // KillerManager y SurvivorManager manejan la limpieza individual por UUID, no deberian afectar a otras partidas.
        game.plugin.asesinoManager.removeAllKillers()
        game.plugin.supervivienteManager.cleanAll()
    }

    fun teleportAllToLobby() {
        val serverMode = game.plugin.serverMode

        game.getPlayers().forEach { p ->
            p.gameMode = GameMode.SURVIVAL

            if (serverMode == "GAME_SERVER") {
                // 🔥 Modo Network: Los pateamos al proxy (Servidor Lobby Principal)
                val lobbyName = game.plugin.config.getString("proxy-lobby-server", "lobby") ?: "lobby"
                BungeeUtils.sendToServer(game.plugin, p, lobbyName)
            } else {
                // 🔥 Modo Multiarena local: Los mandamos al punto de spawn local
                game.plugin.lobbyLocation?.let { loc ->
                    p.teleportAsync(loc)
                }
            }
        }
    }
}


