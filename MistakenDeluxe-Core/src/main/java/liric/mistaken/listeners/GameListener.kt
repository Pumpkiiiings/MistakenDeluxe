package liric.mistaken.listeners

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer

/**
 * [LIRIC-MISTAKEN 2.0]
 * GameListener: Adaptado para MULTIARENA / VELOCITY.
 * Gestiona la lÃ³gica de juego basÃ¡ndose en la sesiÃ³n individual de cada jugador.
 */
class GameListener(private val plugin: Mistaken) : Listener {

    private val mm = plugin.mm
    private val plain = PlainTextComponentSerializer.plainText()
    private val stunSoundsQueue = ConcurrentHashMap<UUID, MutableList<Int>>()
    private val infectionDeathLocs = ConcurrentHashMap<UUID, org.bukkit.Location>()

    /**
     * ðŸ§Š SISTEMA DE RESCATE (Freeze Tag)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRescue(event: PlayerInteractEntityEvent) {
        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return // ðŸ”¥ MULTIARENA

        if (!plugin.isReady || session.currentState != GameState.INGAME) return
        if (session.currentMode != MistakenMode.FREEZE_TAG) return

        val victim = event.rightClicked as? Player ?: return

        if (plugin.combatManager.isFrozen(victim)) {
            if (!session.esAsesino(player.uniqueId)) {
                if (plugin.combatManager.getHealth(player) <= 1) {
                    player.sendActionBar(mm.deserialize("<red>Â¡EstÃ¡s muy herido para rescatar a nadie!"))
                    return
                }

                event.isCancelled = true
                plugin.combatManager.unfreeze(victim, player)
                victim.world.spawnParticle(Particle.SNOWFLAKE, victim.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
                victim.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 1.5f)
            }
        }
    }

    /**
     * ðŸ”¥ EFECTOS VISUALES Y STUN
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamageEffects(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val session = plugin.sessionManager.getSession(victim) ?: return // ðŸ”¥ MULTIARENA

        if (!plugin.isReady || session.currentState != GameState.INGAME) return

        val damager = when (val attacker = event.damager) {
            is Player -> attacker
            is Projectile -> attacker.shooter as? Player
            else -> null
        } ?: return

        val isDamagerKiller = session.esAsesino(damager.uniqueId)
        val isVictimKiller = session.esAsesino(victim.uniqueId)

        if (!isDamagerKiller && isVictimKiller) {
            val killerHealth = plugin.combatManager.getHealth(victim)
            damager.sendActionBar(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(damager, "game.killer-hit-actionbar",
                Placeholder.parsed("health", killerHealth.toString())))

            if (ThreadLocalRandom.current().nextInt(100) < 15) {
                aplicarStunAlAsesino(victim, damager)
            }
        }
    }

    /**
     * ðŸ”¥ MUERTE LÃ“GICA POR ARENA
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val session = plugin.sessionManager.getSession(victim) ?: return // ðŸ”¥ MULTIARENA

        if (!plugin.isReady || session.currentState != GameState.INGAME) return

        val deathLoc = victim.location.clone()
        event.drops.clear()
        event.droppedExp = 0
        event.deathMessage(null)

        // ðŸ”¥ FIX INFECCIÃ“N: Guardamos la ubicaciÃ³n ANTES de handlePlayerDeath
        // para que onRespawn siempre tenga la loc disponible
        if (session.currentMode == MistakenMode.INFECTION) {
            infectionDeathLocs[victim.uniqueId] = deathLoc
        }

        // Procesar muerte en su controlador de sesiÃ³n
        session.playerController.handlePlayerDeath(victim)

        victim.scheduler.runDelayed(plugin, Consumer { _ ->
            if (victim.isOnline && victim.isDead) {
                victim.spigot().respawn()
                victim.scheduler.runDelayed(plugin, Consumer { _ ->
                    if (session.currentState == GameState.INGAME) {
                        // ðŸ”¥ FIX: En infecciÃ³n el jugador se convierte en asesino, nunca espectador
                        if (!session.esAsesino(victim.uniqueId)) {
                            plugin.spectatorManager.setCustomSpectator(victim)
                        }
                    }
                }, null, 2L)
            }
        }, null, 1L)
    }

    // ðŸ”¥ FIX INFECCIÃ“N: HIGHEST para que nuestro respawnLocation no sea sobreescrito por otros plugins/sistemas
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRespawn(event: org.bukkit.event.player.PlayerRespawnEvent) {
        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return

        if (session.currentState == GameState.INGAME && session.currentMode == MistakenMode.INFECTION) {
            val loc = infectionDeathLocs.remove(player.uniqueId)
            if (loc != null) {
                event.respawnLocation = loc
            }
        }
    }

    private fun aplicarStunAlAsesino(killer: Player, damager: Player) {
        killer.removePotionEffect(PotionEffectType.SPEED)
        killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
        killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3))

        killer.playSound(killer.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 0.5f)
        killer.world.spawnParticle(Particle.ENCHANTED_HIT, killer.location.add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)

        killer.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(killer, "game.killer-stunned-victim"))
        damager.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(damager, "game.killer-stunned-damager"))

        val claseAsesino = plugin.playerDataManager.getSelectedKiller(killer.uniqueId)
        if (claseAsesino == "slasher") {
            val uuid = killer.uniqueId
            val queue = stunSoundsQueue.getOrPut(uuid) { mutableListOf(1, 2).apply { shuffle() } }
            if (queue.isEmpty()) { queue.addAll(listOf(1, 2)); queue.shuffle() }

            val soundIndex = queue.removeAt(0)
            killer.world.playSound(killer.location, "mistaken:whitepumpkin_stun_$soundIndex", SoundCategory.PLAYERS, 3.0f, 1.0f)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEnvironmentalDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val session = plugin.sessionManager.getSession(player) ?: return

        if (!plugin.isReady || session.currentState != GameState.INGAME) return

        if (plugin.combatManager.isFrozen(player)) {
            if (event.cause in listOf(EntityDamageEvent.DamageCause.FREEZE, EntityDamageEvent.DamageCause.SUFFOCATION, EntityDamageEvent.DamageCause.DROWNING)) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val session = plugin.sessionManager.getSession(player) ?: return

        if (!plugin.isReady || session.currentState != GameState.INGAME) return

        val type = event.inventory.type
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) return

        val title = plain.serialize(event.view.title())
        val allowed = listOf("Reparando", "Skill Check", "ENTES", "Tienda", "Selecciona", "Espectear")
        if (allowed.any { title.contains(it, ignoreCase = true) }) return

        event.isCancelled = true
    }

    @EventHandler
    fun onHungerChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        val session = plugin.sessionManager.getSession(player) ?: return

        if (plugin.isReady && session.currentState == GameState.INGAME) {
            event.isCancelled = true
            if (player.foodLevel < 20) player.foodLevel = 20
        }
    }

    // --- PROTECCIONES AISLADAS POR SESIÃ“N ---
    @EventHandler fun onDrop(e: PlayerDropItemEvent) {
        val session = plugin.sessionManager.getSession(e.player)
        if (session?.currentState == GameState.INGAME) e.isCancelled = true
    }

    @EventHandler fun onCraft(e: CraftItemEvent) {
        val session = plugin.sessionManager.getSession(e.whoClicked as Player)
        if (session?.currentState == GameState.INGAME) e.isCancelled = true
    }

    @EventHandler fun onBreak(e: BlockBreakEvent) {
        val session = plugin.sessionManager.getSession(e.player)
        if (session?.currentState == GameState.INGAME && !e.player.hasPermission("mistaken.admin")) e.isCancelled = true
    }

    @EventHandler fun onPlace(e: BlockPlaceEvent) {
        val session = plugin.sessionManager.getSession(e.player)
        if (session?.currentState == GameState.INGAME && !e.player.hasPermission("mistaken.admin")) e.isCancelled = true
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
        val player = event.entity as? Player ?: return
        val session = plugin.sessionManager.getSession(player)

        if (session?.currentState == GameState.INGAME) {
            val r = event.regainReason
            if (r == EntityRegainHealthEvent.RegainReason.SATIATED || r == EntityRegainHealthEvent.RegainReason.REGEN) {
                event.isCancelled = true
            }
        }
    }
}

