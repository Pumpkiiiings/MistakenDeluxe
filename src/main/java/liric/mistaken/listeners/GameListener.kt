package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
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
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0]
 * GameListener: El árbitro supremo y controlador de mecánicas.
 *
 * MEJORAS:
 * - Sistema de Rescate (Freeze Tag) por Clic Derecho.
 * - Insta-Respawn (Cero pantalla de muerte).
 * - True Damage integrado.
 * - Filtros O(1) para inventarios y acciones.
 */
class GameListener(private val plugin: Mistaken) : Listener {

    private val mm = plugin.mm
    private val plain = PlainTextComponentSerializer.plainText()

    /**
     * 🧊 SISTEMA DE RESCATE (Freeze Tag):
     * Detecta cuando un superviviente le da clic derecho a un compa congelado.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRescue(event: PlayerInteractEntityEvent) {
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return
        if (plugin.gameManager.currentMode != MistakenMode.FREEZE_TAG) return

        val victim = event.rightClicked as? Player ?: return
        val rescuer = event.player

        // ¿La víctima está hecha paleta de hielo?
        if (plugin.combatManager.isFrozen(victim)) {

            // Solo los humanos rescatan, el asesino no ayuda
            if (!plugin.gameManager.esAsesino(rescuer.uniqueId)) {

                // Si el rescatista está a 1 vida, no tiene fuerza para ayudar
                if (plugin.combatManager.getHealth(rescuer) <= 1) {
                    rescuer.sendActionBar(mm.deserialize("<red>¡Estás muy herido para rescatar a nadie!"))
                    return
                }

                event.isCancelled = true // Evitar abrir inventarios

                // 🔥 ¡LIBERTAD! 🔥
                plugin.combatManager.unfreeze(victim, rescuer)

                // Efectos visuales de rescate
                victim.world.spawnParticle(Particle.SNOWFLAKE, victim.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
                victim.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 1.5f)
            }
        }
    }

    /**
     * 🔥 MOTOR DE COMBATE: Maneja el daño real (True Damage).
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

        // 🧊 REGLA FREEZE: Invulnerabilidad al estar tieso
        if (plugin.combatManager.isFrozen(victim)) {
            event.isCancelled = true
            return
        }

        val isDamagerKiller = plugin.gameManager.esAsesino(damager.uniqueId)
        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        // --- CASO A: EL ASESINO REPARTE LEÑA ---
        if (isDamagerKiller && !isVictimKiller) {
            event.damage = 0.0

            // Aplicamos daño de sistema (Vidas)
            plugin.combatManager.takeDamage(victim)

            // Sangre (Partículas de bloque)
            victim.world.spawnParticle(
                Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10,
                0.1, 0.1, 0.1, Material.REDSTONE_BLOCK.createBlockData()
            )
            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.5f)
            return
        }

        // --- CASO B: EL SOBREVIVIENTE SE REBELA (Ataca al Boss) ---
        if (!isDamagerKiller && isVictimKiller) {
            event.damage = 0.0

            // Feedback de vida del Boss
            damager.sendActionBar(plugin.messageConfig.getMessage(damager, "game.killer-hit-actionbar",
                Placeholder.parsed("health", victim.health.toInt().toString())))

            // Probabilidad de Stun (15%)
            if (ThreadLocalRandom.current().nextInt(100) < 15) {
                aplicarStunAlAsesino(victim, damager)
            }

            // Aplicamos DAÑO REAL al Jefe (usando el método del Main o CombatManager)
            // Si tu CombatManager tiene el método processTrueDamage úsalo, sino victim.damage(4.0)
            victim.damage(4.0, damager)
            return
        }

        // --- CASO C: FUEGO AMIGO ---
        event.isCancelled = true
    }

    /**
     * 🔥 INSTA-RESPAWN: No más pantalla de muerte para no perder tiempo.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return

        val victim = event.entity
        event.drops.clear()
        event.droppedExp = 0
        event.deathMessage(null)

        plugin.gameManager.handlePlayerDeath(victim)

        // 🔥 RESPRAWN PRO: Esperamos 1 tick para que no haya bugs visuales
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (victim.isOnline) {
                victim.spigot().respawn()
            }
        })
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
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return
        val player = event.entity as? Player ?: return

        if (plugin.combatManager.isFrozen(player)) {
            // No morir por frío o asfixia en el bloque de hielo
            if (event.cause == EntityDamageEvent.DamageCause.FREEZE ||
                event.cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
                event.cause == EntityDamageEvent.DamageCause.DROWNING) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return

        val type = event.inventory.type
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) return

        val title = plain.serialize(event.view.title())
        val allowed = listOf("Reparando", "Skill Check", "ENTES", "Tienda", "Selecciona")

        if (allowed.any { title.contains(it, ignoreCase = true) }) return
        event.isCancelled = true
    }

    @EventHandler
    fun onHungerChange(event: FoodLevelChangeEvent) {
        if (plugin.isReady && plugin.gameManager.currentState == GameState.INGAME) {
            event.isCancelled = true
            (event.entity as? Player)?.let { if (it.foodLevel < 20) it.foodLevel = 20 }
        }
    }

    // --- BLOQUEOS RÁPIDOS ---
    @EventHandler fun onDrop(e: PlayerDropItemEvent) { if (plugin.gameManager.currentState == GameState.INGAME) e.isCancelled = true }
    @EventHandler fun onCraft(e: CraftItemEvent) { if (plugin.gameManager.currentState == GameState.INGAME) e.isCancelled = true }
    @EventHandler fun onBreak(e: BlockBreakEvent) { if (plugin.gameManager.currentState == GameState.INGAME && !e.player.hasPermission("mistaken.admin")) e.isCancelled = true }
    @EventHandler fun onPlace(e: BlockPlaceEvent) { if (plugin.gameManager.currentState == GameState.INGAME && !e.player.hasPermission("mistaken.admin")) e.isCancelled = true }

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
            val r = event.regainReason
            if (r == EntityRegainHealthEvent.RegainReason.SATIATED || r == EntityRegainHealthEvent.RegainReason.REGEN) {
                event.isCancelled = true
            }
        }
    }
}
