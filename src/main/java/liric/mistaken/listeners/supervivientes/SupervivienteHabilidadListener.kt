package liric.mistaken.listeners.supervivientes

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.attribute.Attribute
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteHabilidadListener: Gestión de habilidades de humanos y proyectiles.
 * Optimización: Filtrado de movimiento por bloque y cortocircuitos lógicos en proyectiles.
 */
class SupervivienteHabilidadListener(private val plugin: Mistaken) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseSurvivorAbility(event: PlayerInteractEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val player = event.player
        val action = event.action
        val clase = plugin.supervivienteManager.getClase(player) ?: return

        // Validación rápida de ítem en mano
        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) return

        val slot = player.inventory.heldItemSlot

        when (action) {
            // --- CLIC DERECHO: HABILIDADES ACTIVAS ---
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                if (slot in 0..2) {
                    event.isCancelled = true
                    clase.usarHabilidad(player, slot)
                }
            }
            // --- CLIC IZQUIERDO: RASTREO / ESPECIALES ---
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                if (slot == 1) {
                    event.isCancelled = true
                    clase.trackearHeridos(player)
                }
            }
            else -> return
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        val victim = event.hitEntity as? Player ?: return

        // 1. Lógica de la Roca (Civil)
        if (snowball.hasMetadata("mistaken_roca")) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 1))
            victim.playSound(victim.location, Sound.BLOCK_STONE_BREAK, 1f, 0.8f)

            (snowball.shooter as? Player)?.let { shooter ->
                shooter.sendMessage(plugin.messageConfig.getMessage(shooter, "habilidades.roca-impacto-exito"))
            }
            return
        }

        // 2. Lógica del Pedido (Repartidor)
        if (snowball.hasMetadata("mistaken_pedido")) {
            val isKiller = plugin.gameManager.esAsesino(victim.uniqueId)

            if (isKiller) {
                // Efecto negativo al asesino
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.sendMessage(plugin.messageConfig.getMessage(victim, "habilidades.pedido-impacto-asesino"))
                victim.playSound(victim.location, Sound.ENTITY_GENERIC_SPLASH, 1f, 1f)
            } else {
                // Curación al compañero (Sync con Attributes de 1.21.4)
                val maxHealth = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                victim.health = (victim.health + 4.0).coerceAtMost(maxHealth)

                victim.sendMessage(plugin.messageConfig.getMessage(victim, "habilidades.pedido-recibido-cura"))
                victim.playSound(victim.location, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
            }
        }
    }

    /**
     * Optimización de rendimiento: Solo procesa si el jugador cambió de bloque.
     * Con 2 jugadores, el impacto es nulo.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onSalsaMove(event: PlayerMoveEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val from = event.from
        val to = event.to ?: return

        // Si no cambió de bloque físico, salimos (Ahorra 90% de ciclos de este evento)
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        val targetBlock = to.block
        if (targetBlock.hasMetadata("mistaken_derrame")) {
            event.player.apply {
                addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2))
                playSound(location, Sound.BLOCK_SLIME_BLOCK_STEP, 0.5f, 0.5f)
            }
        }
    }
}
