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
 * FIX: Daño directo manipulando la salud para evitar StackOverflowErrors.
 */
class GameListener(private val plugin: Mistaken) : Listener {

    private val mm = plugin.mm
    private val plain = PlainTextComponentSerializer.plainText()

    /**
     * 🧊 SISTEMA DE RESCATE (Freeze Tag)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRescue(event: PlayerInteractEntityEvent) {
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return
        if (plugin.gameManager.currentMode != MistakenMode.FREEZE_TAG) return

        val victim = event.rightClicked as? Player ?: return
        val rescuer = event.player

        if (plugin.combatManager.isFrozen(victim)) {
            if (!plugin.gameManager.esAsesino(rescuer.uniqueId)) {
                if (plugin.combatManager.getHealth(rescuer) <= 1) {
                    rescuer.sendActionBar(mm.deserialize("<red>¡Estás muy herido para rescatar a nadie!"))
                    return
                }

                event.isCancelled = true
                plugin.combatManager.unfreeze(victim, rescuer)
                victim.world.spawnParticle(Particle.SNOWFLAKE, victim.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
                victim.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 1.5f)
            }
        }
    }

    /**
     * 🔥 MOTOR DE COMBATE: True Damage Nativo.
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

        // 🧊 REGLA FREEZE
        if (plugin.combatManager.isFrozen(victim)) {
            event.isCancelled = true
            return
        }

        val isDamagerKiller = plugin.gameManager.esAsesino(damager.uniqueId)
        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        // --- CASO A: EL ASESINO REPARTE LEÑA ---
        if (isDamagerKiller && !isVictimKiller) {
            event.damage = 0.0
            plugin.combatManager.takeDamage(victim)

            victim.world.spawnParticle(
                Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10,
                0.1, 0.1, 0.1, Material.REDSTONE_BLOCK.createBlockData()
            )
            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.5f)
            return
        }

        // --- CASO B: EL SOBREVIVIENTE SE DEFIENDE (Ataca al Boss) ---
        if (!isDamagerKiller && isVictimKiller) {

            // 1. Cancelamos el daño de Minecraft (evita que la armadura de Netherite lo proteja)
            event.isCancelled = true

            // 2. Calculamos el daño real (4.0 = 2 corazones)
            val damageToBoss = plugin.config.getDouble("gameplay.killer.damage", 4.0)

            // 3. Modificamos la salud directo (No dispara eventos de Bukkit = 0 loops)
            val nuevaSalud = (victim.health - damageToBoss).coerceAtLeast(0.0)
            victim.health = nuevaSalud

            // 4. Simulamos el empuje y el color rojo de daño (Nativo de Paper)
            victim.playHurtAnimation(damager.location.yaw)
            victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)

            // 5. Feedback visual de la vida restante del Boss
            val killerHealth = nuevaSalud.toInt()
            damager.sendActionBar(plugin.messageConfig.getMessage(damager, "game.killer-hit-actionbar",
                Placeholder.parsed("health", killerHealth.toString())))

            // Probabilidad de Stun (15%)
            if (ThreadLocalRandom.current().nextInt(100) < 15) {
                aplicarStunAlAsesino(victim, damager)
            }

            // Si el Boss muere
// Si el Boss muere
            if (nuevaSalud <= 0.0) {
                // 🔥 LLAMAMOS DIRECTO A TU MANAGER
                // Esto lo pasa a espectador, da los premios y termina la partida sin bugear a Bukkit
                plugin.gameManager.handlePlayerDeath(victim)
            }
            return
        }

        // --- CASO C: FUEGO AMIGO ---
        event.isCancelled = true
    }

    /**
     * 🔥 INSTA-RESPAWN
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return

        val victim = event.entity
        event.drops.clear()
        event.droppedExp = 0
        event.deathMessage(null)

        plugin.gameManager.handlePlayerDeath(victim)

        plugin.server.scheduler.runTask(plugin, Runnable {
            if (victim.isOnline && !victim.isDead) {
                // Si el jugador sigue vivo (porque lo "matamos" manualmente arriba)
                // no hace falta respawnearlo, el GameManager lo pasa a espectador.
            } else if (victim.isOnline) {
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
