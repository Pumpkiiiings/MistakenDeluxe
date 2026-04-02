package liric.mistaken.game.managers

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
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpectatorManager(private val plugin: Mistaken) : Listener {

    // 🔥 LA CLAVE PARA EVITAR BUGS: Mantener un registro exacto de quién es espectador.
    private val activeSpectators = ConcurrentHashMap.newKeySet<UUID>()

    class SpectatorHolder : InventoryHolder {
        override fun getInventory(): Inventory = Bukkit.createInventory(this, 9)
    }

    fun setCustomSpectator(player: Player) {
        activeSpectators.add(player.uniqueId) // Lo registramos primero

        player.gameMode = GameMode.ADVENTURE
        player.isInvisible = true // Ayuda extra visual
        player.isCollidable = false
        player.isInvulnerable = true

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            player.allowFlight = true
            player.isFlying = true
            player.flySpeed = 0.1f
        }, 1L)

        player.inventory.clear()

        // Objetos del espectador
        val tpItem = ItemStack(Material.COMPASS).apply {
            editMeta { it.displayName(plugin.mm.deserialize("<aqua><bold>▶ Teletransportarse a Jugador")) }
        }
        val speedItem = ItemStack(Material.FEATHER).apply {
            editMeta { it.displayName(plugin.mm.deserialize("<yellow><bold>▶ Velocidad de Vuelo")) }
        }

        player.inventory.setItem(0, tpItem)
        player.inventory.setItem(4, speedItem)

        // Ocultarlo de los vivos, pero permitir que otros espectadores lo vean
        Bukkit.getOnlinePlayers().forEach { online ->
            if (online != player) {
                if (isSpectator(online)) {
                    // Si el otro también es espectador, que se vean entre ellos
                    online.showPlayer(plugin, player)
                    player.showPlayer(plugin, online)
                } else {
                    // Si el otro está vivo, le ocultamos a este espectador
                    online.hidePlayer(plugin, player)
                }
            }
        }

        player.sendMessage(plugin.mm.deserialize("<green>Estás muerto. Has entrado al modo espectador con ítems."))
    }

    fun removeCustomSpectator(player: Player) {
        // 🔥 FIX CRÍTICO: Aunque no esté en la lista, forzamos la restauración de propiedades visuales/físicas
        // para asegurarnos de que Bukkit lo resetee al 100%.
        activeSpectators.remove(player.uniqueId)

        // 🔥 FIX CINEMÁTICA: Si el juego está en fase ENDING, NO le quitamos el GameMode SPECTATOR
        // Porque el CinematicManager lo necesita así para rotar la cámara.
        if (plugin.gameManager.currentState != GameState.ENDING) {
            player.gameMode = GameMode.SURVIVAL
        }

        // Restaurar físicas y visibilidad siempre
        player.isInvisible = false
        player.isCollidable = true
        player.isInvulnerable = false
        player.allowFlight = false
        player.isFlying = false
        player.flySpeed = 0.1f

        // Limpiamos los ítems de vuelo (Brújula y Pluma)
        player.inventory.clear()

        // 🔥 FIX CRÍTICO: Forzar a TODO EL SERVIDOR a volver a ver a este jugador
        Bukkit.getOnlinePlayers().forEach { online ->
            if (online != player) {
                online.showPlayer(plugin, player)
                player.showPlayer(plugin, online)
            }
        }
    }

    fun isSpectator(player: Player): Boolean {
        return activeSpectators.contains(player.uniqueId)
    }

    // 🔥 FIX BUGS DE ENTRADA: Si alguien entra a mitad de partida, no verá a los fantasmas
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val p = e.player

        // Si el juego NO está en curso, asegúrate de que el que entra pueda ver a todos (y todos a él)
        if (plugin.gameManager.currentState != GameState.INGAME) {
            removeCustomSpectator(p)
            return
        }

        // Si el juego ESTÁ en curso, le ocultamos a todos los que son espectadores
        activeSpectators.forEach { specUUID ->
            val spectator = Bukkit.getPlayer(specUUID)
            if (spectator != null && spectator.isOnline) {
                p.hidePlayer(plugin, spectator)
            }
        }
    }

    // 🔥 FIX BUGS DE SALIDA: Si un fantasma se sale, lo limpiamos para que no vuelva bugeado
    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        removeCustomSpectator(e.player)
    }

    // PROTECCIONES GLOBALES PARA EL ESPECTADOR
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (!isSpectator(p)) return

        e.isCancelled = true // Evita que pise placas de presión, abra puertas, etc.

        val item = e.item ?: return
        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            when (item.type) {
                Material.COMPASS -> abrirMenuTeleport(p)
                Material.FEATHER -> toggleFlySpeed(p)
                else -> {}
            }
        }
    }

    private fun toggleFlySpeed(p: Player) {
        when (p.flySpeed) {
            0.1f -> { p.flySpeed = 0.2f; p.sendActionBar(plugin.mm.deserialize("<yellow>Velocidad de vuelo: <bold>x2")) }
            0.2f -> { p.flySpeed = 0.4f; p.sendActionBar(plugin.mm.deserialize("<yellow>Velocidad de vuelo: <bold>x4")) }
            else -> { p.flySpeed = 0.1f; p.sendActionBar(plugin.mm.deserialize("<yellow>Velocidad de vuelo: <bold>x1")) }
        }
        p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
    }

    private fun abrirMenuTeleport(p: Player) {
        val vivos = Bukkit.getOnlinePlayers().filter { !isSpectator(it) }

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

        if (isSpectator(p)) {
            e.isCancelled = true // No pueden mover ítems de su inventario

            if (e.inventory.holder is SpectatorHolder) {
                val clicked = e.currentItem ?: return
                if (clicked.type == Material.PLAYER_HEAD) {
                    val targetName = PlainTextComponentSerializer.plainText().serialize(clicked.itemMeta.displayName()!!)
                    val target = Bukkit.getPlayerExact(targetName)

                    if (target != null && target.isOnline) {
                        p.teleportAsync(target.location)
                        p.playSound(p.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f)
                    } else {
                        p.sendMessage(plugin.mm.deserialize("<red>Ese jugador ya no está disponible."))
                    }
                    p.closeInventory()
                }
            }
            @EventHandler(priority = EventPriority.MONITOR)
            fun onWorldChange(e: org.bukkit.event.player.PlayerChangedWorldEvent) {
                val p = e.player
                if (isSpectator(p)) {
                    // Necesitamos un delay de 1 tick porque Bukkit resetea el vuelo justo DESPUÉS del cambio de mundo
                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                        if (p.isOnline && isSpectator(p)) {
                            p.allowFlight = true
                            p.isFlying = true
                            // Opcional: asegurarnos que la velocidad sea la correcta
                            p.flySpeed = 0.1f
                        }
                    }, 1L)
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onTeleport(e: org.bukkit.event.player.PlayerTeleportEvent) {
                val p = e.player
                if (isSpectator(p)) {
                    // Forzamos que después de cualquier TP siga volando
                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                        if (p.isOnline && isSpectator(p)) {
                            p.allowFlight = true
                            p.isFlying = true
                        }
                    }, 2L) // 2 ticks para estar seguros
                }
            }
        }

    }

    // 🔥 PREVENIR INTERACCIONES MIENTRAS ESTÁN EN VANISH
    @EventHandler fun onDrop(e: PlayerDropItemEvent) { if (isSpectator(e.player)) e.isCancelled = true }
    @EventHandler fun onPickup(e: EntityPickupItemEvent) { val p = e.entity as? Player ?: return; if (isSpectator(p)) e.isCancelled = true }
    @EventHandler fun onDamageHit(e: EntityDamageByEntityEvent) { val p = e.damager as? Player ?: return; if (isSpectator(p)) e.isCancelled = true }
    @EventHandler fun onTakeDamage(e: EntityDamageEvent) { val p = e.entity as? Player ?: return; if (isSpectator(p)) e.isCancelled = true }
}
