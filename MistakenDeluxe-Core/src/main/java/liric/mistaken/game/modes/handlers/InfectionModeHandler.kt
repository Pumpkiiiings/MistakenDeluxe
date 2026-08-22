package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.modes.ModeHandler

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class InfectionModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {
    private val infectionDeathLocs = ConcurrentHashMap<UUID, Location>()

    override fun onPlayerDeath(player: Player): Boolean {
        // En Infection, los supervivientes se convierten en asesinos al morir
        if (!session.isKiller(player.uniqueId)) {
            plugin.survivorManager.getSurvivorClass(player)?.cleanup(player)
            session.killersUUIDs.add(player.uniqueId)
            player.isSwimming = false
            session.ambientManager.stopAmbience(player)
            session.combatManager.resetHealth(player)

            liric.mistaken.utils.hooks.ObserverHook.setTrueDarkness(player, false)

            session.uiController.setLuckPermsPrefix(player, "<red>")

            plugin.lobbyLocation?.let { loc ->
                player.teleportAsync(loc)
            }

            player.scheduler.runDelayed(
                plugin,
                Consumer { _ ->
                    val claseID = plugin.playerDataManager.getSelectedKiller(player.uniqueId)
                    plugin.killerManager.equipKiller(player, claseID)
                    session.uiController.playRoleTitle(player, true)
                },
                null,
                5L
            )

            session.getPlayers().forEach { it.sendMessage(MiniMessage.miniMessage().deserialize("<dark_red>Infección</dark_red> <dark_gray>»</dark_gray> <red>¡${player.name} ha sido infectado y ahora es un asesino!</red>")) }
            player.world.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 1f)

            session.getCurrentKiller()?.let { killer ->
                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    plugin.statsManager.incrementStat(killer.uniqueId, "kills")
                }
            }

            session.playerController.checkWinCondition()
            return true // Indica que InfectionModeHandler manejó la muerte del jugador
        }
        return false // Era el asesino inicial, o maneja la lógica normal
    }

    override fun onPlayerDeathEvent(event: PlayerDeathEvent) {
        val victim = event.entity
        infectionDeathLocs[victim.uniqueId] = victim.location.clone()
    }

    override fun onPlayerRespawnEvent(event: PlayerRespawnEvent) {
        val player = event.player
        val loc = infectionDeathLocs.remove(player.uniqueId)
        if (loc != null) {
            event.respawnLocation = loc
        }
    }
}
