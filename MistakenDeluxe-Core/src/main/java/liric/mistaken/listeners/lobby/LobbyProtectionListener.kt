package liric.mistaken.listeners.lobby

import liric.mistaken.Mistaken
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerInteractEvent

class LobbyProtectionListener(private val plugin: Mistaken) : Listener {

    private fun isLobby(worldName: String?): Boolean {
        if (worldName == null) return false
        val lobbyWorld = plugin.lobbyLocation?.world?.name ?: return false
        return worldName == lobbyWorld
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        
        if (isLobby(player.world.name)) {
            event.isCancelled = true
            if (event.cause == EntityDamageEvent.DamageCause.VOID) {
                plugin.lobbyLocation?.let { player.teleportAsync(it) }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        
        if (isLobby(player.world.name)) {
            event.isCancelled = true
            player.foodLevel = 20
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCropTrample(event: PlayerInteractEvent) {
        if (event.action == Action.PHYSICAL) {
            val block = event.clickedBlock ?: return
            if (block.type == Material.FARMLAND) {
                if (isLobby(event.player.world.name)) {
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (isLobby(event.location.world?.name)) {
            event.blockList().clear()
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (isLobby(event.block.world.name)) {
            event.blockList().clear()
            event.isCancelled = true
        }
    }
}
