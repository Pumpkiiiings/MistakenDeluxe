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

class GamePlayerController(private val game: GameSession) {

    private var lmsActivado = false

    fun setupPlayers(arena: liric.mistaken.game.Arena) {
        // 🔥 FIX: Solo tomamos los jugadores de ESTA sesión
        val sessionPlayers = game.getPlayers().filter { !game.plugin.isIgnored(it) }.toMutableList()
        if (sessionPlayers.isEmpty()) return

        game.asesinosUUIDs.clear()

        // --- 1. MODO ASESINO PVP (Extremo) ---
        if (game.currentMode == MistakenMode.ASSASSIN_PVP) {
            sessionPlayers.shuffled().forEachIndexed { index, p ->
                game.asesinosUUIDs.add(p.uniqueId)

                val spawns = arena.survivorSpawns
                val spawnLoc = if (spawns.isEmpty()) arena.asesinoSpawn ?: p.world.spawnLocation else spawns[index % spawns.size]

                p.inventory.clear()
                game.combatManager.resetHealth(p)

                if (p.gameMode == GameMode.SPECTATOR) p.spectatorTarget = null
                p.gameMode = GameMode.SURVIVAL

                game.uiController.setLuckPermsPrefix(p, "<dark_red>")

                p.teleportAsync(spawnLoc).thenAccept { success ->
                    if (success && p.isOnline) {
                        val claseID = game.plugin.playerDataManager.getSelectedKiller(p.uniqueId)
                        game.plugin.asesinoManager.equiparAsesino(p, claseID)
                    }
                }
                game.uiController.playRoleTitle(p, true)
            }
            return
        }

        // --- 2. MODOS CLÁSICOS ---
        val candidatos = sessionPlayers.filter { !game.yaJugaronAsesino.contains(it.uniqueId) }.toMutableList()
        if (candidatos.isEmpty()) {
            game.yaJugaronAsesino.clear()
            candidatos.addAll(sessionPlayers)
        }
        candidatos.shuffle()

        val killersToSelect = when (game.currentMode) {
            MistakenMode.DOUBLE_KILLER -> if (sessionPlayers.size >= 4) 2 else 1
            MistakenMode.ONE_BOUNCE -> (sessionPlayers.size - 1).coerceAtLeast(1)
            else -> 1
        }

        for (i in 0 until min(killersToSelect, candidatos.size)) {
            val uuid = candidatos[i].uniqueId
            game.asesinosUUIDs.add(uuid)
            game.yaJugaronAsesino.add(uuid)
        }
        game.currentAsesinoUUID = game.asesinosUUIDs.firstOrNull()

        var survivorIndex = 0
        val survivorsSolo = mutableListOf<Player>()

        for (p in sessionPlayers) {
            game.plugin.spectatorManager.removeCustomSpectator(p)

            val isKiller = game.esAsesino(p.uniqueId)
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

                        val claseID = game.plugin.playerDataManager.getSelectedKiller(p.uniqueId)
                        game.plugin.asesinoManager.equiparAsesino(p, claseID)
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
                                val idElegido = game.plugin.playerDataManager.getSelectedSurvivor(p.uniqueId)
                                val clase = game.plugin.supervivienteManager.getClasePorId(idElegido) ?: liric.mistaken.roles.supervivientes.clases.Civil()
                                game.plugin.supervivienteManager.registrarSuperviviente(p, clase)
                            }
                        }
                    },
                    null,
                    delayTicks.coerceAtLeast(1L)
                )
            }

            game.uiController.playRoleTitle(p, isKiller)
        }

        game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
            game.getCurrentAsesino()?.let { killer ->
                game.plugin.webHook.sendGameStart(game.currentMapName, game.currentMode.name, survivorsSolo, killer)
            }
        }
    }

    fun handleInGameTick(players: Collection<Player>, ticks: Int) {
        if (game.asesinosUUIDs.isEmpty() && game.currentMode != MistakenMode.ASSASSIN_PVP) {
            game.stateController.endGame("game.killer-disconnected", false)
            return
        }

        // Solo evalúa a los asesinos de esta sesión
        val killersOnline = game.asesinosUUIDs.mapNotNull { game.plugin.server.getPlayer(it) }.filter { it.isOnline }

        for (p in players) {
            if (game.plugin.isIgnored(p) || game.esAsesino(p.uniqueId) || p.gameMode == GameMode.SPECTATOR || p.isInvisible || game.currentMode == MistakenMode.ASSASSIN_PVP) continue

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
            if (game.timer <= 0 && game.currentMode != MistakenMode.ASSASSIN_PVP) {
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

        if (game.currentMode == MistakenMode.ASSASSIN_PVP) {
            val aliveAssassins = sessionPlayers.count { !game.plugin.isIgnored(it) && it.gameMode == GameMode.SURVIVAL && !it.isInvisible }
            if (aliveAssassins <= 1) {
                game.stateController.endGame("discord.reason_killer_won", true)
            }
            return
        }

        val allSurvivors = sessionPlayers.filter { !game.esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL && !it.isInvisible }

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
        if (game.currentState != GameState.INGAME || lmsActivado || game.currentMode == MistakenMode.ASSASSIN_PVP) return

        // 🔥 FIX: Solo evaluamos en esta sesión
        val supervivientesVivos = game.getPlayers().filter {
            !game.esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL && !it.isInvisible
        }

        if (supervivientesVivos.size == 1 && game.currentMode != MistakenMode.FREEZE_TAG) {
            lmsActivado = true
            val ultimoHeroe = supervivientesVivos[0]
            triggerLMS(ultimoHeroe)
        }
    }

    private fun triggerLMS(player: Player) {
        game.uiController.broadcastLMS(player)
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 60, 0))
    }

    fun handlePlayerDeath(player: Player) {
        if (game.currentState == GameState.ENDING || player.gameMode == GameMode.SPECTATOR || player.isInvisible) return

        if (game.currentMode == MistakenMode.ASSASSIN_PVP) {
            game.plugin.spectatorManager.setCustomSpectator(player)
            player.isSwimming = false
            game.broadcastLocalized("game.player-died", Placeholder.parsed("player", player.name))
            player.playSound(player.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)

            checkWinCondition()
            return
        }

        if (game.esAsesino(player.uniqueId)) {
            game.asesinosUUIDs.remove(player.uniqueId)
            game.plugin.spectatorManager.setCustomSpectator(player)

            if (game.asesinosUUIDs.isEmpty() && game.currentState == GameState.INGAME) {
                game.stateController.endGame("game.victory-survivors", false)
            }
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
            p.stopSound("mistaken:lms", SoundCategory.RECORDS)

            p.passengers.forEach { p.removePassenger(it) }
            p.vehicle?.removePassenger(p)
            p.fireTicks = 0
            p.inventory.clear()
            p.inventory.armorContents = arrayOfNulls(4)
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }

            if (game.esAsesino(p.uniqueId)) {
                game.plugin.asesinoManager.getAsesinoDelJugador(p)?.cleanup(p)
            } else {
                game.plugin.supervivienteManager.getClase(p)?.cleanup(p)
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
                game.plugin.messageConfig.getMessage(p, if (game.currentMode == MistakenMode.ASSASSIN_PVP) "lms.title" else "game.$type-title"),
                game.plugin.messageConfig.getMessage(p, if (game.currentMode == MistakenMode.ASSASSIN_PVP) "game.killer-subtitle" else "game.$type-subtitle")
            ))

            p.playSound(p.location, winSound, 1f, 1f)
        }

        game.combatManager.clearAll()
        game.asesinosUUIDs.clear()

        // AsesinoManager y SupervivienteManager manejan la limpieza individual por UUID, no deberian afectar a otras partidas.
        game.plugin.asesinoManager.removerTodosLosAsesinos()
        game.plugin.supervivienteManager.limpiarTodo()
    }

    fun teleportAllToLobby() {
        val serverMode = game.plugin.serverMode

        game.getPlayers().forEach { p ->
            p.gameMode = org.bukkit.GameMode.SURVIVAL

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
