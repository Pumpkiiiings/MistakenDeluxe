package liric.mistaken.game.managers.gameplay

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class SpectatorManager(private val plugin: Mistaken) : Listener {

    private val activeSpectators = ConcurrentHashMap.newKeySet<UUID>()

    class SpectatorHolder : InventoryHolder {
        override fun getInventory(): Inventory = Bukkit.createInventory(this, 9)
    }

    fun isSpectator(player: Player): Boolean = activeSpectators.contains(player.uniqueId)

    fun setCustomSpectator(player: Player) {
        activeSpectators.add(player.uniqueId)

        // 🔥 FIX VUELO: Usamos SURVIVAL (no ADVENTURE) porque el modo ADVENTURE no soporta vuelo en Minecraft.
        // El jugador es invisible e invulnerable, lo que lo protege como un espectador real.
        player.gameMode = GameMode.SURVIVAL
        player.isInvisible = true
        player.isCollidable = false
        player.isInvulnerable = true

        // Forzamos el vuelo con un pequeño delay para asegurar que el cambio de GameMode no lo cancele
        player.scheduler.runDelayed(plugin, Consumer { _ ->
            player.allowFlight = true
            player.isFlying = true
            player.flySpeed = 0.1f
        }, null, 3L)

        player.inventory.clear()

        val tpItem = ItemStack(Material.COMPASS).apply {
            editMeta { it.displayName(pumpking.lib.color.ColorTranslator.translate("<aqua><bold>▶ Teletransportarse a Jugador")) }
        }
        val speedItem = ItemStack(Material.FEATHER).apply {
            editMeta { it.displayName(pumpking.lib.color.ColorTranslator.translate("<yellow><bold>▶ Velocidad de Vuelo")) }
        }

        player.inventory.setItem(0, tpItem)
        player.inventory.setItem(4, speedItem)

        // Ocultar de los vivos
        Bukkit.getOnlinePlayers().forEach { online ->
            if (online != player) {
                if (isSpectator(online)) {
                    plugin.visibilityManager.showPlayer(player, online)
                    plugin.visibilityManager.showPlayer(online, player)
                } else {
                    plugin.visibilityManager.hidePlayer(player, online)
                }
            }
        }
        player.sendMessage(pumpking.lib.color.ColorTranslator.translate("<green>Has entrado al modo espectador invisible."))
    }

    fun removeCustomSpectator(player: Player) {
        activeSpectators.remove(player.uniqueId)

        player.isInvisible = false
        player.isCollidable = true
        player.isInvulnerable = false
        player.allowFlight = false
        player.isFlying = false
        player.flySpeed = 0.1f
        player.inventory.clear()

        val session = plugin.sessionManager.getSession(player)

        if (session?.currentState != GameState.ENDING) {
            player.gameMode = GameMode.SURVIVAL
        }

        Bukkit.getOnlinePlayers().forEach { online ->
            plugin.visibilityManager.showPlayer(player, online)
            plugin.visibilityManager.showPlayer(online, player)
        }
    }

    // 🔥 FIX: Restaurar vuelo al cambiar de mundo (Lobby -> Arena)
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(e: PlayerChangedWorldEvent) {
        val p = e.player
        if (isSpectator(p)) {
            // Delay obligatorio: Bukkit resetea el vuelo al spawnear en el nuevo mundo
            p.scheduler.runDelayed(plugin, Consumer { _ ->
                p.gameMode = GameMode.SURVIVAL
                p.allowFlight = true
                p.isFlying = true
                p.flySpeed = 0.1f
            }, null, 2L)
        }
    }

    // 🔥 FIX: Restaurar vuelo después de un Teleport
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(e: PlayerTeleportEvent) {
        val p = e.player
        if (isSpectator(p)) {
            p.scheduler.runDelayed(plugin, Consumer { _ ->
                p.allowFlight = true
                p.isFlying = true
            }, null, 2L)
        }
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val player = e.player
        // 🔥 MULTIARENA: Buscamos la sesión a la que acaba de entrar el jugador
        val session = plugin.sessionManager.getSession(player)

        // Si la sesión existe y ya están jugando
        if (session != null && session.currentState == GameState.INGAME) {
            activeSpectators.forEach { specUUID ->
                val spectator = Bukkit.getPlayer(specUUID)
                if (spectator != null && spectator.isOnline) {
                    // 🔥 Solo ocultamos al espectador si pertenece a la MISMA arena que el que entra
                    if (plugin.sessionManager.getSession(spectator) == session) {
                        plugin.visibilityManager.hidePlayer(spectator, player)
                    }
                }
            }
        } else {
            // Si entra al Lobby o la partida no ha empezado, nos aseguramos que no sea espectador
            removeCustomSpectator(player)
        }
    }
    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        removeCustomSpectator(e.player)
        plugin.visibilityManager.removePlayer(e.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInteract(e: PlayerInteractEvent) {
        if (!isSpectator(e.player)) return
        e.isCancelled = true
        val item = e.item ?: return
        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            when (item.type) {
                Material.COMPASS -> abrirMenuTeleport(e.player)
                Material.FEATHER -> toggleFlySpeed(e.player)
                else -> {}
            }
        }
    }

    private fun toggleFlySpeed(p: Player) {
        p.flySpeed = when (p.flySpeed) {
            0.1f -> 0.2f.also { p.sendActionBar(pumpking.lib.color.ColorTranslator.translate("<yellow>Velocidad: <bold>x2")) }
            0.2f -> 0.4f.also { p.sendActionBar(pumpking.lib.color.ColorTranslator.translate("<yellow>Velocidad: <bold>x4")) }
            else -> 0.1f.also { p.sendActionBar(pumpking.lib.color.ColorTranslator.translate("<yellow>Velocidad: <bold>x1")) }
        }
        p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
    }

    private fun abrirMenuTeleport(p: Player) {
        val vivos = Bukkit.getOnlinePlayers().filter { it.gameMode == GameMode.SURVIVAL && !isSpectator(it) }
        if (vivos.isEmpty()) {
            p.sendMessage(pumpking.lib.color.ColorTranslator.translate("<red>No hay jugadores vivos."))
            return
        }

        val inv = Bukkit.createInventory(SpectatorHolder(), 27, pumpking.lib.color.ColorTranslator.translate("<dark_gray>Espectear Jugador"))
        vivos.forEach { target ->
            val head = ItemStack(Material.PLAYER_HEAD).apply {
                editMeta { meta ->
                    if (meta is SkullMeta) meta.owningPlayer = target
                    meta.displayName(pumpking.lib.color.ColorTranslator.translate("<aqua>${target.name}"))
                }
            }
            inv.addItem(head)
        }
        p.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val p = e.whoClicked as? Player ?: return
        if (!isSpectator(p)) return
        e.isCancelled = true

        if (e.inventory.holder is SpectatorHolder) {
            val clicked = e.currentItem ?: return
            val targetName = PlainTextComponentSerializer.plainText().serialize(clicked.itemMeta.displayName()!!)
            Bukkit.getPlayerExact(targetName)?.let { target ->
                p.teleportAsync(target.location).thenAccept {
                    p.playSound(p.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f)
                }
            }
            p.closeInventory()
        }
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) { if (isSpectator(e.player)) e.isCancelled = true }
    @EventHandler
    fun onPickup(e: EntityPickupItemEvent) { val p = e.entity as? Player ?: return; if (isSpectator(p)) e.isCancelled = true }
    @EventHandler
    fun onDamageHit(e: EntityDamageByEntityEvent) { val p = e.damager as? Player ?: return; if (isSpectator(p)) e.isCancelled = true }
    @EventHandler
    fun onTakeDamage(e: EntityDamageEvent) { val p = e.entity as? Player ?: return; if (isSpectator(p)) e.isCancelled = true }
}