package liric.mistaken.game.logic

import liric.mistaken.game.GameManager
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
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

class GamePlayerController(private val game: GameManager) {

    // 🔥 Variable para controlar que el evento LMS no se dispare más de 1 vez por partida
    private var lmsActivado = false

    fun setupPlayers(arena: liric.mistaken.game.Arena) {
        val onlinePlayers = game.plugin.server.onlinePlayers.filter { !game.plugin.isIgnored(it) }.toMutableList()
        if (onlinePlayers.isEmpty()) return

        game.asesinosUUIDs.clear()
        val candidatos = onlinePlayers.filter { !game.yaJugaronAsesino.contains(it.uniqueId) }.toMutableList()
        if (candidatos.isEmpty()) {
            game.yaJugaronAsesino.clear()
            candidatos.addAll(onlinePlayers)
        }
        candidatos.shuffle()

        val killersToSelect = when (game.currentMode) {
            MistakenMode.DOUBLE_KILLER -> if (onlinePlayers.size >= 4) 2 else 1
            MistakenMode.ONE_BOUNCE -> (onlinePlayers.size - 1).coerceAtLeast(1)
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

        for (p in onlinePlayers) {
            val isKiller = game.esAsesino(p.uniqueId)
            p.inventory.clear()
            game.combatManager.resetHealth(p)
            p.gameMode = GameMode.SURVIVAL

            if (isKiller) {
                game.uiController.setLuckPermsPrefix(p, "<red>")
                val spawnLoc = arena.asesinoSpawn ?: p.world.spawnLocation

                p.teleportAsync(spawnLoc).thenAccept { success ->
                    if (success && p.isOnline) {
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
                                val clase = game.plugin.supervivienteManager.getClasePorId(idElegido) ?: liric.mistaken.supervivientes.clases.Civil()
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
                game.plugin.discordManager.sendGameStart(game.currentMapName, game.currentMode.name, survivorsSolo, killer)
            }
        }
    }

    fun handleInGameTick(players: Collection<Player>, ticks: Int) {
        if (game.asesinosUUIDs.isEmpty()) {
            game.stateController.endGame("game.killer-disconnected", false)
            return
        }

        val killersOnline = game.asesinosUUIDs.mapNotNull { game.plugin.server.getPlayer(it) }.filter { it.isOnline }

        for (p in players) {
            if (game.plugin.isIgnored(p) || game.esAsesino(p.uniqueId) || p.gameMode == GameMode.SPECTATOR) continue

            if ((ticks + (p.uniqueId.hashCode() and 0xFFFF)) % 5 == 0) {
                game.uiController.playAmbientForPlayer(p, killersOnline)
            }

            if (ticks % 10 == 0 && killersOnline.isNotEmpty()) {
                game.uiController.checkHeartbeat(p, killersOnline[0])
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
            if (game.timer <= 0) game.stateController.endGame("game.victory-survivors", false)
            else checkWinCondition()
        }
    }

    fun checkWinCondition() {
        if (game.currentState != GameState.INGAME) return
        val allSurvivors = game.plugin.server.onlinePlayers.filter { !game.esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL }

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

    // 🔥 NUEVA FUNCIÓN: Chequeo de Last Man Standing
    private fun checkLastManStanding() {
        if (game.currentState != GameState.INGAME || lmsActivado) return

        val supervivientesVivos = game.plugin.server.onlinePlayers.filter {
            !game.esAsesino(it.uniqueId) && it.gameMode == GameMode.SURVIVAL
        }

        // Si solo queda 1 superviviente y el modo NO es Freeze Tag (opcional)
        if (supervivientesVivos.size == 1 && game.currentMode != MistakenMode.FREEZE_TAG) {
            lmsActivado = true
            val ultimoHeroe = supervivientesVivos[0]
            triggerLMS(ultimoHeroe)
        }
    }

    // 🔥 NUEVA FUNCIÓN: Ejecución de LMS
    private fun triggerLMS(player: Player) {
        // Llamamos al GameUIController que ya tiene los mensajes dinámicos
        game.uiController.broadcastLMS(player)

        // Le damos un pequeño buff al último jugador para darle esperanza
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 60, 0)) // Velocidad I por 60 seg
    }

    fun handlePlayerDeath(player: Player) {
        // 🔥 EL ESCUDO: Si ya está en espectador, ignoramos el código (Evita muerte triple)
        if (player.gameMode == GameMode.SPECTATOR) return

        if (game.esAsesino(player.uniqueId)) {
            game.asesinosUUIDs.remove(player.uniqueId)
            player.gameMode = GameMode.SPECTATOR
            if (game.asesinosUUIDs.isEmpty() && game.currentState == GameState.INGAME) {
                game.stateController.endGame("game.victory-survivors", false)
            }
            return
        }

        // Pasamos al jugador a espectador INMEDIATAMENTE
        player.gameMode = GameMode.SPECTATOR
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

        // 🔥 Se llama aquí para checar si la muerte dejó a un solo jugador vivo
        checkLastManStanding()
        checkWinCondition()
    }

    fun cleanupAllPlayers(killerWon: Boolean) {
        // 🔥 Reseteamos el LMS para la siguiente partida
        lmsActivado = false

        val winSound = if (killerWon) Sound.ENTITY_WITHER_SPAWN else Sound.UI_TOAST_CHALLENGE_COMPLETE
        val type = if (killerWon) "killer" else "survivor"

        game.plugin.server.onlinePlayers.forEach { p ->
            // Detener la música LMS si seguía sonando al acabar la partida
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

            liric.mistaken.utils.SpectatorUtils.setSafeSpectator(p)

            p.showTitle(net.kyori.adventure.title.Title.title(
                game.plugin.messageConfig.getMessage(p, "game.$type-title"),
                game.plugin.messageConfig.getMessage(p, "game.$type-subtitle")
            ))
            p.playSound(p.location, winSound, 1f, 1f)
        }

        game.asesinosUUIDs.clear()
        game.plugin.asesinoManager.removerTodosLosAsesinos()
        game.plugin.supervivienteManager.limpiarTodo()
    }

    fun teleportAllToLobby() {
        game.plugin.lobbyLocation?.let { loc ->
            game.plugin.server.onlinePlayers.forEach { p ->
                p.teleportAsync(loc)
                p.gameMode = GameMode.SURVIVAL
            }
        }
    }
}
