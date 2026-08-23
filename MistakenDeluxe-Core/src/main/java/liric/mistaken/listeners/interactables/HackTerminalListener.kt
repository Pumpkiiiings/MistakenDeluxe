package liric.mistaken.listeners.interactables

import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.scheduler.BukkitRunnable
import liric.mistaken.config.engine.core.MessageService
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class HackTerminalListener(private val plugin: Mistaken) : Listener {

    private val activeSessions = ConcurrentHashMap<UUID, HackSession>()

    private fun msg(player: Player?, key: String): Component =
        MessageService.getComponent(player, "listeners.hack_terminal.$key")

    private val colors = listOf(
        Material.RED_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE
    )

    class HackTerminalHolder(val loc: Location) : InventoryHolder {
        override fun getInventory(): Inventory = Bukkit.createInventory(this, 27)
    }

    data class HackSession(
        val loc: Location,
        val sequence: List<Material>,
        var currentIndex: Int = 0,
        var isShowing: Boolean = true
    )

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.OBSERVER) return

        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return

        
        if (plugin.spectatorManager.isSpectator(player)) return

        if (session.isKiller(player.uniqueId)) {
            player.sendMessage(msg(player, "killer_error"))
            return
        }

        val loc = block.location

        if (plugin.generatorManager.isCompleted(loc)) {
            player.sendMessage(msg(player, "already_hacked"))
            return
        }

        event.isCancelled = true
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
        startHackSession(player, loc)
    }

    private fun startHackSession(player: Player, loc: Location) {
        val inv = Bukkit.createInventory(HackTerminalHolder(loc), 27, msg(player, "gui_title"))
        player.openInventory(inv)

        
        val seqLength = 4
        val sequence = (1..seqLength).map { colors.random() }

        val hackSession = HackSession(loc, sequence)
        activeSessions[player.uniqueId] = hackSession

        
        val bg = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val bgMeta = bg.itemMeta
        bgMeta?.displayName(Component.empty())
        bg.itemMeta = bgMeta
        for (i in 0 until 27) inv.setItem(i, bg)

        
        inv.setItem(19, createItem(Material.RED_STAINED_GLASS_PANE, msg(player, "color_red")))
        inv.setItem(21, createItem(Material.LIME_STAINED_GLASS_PANE, msg(player, "color_green")))
        inv.setItem(23, createItem(Material.BLUE_STAINED_GLASS_PANE, msg(player, "color_blue")))
        inv.setItem(25, createItem(Material.YELLOW_STAINED_GLASS_PANE, msg(player, "color_yellow")))

        
        object : BukkitRunnable() {
            var step = 0
            override fun run() {
                if (!player.isOnline || player.openInventory.topInventory.holder !is HackTerminalHolder) {
                    this.cancel()
                    return
                }

                if (step < sequence.size) {
                    val mat = sequence[step]
                    inv.setItem(13, createItem(mat, msg(player, "memorize")))
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f)
                    
                    
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        if (player.openInventory.topInventory.holder is HackTerminalHolder) {
                            inv.setItem(13, createItem(Material.GRAY_STAINED_GLASS_PANE, msg(player, "waiting")))
                        }
                    }, 10L)

                    step++
                } else {
                    inv.setItem(13, createItem(Material.GREEN_STAINED_GLASS_PANE, msg(player, "enter_sequence")))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                    hackSession.isShowing = false
                    this.cancel()
                }
            }
        }.runTaskTimer(plugin, 10L, 20L)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? HackTerminalHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val session = activeSessions[player.uniqueId] ?: return

        if (session.isShowing) return 

        val clicked = event.currentItem ?: return
        if (clicked.type !in colors) return

        val expected = session.sequence[session.currentIndex]

        if (clicked.type == expected) {
            session.currentIndex++
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f)
            
            if (session.currentIndex >= session.sequence.size) {
                
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                activeSessions.remove(player.uniqueId)
                player.closeInventory()
                
                plugin.generatorManager.addProgress(session.loc, 25)
                player.sendMessage(msg(player, "success"))
            }
        } else {
            
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            player.sendMessage(msg(player, "fail"))
            activeSessions.remove(player.uniqueId)
            player.closeInventory()
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (event.inventory.holder is HackTerminalHolder) {
            activeSessions.remove(event.player.uniqueId)
        }
    }

    private fun createItem(mat: Material, name: Component): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta
        meta?.displayName(name)
        item.itemMeta = meta
        return item
    }
}
