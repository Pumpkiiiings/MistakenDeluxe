package liric.mistaken.game.managers.setup

import liric.mistaken.Mistaken
import liric.mistaken.game.Arena
import liric.mistaken.config.engine.core.MessageService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SetupManager(private val plugin: Mistaken) {

    // UUID of Player -> Arena Name they are editing
    val activeSetups = ConcurrentHashMap<UUID, String>()
    
    // Player UUID -> Array of ItemStacks (Original Inventory)
    private val savedInventories = ConcurrentHashMap<UUID, Array<ItemStack?>>()
    private val savedArmor = ConcurrentHashMap<UUID, Array<ItemStack?>>()
    
    // Player UUID -> BukkitTask (Particle task)
    private val particleTasks = ConcurrentHashMap<UUID, BukkitTask>()

    private fun createSetupItem(material: Material, nameComp: Component, loreComps: List<Component>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(nameComp.decoration(TextDecoration.ITALIC, false))
        meta.lore(loreComps.map { it.decoration(TextDecoration.ITALIC, false) })
        item.itemMeta = meta
        return item
    }

    fun startSetup(player: Player, arenaName: String) {
        val arena = plugin.arenaManager.getArena(arenaName) ?: return

        activeSetups[player.uniqueId] = arenaName

        // Save inventory
        savedInventories[player.uniqueId] = player.inventory.contents
        savedArmor[player.uniqueId] = player.inventory.armorContents

        // Clear and give tools
        player.inventory.clear()
        player.inventory.armorContents = emptyArray()
        
        // Dynamically create items with player's language
        val itemKiller = createSetupItem(Material.IRON_SWORD, MessageService.getComponent(player, "setup.items.killer.name"), MessageService.getComponentList(player, "setup.items.killer.lore", "messages"))
        val itemSurvivor = createSetupItem(Material.FEATHER, MessageService.getComponent(player, "setup.items.survivor.name"), MessageService.getComponentList(player, "setup.items.survivor.lore", "messages"))
        val itemGen = createSetupItem(Material.REDSTONE, MessageService.getComponent(player, "setup.items.generator.name"), MessageService.getComponentList(player, "setup.items.generator.lore", "messages"))
        val itemExit = createSetupItem(Material.EMERALD, MessageService.getComponent(player, "setup.items.exit.name"), MessageService.getComponentList(player, "setup.items.exit.lore", "messages"))

        player.inventory.setItem(0, itemKiller)
        player.inventory.setItem(1, itemSurvivor)
        player.inventory.setItem(2, itemGen)
        player.inventory.setItem(8, itemExit)

        player.gameMode = org.bukkit.GameMode.CREATIVE

        player.sendMessage(MessageService.getComponent(player, "setup.enter", Placeholder.parsed("arena", arenaName)))
        player.sendMessage(MessageService.getComponent(player, "setup.enter-subtitle"))

        startParticleTask(player, arena)
    }

    fun stopSetup(player: Player) {
        val arenaName = activeSetups.remove(player.uniqueId) ?: return

        // Cancel particles
        particleTasks.remove(player.uniqueId)?.cancel()

        // Restore inventory
        player.inventory.clear()
        val inv = savedInventories.remove(player.uniqueId)
        val armor = savedArmor.remove(player.uniqueId)
        
        if (inv != null) player.inventory.contents = inv
        if (armor != null) player.inventory.armorContents = armor

        player.sendMessage(MessageService.getComponent(player, "setup.exit"))
    }

    private fun startParticleTask(player: Player, arena: Arena) {
        val task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (!player.isOnline) {
                stopSetup(player)
                return@Runnable
            }

            // Draw Killer Spawn (Red)
            arena.killerSpawn?.let { spawn ->
                if (spawn.world?.name == player.world.name) {
                    drawMarker(player, spawn, 255, 0, 0) // Red
                }
            }

            // Draw Survivor Spawns (Green)
            arena.survivorSpawns.forEach { spawn ->
                if (spawn.world?.name == player.world.name) {
                    drawMarker(player, spawn, 0, 255, 0) // Green
                }
            }

            // Draw Generators (Blue)
            arena.generators.forEach { gen ->
                if (gen.world?.name == player.world.name) {
                    drawMarker(player, gen, 0, 100, 255) // Blue
                }
            }
        }, 0L, 10L) // Every half second

        particleTasks[player.uniqueId] = task
    }

    private fun drawMarker(player: Player, loc: Location, r: Int, g: Int, b: Int) {
        val center = loc.clone().add(0.5, 1.0, 0.5)
        player.spawnParticle(Particle.DUST, center, 10, 0.2, 0.2, 0.2, 0.0, Particle.DustOptions(org.bukkit.Color.fromRGB(r, g, b), 1.5f))
    }
}
