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
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap

/**
 *[LIRIC-MISTAKEN 2.0]
 * SupervivienteHabilidadListener: Gestión de habilidades ultra-optimizada.
 *
 * FIXES:
 * - Prevención de doble disparo (MainHand Check).
 * - Cero Garbage Collection en PlayerMoveEvent usando Keys de String.
 */
class SupervivienteHabilidadListener(private val plugin: Mistaken) : Listener {

    // --- OPTIMIZACIÓN EXTREMA: Registro de bloques en RAM ---
    // Usar Strings (Mundo_X_Y_Z) consume mucha menos RAM que guardar objetos Location enteros.
    companion object {
        val bloquesDerrame = ConcurrentHashMap.newKeySet<String>()

        private val ROCA_KEY = NamespacedKey("mistaken", "roca")
        private val PEDIDO_KEY = NamespacedKey("mistaken", "pedido")

        fun marcarBloque(loc: org.bukkit.Location) {
            val key = "${loc.world.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"
            bloquesDerrame.add(key)
        }

        fun desmarcarBloque(loc: org.bukkit.Location) {
            val key = "${loc.world.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"
            bloquesDerrame.remove(key)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseSurvivorAbility(event: PlayerInteractEvent) {
        // 1. 🔥 FIX: Evitar doble ejecución por las manos
        if (event.hand != EquipmentSlot.HAND) return

        if (plugin.gameManager.currentState != GameState.INGAME) return

        val player = event.player
        val action = event.action
        val slot = player.inventory.heldItemSlot

        // Solo permitimos uso de habilidades en los slots 0, 1 y 2
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

        // 1. Roca (Civil)
        if (pdc.has(ROCA_KEY, PersistentDataType.BYTE)) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 1))
            victim.playSound(victim.location, Sound.BLOCK_STONE_BREAK, 1f, 0.8f)

            (snowball.shooter as? Player)?.let { shooter ->
                shooter.sendMessage(plugin.messageConfig.getMessage(shooter, "habilidades.roca-impacto-exito"))
            }
            return
        }

        // 2. Pedido (Repartidor)
        if (pdc.has(PEDIDO_KEY, PersistentDataType.BYTE)) {
            val isKiller = plugin.gameManager.esAsesino(victim.uniqueId)

            if (isKiller) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.sendMessage(plugin.messageConfig.getMessage(victim, "habilidades.pedido-impacto-asesino"))
                victim.playSound(victim.location, Sound.ENTITY_GENERIC_SPLASH, 1f, 1f)
            } else {
                val maxHealth = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                victim.health = (victim.health + 4.0).coerceAtMost(maxHealth)
                victim.sendMessage(plugin.messageConfig.getMessage(victim, "habilidades.pedido-recibido-cura"))
                victim.playSound(victim.location, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
            }
        }
    }

    /**
     * 🔥 Cero Garbage Collection
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onSalsaMove(event: PlayerMoveEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val to = event.to ?: return
        val from = event.from

        // 1. Filtro: Solo nos importa si el jugador cambia de bloque entero
        if (from.blockX == to.blockX && from.blockZ == to.blockZ && from.blockY == to.blockY) return

        // 2. Buscamos en el HashSet armando el String (Súper ligero)
        val key = "${to.world.name}_${to.blockX}_${to.blockY}_${to.blockZ}"

        if (bloquesDerrame.contains(key)) {
            val player = event.player
            if (!player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2))
                player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_STEP, 0.5f, 0.5f)
            }
        }
    }
}
