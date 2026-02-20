package liric.mistaken.listeners.supervivientes

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.NamespacedKey
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
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteHabilidadListener: Gestión de habilidades ultra-optimizada.
 *
 * FIXES DE RENDIMIENTO:
 * - Eliminado Block Metadata (reemplazado por HashSet en memoria).
 * - Eliminado Entity Metadata (reemplazado por PDC API).
 * - Culling de eventos de movimiento.
 */
class SupervivienteHabilidadListener(private val plugin: Mistaken) : Listener {

    // --- OPTIMIZACIÓN SÉNIOR: Registro de bloques en RAM ---
    // Usamos esto en lugar de block.hasMetadata(). ¡100 veces más rápido!
    companion object {
        val bloquesDerrame = ConcurrentHashMap.newKeySet<org.bukkit.Location>()

        // Llaves para proyectiles (Paper PDC)
        private val ROCA_KEY = NamespacedKey("mistaken", "roca")
        private val PEDIDO_KEY = NamespacedKey("mistaken", "pedido")

        // Métodos de utilidad para que las clases Civil/Repartidor usen
        fun marcarBloque(loc: org.bukkit.Location) = bloquesDerrame.add(loc.block.location)
        fun desmarcarBloque(loc: org.bukkit.Location) = bloquesDerrame.remove(loc.block.location)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseSurvivorAbility(event: PlayerInteractEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val player = event.player
        val action = event.action

        // Slot check rápido antes de buscar la clase
        val slot = player.inventory.heldItemSlot
        if (slot > 2) return

        val clase = plugin.supervivienteManager.getClase(player) ?: return
        if (player.inventory.itemInMainHand.type.isAir) return

        when (action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                event.isCancelled = true
                clase.usarHabilidad(player, slot)
            }
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                if (slot == 1) {
                    event.isCancelled = true
                    clase.trackearHeridos(player)
                }
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        val victim = event.hitEntity as? Player ?: return

        val pdc = snowball.persistentDataContainer

        // 1. Lógica de la Roca (Civil) - Usando PDC
        if (pdc.has(ROCA_KEY, PersistentDataType.BYTE)) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 1))
            victim.playSound(victim.location, Sound.BLOCK_STONE_BREAK, 1f, 0.8f)

            (snowball.shooter as? Player)?.let { shooter ->
                shooter.sendMessage(plugin.messageConfig.getMessage(shooter, "habilidades.roca-impacto-exito"))
            }
            return
        }

        // 2. Lógica del Pedido (Repartidor) - Usando PDC
        if (pdc.has(PEDIDO_KEY, PersistentDataType.BYTE)) {
            val isKiller = plugin.gameManager.esAsesino(victim.uniqueId)

            if (isKiller) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.sendMessage(plugin.messageConfig.getMessage(victim, "habilidades.pedido-impacto-asesino"))
                victim.playSound(victim.location, Sound.ENTITY_GENERIC_SPLASH, 1f, 1f)
            } else {
                // Curación con API de Atributos 1.21.4
                val maxHealth = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                victim.health = (victim.health + 4.0).coerceAtMost(maxHealth)

                victim.sendMessage(plugin.messageConfig.getMessage(victim, "habilidades.pedido-recibido-cura"))
                victim.playSound(victim.location, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
            }
        }
    }

    /**
     * 🔥 FIX AL 0.03% DE SPARK:
     * Eliminamos 'hasMetadata'. Ahora buscamos la Location en un HashSet.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onSalsaMove(event: PlayerMoveEvent) {
        // Culling rápido por estado de juego
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val from = event.from
        val to = event.to ?: return

        // 1. Filtro de bloque: Si solo movió la cabeza, ignoramos.
        if (from.blockX == to.blockX && from.blockZ == to.blockZ && from.blockY == to.blockY) return

        // 2. Búsqueda instantánea en el Set de coordenadas
        // block.location crea un objeto nuevo, pero comparado con Metadata es insignificante.
        if (bloquesDerrame.contains(to.block.location)) {
            event.player.apply {
                // Solo aplicamos si no lo tiene para no resetear el efecto cada tick
                if (!hasPotionEffect(PotionEffectType.SLOWNESS)) {
                    addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2))
                    playSound(location, Sound.BLOCK_SLIME_BLOCK_STEP, 0.5f, 0.5f)
                }
            }
        }
    }
}
