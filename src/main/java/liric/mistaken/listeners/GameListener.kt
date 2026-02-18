package liric.mistaken.listeners

import io.papermc.paper.event.player.PlayerPurchaseEvent
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
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
 * Optimizado para minimizar el impacto en el hilo principal durante el combate.
 */
class GameListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val victim = event.entity as? Player ?: return
        val damager = when (val attacker = event.damager) {
            is Player -> attacker
            is Projectile -> attacker.shooter as? Player
            else -> null
        } ?: return

        // 🧊 REGLA FREEZE: Jugadores congelados son invulnerables
        if (plugin.gameManager.combatManager.isFrozen(victim)) {
            event.isCancelled = true
            return
        }

        val damagerUUID = damager.uniqueId
        val victimUUID = victim.uniqueId

        // 1. EL ASESINO ATACA
        if (plugin.gameManager.esAsesino(damagerUUID)) {
            event.damage = 0.0 // Manejamos la salud por API propia
            plugin.gameManager.combatManager.takeDamage(victim)

            // Efectos visuales eficientes
            victim.world.spawnParticle(
                Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 15,
                0.1, 0.1, 0.1, Material.REDSTONE_BLOCK.createBlockData()
            )
            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.5f)
            return
        }

        // 2. EL SUPERVIVIENTE SE DEFIENDE
        if (plugin.gameManager.esAsesino(victimUUID)) {
            damager.sendActionBar(plugin.messageConfig.getMessage(damager, "game.killer-hit-actionbar",
                Placeholder.parsed("health", victim.health.toInt().toString())))

            // Aturdimiento (Stun) probabilístico optimizado
            if (ThreadLocalRandom.current().nextInt(100) < 15) {
                aplicarStunAlAsesino(victim, damager)
            }
            return
        }

        // 3. FUEGO AMIGO: Cancelado por defecto entre supervivientes
        event.isCancelled = true
    }

    private fun aplicarStunAlAsesino(killer: Player, damager: Player) {
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
        val player = event.entity as? Player ?: return
        if (plugin.gameManager.currentState != GameState.INGAME) return

        if (plugin.gameManager.combatManager.isFrozen(player)) {
            // Cancelar daños que no deberían afectar a un bloque de hielo
            if (event.cause == EntityDamageEvent.DamageCause.FREEZE ||
                event.cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val title = plain.serialize(event.view.title())
        val allowedTitles = listOf("Reparando", "Skill Check", "ENTES", "Tienda")

        if (allowedTitles.any { title.contains(it) }) return

        // Bloquear acceso a inventarios vanilla (Cofres, hornos, etc)
        val type = event.inventory.type
        if (type != InventoryType.PLAYER && type != InventoryType.CRAFTING) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onHungerChange(event: FoodLevelChangeEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) {
            event.isCancelled = true
            // La estamina se muestra en la barra de hambre, pero no dejamos que baje de verdad
            if (event.foodLevel < 20) (event.entity as Player).foodLevel = 20
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val victim = event.entity
        plugin.gameManager.handlePlayerDeath(victim)

        // Evitar que caigan items al suelo (Limpia el server)
        event.drops.clear()
        event.droppedExp = 0
    }

    // --- BLOQUEOS DE ACCIÓN ---

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) e.isCancelled = true
    }

    @EventHandler
    fun onCraft(e: CraftItemEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) e.isCancelled = true
    }

    @EventHandler
    fun onBreak(e: BlockBreakEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME && !e.player.hasPermission("mistaken.admin")) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onPlace(e: BlockPlaceEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME && !e.player.hasPermission("mistaken.admin")) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerDismount(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (event.isSneaking && player.passengers.isNotEmpty()) {
            plugin.gameManager.combatManager.soltarPasajero(player)
        }
    }

    @EventHandler
    fun onRegen(event: EntityRegainHealthEvent) {
        // Desactivar regeneración natural por comida o tiempo
        if (plugin.gameManager.currentState == GameState.INGAME) {
            val reason = event.regainReason
            if (reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
                reason == EntityRegainHealthEvent.RegainReason.REGEN) {
                event.isCancelled = true
            }
        }
    }
}
