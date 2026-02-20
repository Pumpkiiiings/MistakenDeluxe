package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0]
 * GameListener: El árbitro de la partida.
 * Optimizado para Paper 1.21.4 con filtrado rápido de eventos.
 */
class GameListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        // 1. Salida rápida si no hay partida o el plugin no está listo
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return

        val victim = event.entity as? Player ?: return
        val damager = when (val attacker = event.damager) {
            is Player -> attacker
            is Projectile -> attacker.shooter as? Player
            else -> null
        } ?: return

        // 🧊 REGLA FREEZE: Jugadores congelados son invulnerables a golpes
        if (plugin.combatManager.isFrozen(victim)) {
            event.isCancelled = true
            return
        }

        val damagerUUID = damager.uniqueId
        val victimUUID = victim.uniqueId

        // A. EL ASESINO ATACA
        if (plugin.gameManager.esAsesino(damagerUUID)) {
            event.damage = 0.0 // Usamos nuestro sistema de corazones (0-6)
            plugin.combatManager.takeDamage(victim)

            // Efectos visuales de sangre (Bloques de redstone)
            victim.world.spawnParticle(
                Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10,
                0.1, 0.1, 0.1, Material.REDSTONE_BLOCK.createBlockData()
            )
            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.5f)
            return
        }

        // B. EL SUPERVIVIENTE SE DEFIENDE (Golpea al Asesino)
        if (plugin.gameManager.esAsesino(victimUUID)) {
            // Mostramos la salud custom del asesino en la ActionBar del sobreviviente
            val killerHealth = plugin.combatManager.getHealth(victim)
            damager.sendActionBar(plugin.messageConfig.getMessage(damager, "game.killer-hit-actionbar",
                Placeholder.parsed("health", killerHealth.toString())))

            // Probabilidad de Stun (15%)
            if (ThreadLocalRandom.current().nextInt(100) < 15) {
                aplicarStunAlAsesino(victim, damager)
            }
            // Los golpes de sobrevivientes no bajan vida real, solo activan stun
            event.damage = 0.0
            return
        }

        // C. FUEGO AMIGO: Bloqueado por defecto
        event.isCancelled = true
    }

    private fun aplicarStunAlAsesino(killer: Player, damager: Player) {
        // Limpiar Speed y aplicar debuffs
        killer.removePotionEffect(PotionEffectType.SPEED)
        killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
        killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3))

        killer.playSound(killer.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 0.5f)

        killer.sendMessage(plugin.messageConfig.getMessage(killer, "game.killer-stunned-victim"))
        damager.sendMessage(plugin.messageConfig.getMessage(damager, "game.killer-stunned-damager"))

        killer.world.spawnParticle(Particle.ENCHANTED_HIT, killer.location.add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return
        val player = event.entity as? Player ?: return

        if (plugin.combatManager.isFrozen(player)) {
            // En modo Freeze Tag, el hielo no se asfixia ni se congela
            if (event.cause == EntityDamageEvent.DamageCause.FREEZE ||
                event.cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        // 1. Primero checamos el tipo (Ahorra CPU antes de leer el título)
        val type = event.inventory.type
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) return

        // 2. Solo si es un cofre/bloque, verificamos el título para nuestras GUIs
        val title = plain.serialize(event.view.title())
        val allowedKeywords = listOf("Reparando", "Skill Check", "ENTES", "Tienda", "Selecciona")

        if (allowedKeywords.any { title.contains(it) }) return

        // Bloquear el resto (Cofres del mapa, hornos, etc)
        event.isCancelled = true
    }

    @EventHandler
    fun onHungerChange(event: FoodLevelChangeEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) {
            event.isCancelled = true
            // Aseguramos que la barra de comida siempre se vea llena si la estamina no la está usando
            if (event.foodLevel < 20) (event.entity as Player).foodLevel = 20
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        // No dropear nada al morir para mantener el suelo limpio (Rendimiento)
        event.drops.clear()
        event.droppedExp = 0

        // El plugin maneja la muerte por su cuenta
        plugin.gameManager.handlePlayerDeath(event.entity)
    }

    // --- BLOQUEOS DE ACCIÓN DURANTE EL JUEGO ---

    @EventHandler fun onDrop(e: PlayerDropItemEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) e.isCancelled = true
    }

    @EventHandler fun onCraft(e: CraftItemEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) e.isCancelled = true
    }

    @EventHandler fun onBreak(e: BlockBreakEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME && !e.player.hasPermission("mistaken.admin")) {
            e.isCancelled = true
        }
    }

    @EventHandler fun onPlace(e: BlockPlaceEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME && !e.player.hasPermission("mistaken.admin")) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerDismount(event: PlayerToggleSneakEvent) {
        // En 1.21.4, el sneak es la forma estándar de soltar a alguien que llevas cargado (si usas pasajeros)
        val player = event.player
        if (event.isSneaking && player.passengers.isNotEmpty()) {
            plugin.combatManager.soltarPasajero(player)
        }
    }

    @EventHandler
    fun onRegen(event: EntityRegainHealthEvent) {
        // En Mistaken, la salud no se recupera comiendo, solo por habilidades
        if (plugin.gameManager.currentState == GameState.INGAME) {
            val reason = event.regainReason
            if (reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
                reason == EntityRegainHealthEvent.RegainReason.REGEN) {
                event.isCancelled = true
            }
        }
    }
}
