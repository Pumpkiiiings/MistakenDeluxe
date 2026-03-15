package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

class SpectatorManager(private val plugin: Mistaken) : Listener {

    class SpectatorHolder : InventoryHolder {
        override fun getInventory(): Inventory = Bukkit.createInventory(this, 9)
    }

    fun setCustomSpectator(player: Player) {
        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = true
        player.isFlying = true
        player.isInvisible = true
        player.isCollidable = false
        player.isInvulnerable = true

        // Ocultarlo de los vivos
        Bukkit.getOnlinePlayers().forEach { online ->
            if (online.gameMode == GameMode.SURVIVAL) {
                online.hidePlayer(plugin, player)
            }
        }

        player.inventory.clear()

        val tpItem = ItemStack(Material.COMPASS).apply {
            editMeta { it.displayName(plugin.mm.deserialize("<aqua><bold>▶ Teletransportarse a Jugador")) }
        }
        val speedItem = ItemStack(Material.FEATHER).apply {
            editMeta { it.displayName(plugin.mm.deserialize("<yellow><bold>▶ Velocidad de Vuelo")) }
        }

        player.inventory.setItem(0, tpItem)
        player.inventory.setItem(4, speedItem)
        player.flySpeed = 0.1f

        player.sendMessage(plugin.mm.deserialize("<green>Estás muerto. Has entrado al modo espectador con ítems."))
    }

    // 🔥 NUEVA FUNCIÓN: Restauración Forzada Total
    fun removeCustomSpectator(player: Player) {
        player.isInvisible = false
        player.isCollidable = true
        player.isInvulnerable = false
        player.allowFlight = false
        player.isFlying = false
        player.flySpeed = 0.1f

        // Fuerza a Bukkit a volver a enviarle los paquetes de este jugador a TODO el servidor
        Bukkit.getOnlinePlayers().forEach { online ->
            online.showPlayer(plugin, player)
            // Aseguramos que él también pueda ver a todos (Por si se bugueó su propio cliente)
            player.showPlayer(plugin, online)
        }
    }

    // 🔥 SEGURIDAD: Si alguien entra a mitad de la partida, que no vea a los muertos flotando
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) {
            Bukkit.getOnlinePlayers().forEach { online ->
                if (online.gameMode == GameMode.ADVENTURE && online.isInvisible) {
                    e.player.hidePlayer(plugin, online)
                }
            }
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (p.gameMode != GameMode.ADVENTURE || !p.isInvisible) return

        e.isCancelled = true
        val item = e.item ?: return

        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            if (item.type == Material.COMPASS) {
                abrirMenuTeleport(p)
            } else if (item.type == Material.FEATHER) {
                toggleFlySpeed(p)
            }
        }
    }

    private fun toggleFlySpeed(p: Player) {
        when (p.flySpeed) {
            0.1f -> { p.flySpeed = 0.2f; p.sendActionBar(plugin.mm.deserialize("<yellow>Velocidad de vuelo: <bold>x2")) }
            0.2f -> { p.flySpeed = 0.4f; p.sendActionBar(plugin.mm.deserialize("<yellow>Velocidad de vuelo: <bold>x4")) }
            else -> { p.flySpeed = 0.1f; p.sendActionBar(plugin.mm.deserialize("<yellow>Velocidad de vuelo: <bold>x1")) }
        }
        p.playSound(p.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
    }

    private fun abrirMenuTeleport(p: Player) {
        val vivos = Bukkit.getOnlinePlayers().filter { it.gameMode == GameMode.SURVIVAL }
        if (vivos.isEmpty()) {
            p.sendMessage(plugin.mm.deserialize("<red>No hay jugadores vivos a los que teletransportarse."))
            return
        }

        val size = ((vivos.size - 1) / 9 + 1) * 9
        val inv = Bukkit.createInventory(SpectatorHolder(), size, plugin.mm.deserialize("<dark_gray>Espectear Jugador"))

        vivos.forEach { target ->
            val head = ItemStack(Material.PLAYER_HEAD).apply {
                editMeta { meta ->
                    if (meta is SkullMeta) meta.owningPlayer = target

                    meta.displayName(plugin.mm.deserialize("<aqua>${target.name}"))
                    val color = if (plugin.gameManager.esAsesino(target.uniqueId)) "<red>Asesino" else "<green>Superviviente"
                    meta.lore(listOf(plugin.mm.deserialize(color)))
                }
            }
            inv.addItem(head)
        }
        p.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val p = e.whoClicked as? Player ?: return

        if (p.gameMode == GameMode.ADVENTURE && p.isInvisible) {
            e.isCancelled = true

            if (e.inventory.holder is SpectatorHolder) {
                val clicked = e.currentItem ?: return
                if (clicked.type == Material.PLAYER_HEAD) {
                    val targetName = PlainTextComponentSerializer.plainText().serialize(clicked.itemMeta.displayName()!!)
                    val target = Bukkit.getPlayerExact(targetName)

                    if (target != null && target.isOnline) {
                        p.teleportAsync(target.location)
                        p.playSound(p.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f)
                    } else {
                        p.sendMessage(plugin.mm.deserialize("<red>Ese jugador ya no está disponible."))
                    }
                    p.closeInventory()
                }
            }
        }
    }

    @EventHandler fun onDrop(e: PlayerDropItemEvent) { if (e.player.gameMode == GameMode.ADVENTURE && e.player.isInvisible) e.isCancelled = true }
    @EventHandler fun onPickup(e: EntityPickupItemEvent) { val p = e.entity as? Player ?: return; if (p.gameMode == GameMode.ADVENTURE && p.isInvisible) e.isCancelled = true }
    @EventHandler fun onDamageHit(e: EntityDamageByEntityEvent) { val p = e.damager as? Player ?: return; if (p.gameMode == GameMode.ADVENTURE && p.isInvisible) e.isCancelled = true }
}
