package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
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
 * Optimizado para Paper 1.21.4 y sincronizado con el sistema de salud Boss/Survivor.
 */
class GameListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return

        val victim = event.entity as? Player ?: return
        val damager = when (val attacker = event.damager) {
            is Player -> attacker
            is Projectile -> attacker.shooter as? Player
            else -> null
        } ?: return

        // 🧊 REGLA FREEZE
        if (plugin.combatManager.isFrozen(victim)) {
            event.isCancelled = true
            return
        }

        val damagerUUID = damager.uniqueId
        val victimUUID = victim.uniqueId

        // A. EL ASESINO ATACA A UN SOBREVIVIENTE
        if (plugin.gameManager.esAsesino(damagerUUID) && !plugin.gameManager.esAsesino(victimUUID)) {
            event.damage = 0.0 // Cancelamos Minecraft
            // 🔥 LLAMADA CORREGIDA: Usamos processTrueDamage
            plugin.combatManager.processTrueDamage(victim, damager, 6.0)

            // Visuales de sangre
            victim.world.spawnParticle(Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10, 0.1, 0.1, 0.1, Material.REDSTONE_BLOCK.createBlockData())
            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.5f)
            return
        }

        // B. EL SOBREVIVIENTE SE DEFIENDE (Golpea al Asesino)
        if (plugin.gameManager.esAsesino(victimUUID)) {
            // Mostramos la salud real (0-200)
            val killerHealth = victim.health.toInt()
            damager.sendActionBar(plugin.messageConfig.getMessage(damager, "game.killer-hit-actionbar",
                Placeholder.parsed("health", killerHealth.toString())))

            if (ThreadLocalRandom.current().nextInt(100) < 15) {
                aplicarStunAlAsesino(victim, damager)
            }

            event.damage = 0.0
            // 🔥 LLAMADA CORREGIDA: El sobreviviente quita 4 de vida al jefe
            plugin.combatManager.processTrueDamage(victim, damager, 4.0)
            return
        }

        event.isCancelled = true // Fuego amigo
    }

    private fun aplicarStunAlAsesino(killer: Player, damager: Player) {
        killer.removePotionEffect(PotionEffectType.SPEED)
        killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
        killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3))

        killer.playSound(killer.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 0.5f)
        killer.world.spawnParticle(Particle.ENCHANTED_HIT, killer.location.add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)

        killer.sendMessage(plugin.messageConfig.getMessage(killer, "game.killer-stunned-victim"))
        damager.sendMessage(plugin.messageConfig.getMessage(damager, "game.killer-stunned-damager"))
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return
        val player = event.entity as? Player ?: return

        if (plugin.combatManager.isFrozen(player)) {
            if (event.cause == EntityDamageEvent.DamageCause.FREEZE ||
                event.cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        // Check de tipo rápido (Ahorra CPU)
        val type = event.inventory.type
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) return

        // Verificación de títulos para nuestras GUIs custom
        val title = plain.serialize(event.view.title())
        val allowedKeywords = listOf("Reparando", "Skill Check", "ENTES", "Tienda", "Selecciona")

        if (allowedKeywords.any { title.contains(it) }) return

        // Bloquear cofres del mapa, hornos, etc.
        event.isCancelled = true
    }

    @EventHandler
    fun onHungerChange(event: FoodLevelChangeEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) {
            event.isCancelled = true
            // Mantener barra de comida llena si no se está usando para estamina
            if (event.foodLevel < 20) (event.entity as Player).foodLevel = 20
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        // Limpieza de drops para rendimiento
        event.drops.clear()
        event.droppedExp = 0

        // El plugin maneja el cambio a espectador y fin de juego
        plugin.gameManager.handlePlayerDeath(event.entity)
    }

    // --- BLOQUEOS DE ACCIÓN ---

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
        val player = event.player
        if (event.isSneaking && player.passengers.isNotEmpty()) {
            plugin.combatManager.soltarPasajero(player)
        }
    }

    @EventHandler
    fun onRegen(event: EntityRegainHealthEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) {
            val reason = event.regainReason
            if (reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
                reason == EntityRegainHealthEvent.RegainReason.REGEN) {
                event.isCancelled = true
            }
        }
    }
}
