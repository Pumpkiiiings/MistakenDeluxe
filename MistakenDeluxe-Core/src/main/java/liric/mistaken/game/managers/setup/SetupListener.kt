package liric.mistaken.game.managers.setup

import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent

class SetupListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val arenaName = plugin.setupManager.activeSetups[player.uniqueId] ?: return
        val arena = plugin.arenaManager.getArena(arenaName) ?: return

        val item = event.item ?: return

        // Prevent breaking blocks or triggering real interactions during setup
        event.isCancelled = true

        val action = event.action
        val slot = player.inventory.heldItemSlot

        when (slot) {
            0 -> { // Killer Spawn
                if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                    plugin.arenaManager.setSpawn(arenaName, "asesino", player.location)
                    player.sendMessage(MessageService.getComponent(player, "setup.killer-set"))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                }
            }
            1 -> { // Survivor Spawn
                if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                    plugin.arenaManager.setSpawn(arenaName, "survivor", player.location)
                    player.sendMessage(MessageService.getComponent(player, "setup.survivor-set"))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                }
            }
            2 -> { // Generator
                val clickedBlock = event.clickedBlock
                if (clickedBlock != null) {
                    val allowedGens = setOf(Material.RAW_IRON_BLOCK, Material.IRON_BLOCK, Material.AMETHYST_BLOCK)
                    
                    if (action == Action.LEFT_CLICK_BLOCK) {
                        if (clickedBlock.type !in allowedGens) {
                            player.sendMessage(MessageService.getComponent(player, "setup.gen-invalid"))
                            return
                        }
                        plugin.arenaManager.addGenerator(arenaName, clickedBlock.location)
                        player.sendMessage(MessageService.getComponent(player, "setup.gen-added"))
                        player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)
                    } 
                    else if (action == Action.RIGHT_CLICK_BLOCK) {
                        val currentGens = arena.generators.toMutableList()
                        val removed = currentGens.removeIf { loc ->
                            loc.blockX == clickedBlock.x && loc.blockY == clickedBlock.y && loc.blockZ == clickedBlock.z
                        }
                        if (removed) {
                            plugin.arenaManager.saveGenerators(arenaName, currentGens)
                            player.sendMessage(MessageService.getComponent(player, "setup.gen-removed"))
                            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
                        }
                    }
                }
            }
            8 -> { // Exit
                if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                    plugin.setupManager.stopSetup(player)
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
            }
        }
    }

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        if (plugin.setupManager.activeSetups.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        if (plugin.setupManager.activeSetups.containsKey(event.player.uniqueId)) {
            plugin.setupManager.stopSetup(event.player)
        }
    }
}
