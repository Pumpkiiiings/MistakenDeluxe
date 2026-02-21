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
 * GameListener: El árbitro supremo de la partida.
 *
 * MEJORAS:
 * - Insta-Respawn integrado (Cero pantalla roja).
 * - Daño Real (True Damage) de 1.5 corazones para el Asesino.
 * - Daño Real de 2.0 corazones para el Superviviente contra el Boss.
 * - Optimización masiva de eventos de inventario y hambre.
 */
class GameListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()

    /**
     * 🔥 MOTOR DE COMBATE: Maneja el daño real ignorando armaduras.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return

        val victim = event.entity as? Player ?: return
        val damager = when (val attacker = event.damager) {
            is Player -> attacker
            is Projectile -> attacker.shooter as? Player
            else -> null
        } ?: return

        // 🧊 REGLA FREEZE: Invulnerabilidad al estar congelado
        if (plugin.combatManager.isFrozen(victim)) {
            event.isCancelled = true
            return
        }

        val isDamagerKiller = plugin.gameManager.esAsesino(damager.uniqueId)
        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        // CASO A: EL ASESINO ATACA A UN SOBREVIVIENTE
        if (isDamagerKiller && !isVictimKiller) {
            event.damage = 0.0 // Cancelamos daño vanilla

            // Aplicamos DAÑO REAL (1.5 corazones = 3.0 HP)
            plugin.combatManager.processTrueDamage(victim, damager, 3.0)

            // Efectos visuales de impacto (Sangre)
            victim.world.spawnParticle(
                Particle.BLOCK,
                victim.location.add(0.0, 1.0, 0.0), 10,
                0.1, 0.1, 0.1,
                Material.REDSTONE_BLOCK.createBlockData()
            )
            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.5f)
            return
        }

        // CASO B: EL SOBREVIVIENTE SE DEFIENDE (Ataca al Jefe)
        if (!isDamagerKiller && isVictimKiller) {
            event.damage = 0.0 // Cancelamos daño vanilla para usar daño fijo

            // Mostramos la salud real del Boss (0-200)
            val killerHealth = victim.health.toInt()
            damager.sendActionBar(plugin.messageConfig.getMessage(damager, "game.killer-hit-actionbar",
                Placeholder.parsed("health", killerHealth.toString())))

            // Probabilidad de Stun al Jefe
            if (ThreadLocalRandom.current().nextInt(100) < 15) {
                aplicarStunAlAsesino(victim, damager)
            }

            // Aplicamos DAÑO REAL al Jefe (2.0 corazones = 4.0 HP)
            plugin.combatManager.processTrueDamage(victim, damager, 4.0)
            return
        }

        // CASO C: FUEGO AMIGO
        event.isCancelled = true
    }

    /**
     * 🔥 INSTA-RESPAWN: Elimina la pantalla de muerte y acelera el juego.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val victim = event.entity

        // 1. Rendimiento: Limpiar basura del suelo y ocultar mensaje vanilla
        event.drops.clear()
        event.droppedExp = 0
        event.deathMessage(null)

        // 2. Lógica de juego: Cambiar a espectador, avisar a la arena, etc.
        plugin.gameManager.handlePlayerDeath(victim)

        // 3. LA LLAVE DEL INSTA-RESPAWN:
        // Forzamos al cliente a reaparecer en el mismo tick.
        victim.spigot().respawn()
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

        // Bloquear daños ambientales si está congelado (Freeze Tag)
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

        val type = event.inventory.type
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) return

        // Filtrar por títulos de nuestras GUIs de TriumphGUI
        val title = plain.serialize(event.view.title())
        val allowedKeywords = listOf("Reparando", "Skill Check", "ENTES", "Tienda", "Selecciona")

        if (allowedKeywords.any { title.contains(it) }) return

        // Bloquear cofres vanilla y otros bloques con inventario
        event.isCancelled = true
    }

    @EventHandler
    fun onHungerChange(event: FoodLevelChangeEvent) {
        if (plugin.gameManager.currentState == GameState.INGAME) {
            event.isCancelled = true
            // Mantener barra visual llena si no hay estamina usándola
            if (event.foodLevel < 20) (event.entity as Player).foodLevel = 20
        }
    }

    // --- BLOQUEOS DE ACCIÓN (RENDIMIENTO Y REGLAS) ---

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
        // Desactivar curación por comida (Satiated)
        if (plugin.gameManager.currentState == GameState.INGAME) {
            val reason = event.regainReason
            if (reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
                reason == EntityRegainHealthEvent.RegainReason.REGEN) {
                event.isCancelled = true
            }
        }
    }
}
