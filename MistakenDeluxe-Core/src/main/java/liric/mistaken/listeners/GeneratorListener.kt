package liric.mistaken.listeners

import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

class GeneratorListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private var genBlock: Material = Material.RAW_IRON_BLOCK

    private val activeMenus = ConcurrentHashMap<Location, Inventory>()
    private val colaboradores = ConcurrentHashMap<Location, MutableSet<UUID>>()

    private val menuTitle by lazy { pumpking.lib.service.PumpkingServiceManager.messages.getComponent(null, "listeners.generators.gui_title") }
    private val killerError = mm.deserialize("<red>¡Eres el asesino! No puedes reparar generadores.")

    class GeneratorHolder(val loc: Location) : InventoryHolder {
        override fun getInventory(): Inventory = Bukkit.createInventory(this, 27)
    }

    init {
        val materialName = plugin.config.getString("settings.generator-block", "RAW_IRON_BLOCK")
        genBlock = Material.matchMaterial(materialName ?: "RAW_IRON_BLOCK") ?: Material.RAW_IRON_BLOCK
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.type != genBlock) return

        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return

        if (session.isKiller(player.uniqueId)) {
            player.sendMessage(killerError)
            return
        }

        val loc = block.location

        if (plugin.generatorManager.isCompleted(loc)) {
            val msg = pumpking.lib.service.PumpkingServiceManager.messages.getRawString(player, "messages.already-completed", "<red>Ya completado")
            player.sendMessage(mm.deserialize(msg))
            return
        }

        event.isCancelled = true

        val inv = activeMenus.getOrPut(loc) {
            Bukkit.createInventory(GeneratorHolder(loc), 27, menuTitle)
        }

        actualizarMenuDinamico(inv, plugin.generatorManager.getProgress(loc))
        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? GeneratorHolder ?: return
        event.isCancelled = true

        val clicked = event.currentItem ?: return
        if (clicked.type != Material.IRON_INGOT) return

        val player = event.whoClicked as Player
        val loc = holder.loc

        val session = plugin.sessionManager.getSession(player) ?: return

        val meta = clicked.itemMeta
        val isCorrect = meta != null && meta.hasDisplayName() && mm.serialize(meta.displayName()!!).contains("yellow")

        if (isCorrect) {
            plugin.generatorManager.addProgress(loc, 10)
            player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 0.6f, 1.6f)
            loc.world.spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 1.1, 0.5), 5, 0.1, 0.1, 0.1, 0.05)

            colaboradores.getOrPut(loc) { ConcurrentHashMap.newKeySet() }.add(player.uniqueId)
        } else {
            plugin.generatorManager.addProgress(loc, -10)
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            player.sendMessage(mm.deserialize("<red>¡Fallaste! El progreso ha bajado un 10%."))
            loc.world.spawnParticle(Particle.FLAME, loc.clone().add(0.5, 1.1, 0.5), 8, 0.2, 0.2, 0.2, 0.1)
        }

        val progress = plugin.generatorManager.getProgress(loc)
        if (progress >= 100) {
            finalizarGenerador(loc, event.inventory)
        } else {
            actualizarMenuDinamico(event.inventory, progress)
        }
    }

    private fun actualizarMenuDinamico(inv: Inventory, progress: Int) {
        inv.clear()
        val fakesCount = if (progress >= 80) 6 else if (progress >= 40) 4 else 2

        val slots = (0..26).toMutableList()
        slots.shuffle()

        inv.setItem(slots[0], createRepairItem(progress))
        for (i in 1..fakesCount) {
            inv.setItem(slots[i], createFakeItem(progress))
        }
    }

    private fun finalizarGenerador(loc: Location, inv: Inventory) {
        val listaColaboradores = colaboradores.remove(loc)
        val commands = plugin.config.getStringList("settings.rewards.commands")
        val successMsg = mm.deserialize(pumpking.lib.service.PumpkingServiceManager.messages.getRawString(null, "messages.success", "<green>¡Reparado!"))

        listaColaboradores?.forEach { uuid ->
            val p = Bukkit.getPlayer(uuid)
            if (p != null && p.isOnline) {
                commands.forEach { cmd ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.name))
                }
                p.sendMessage(successMsg)
                p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f)
            }
        }

        ArrayList(inv.viewers).forEach { it.closeInventory() }
        activeMenus.remove(loc)

        plugin.server.regionScheduler.run(plugin, loc) { _ ->
            loc.world.strikeLightningEffect(loc)
        }
    }

    @EventHandler
    fun onMenuClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? GeneratorHolder ?: return
        if (event.inventory.viewers.size <= 1) {
            activeMenus.remove(holder.loc)
        }
    }

    private fun createRepairItem(progress: Int): ItemStack {
        val item = ItemStack(Material.IRON_INGOT)
        item.editMeta { meta ->
            meta.displayName(mm.deserialize("<yellow><bold>¡CLIC RÁPIDO!"))
            val color = if (progress < 40) "<red>" else if (progress < 80) "<yellow>" else "<green>"
            meta.lore(listOf(
                Component.empty(),
                pumpking.lib.service.PumpkingServiceManager.messages.getComponent(null, "listeners.generators.progress_lore", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("color", color), net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("progress", progress.toString())),
                mm.deserialize("<gray>Ayuda a tus compañeros!"),
                Component.empty()
            ))
        }
        return item
    }

    private fun createFakeItem(progress: Int): ItemStack {
        val item = ItemStack(Material.IRON_INGOT)
        item.editMeta { meta ->
            if (progress >= 80) {
                val color = if (ThreadLocalRandom.current().nextBoolean()) "<gold>" else "<red>"
                meta.displayName(mm.deserialize("$color<bold>¡CLIC RÁPIDO!"))
                meta.lore(listOf(
                    Component.empty(),
                    pumpking.lib.service.PumpkingServiceManager.messages.getComponent(null, "listeners.generators.progress_loss_lore"),
                    mm.deserialize("<gray>¿De verdad quieres ayudar?"),
                    Component.empty()
                ))
            } else {
                meta.displayName(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(null, "listeners.generators.not_this_one"))
            }
        }
        return item
    }
}


