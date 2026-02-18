package liric.mistaken.listeners.asesinos

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoHabilidadListener: Gestión de disparadores y proyectiles especiales.
 * Optimización: Cortocircuitos lógicos y uso nativo de Adventure Component para 1.21.4.
 */
class AsesinoHabilidadListener(private val plugin: Mistaken) : Listener {

    private val mm = plugin.mm
    private val plain = PlainTextComponentSerializer.plainText()

    /**
     * Trigger: Activación de habilidades activas (Click Derecho).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseAbility(event: PlayerInteractEvent) {
        // 1. Filtro de Estado (Acceso a variable en RAM)
        if (plugin.gameManager.currentState != GameState.INGAME) return

        // 2. Filtro de Acción (isRightClick es un helper de Paper)
        if (!event.action.isRightClick) return

        val player = event.player
        val slot = player.inventory.heldItemSlot

        // 3. Filtro de Slot (Solo slots del 1 al 4 disparan habilidades)
        if (slot !in 1..4) return

        // 4. Búsqueda de Clase (Operación O(1))
        val asesino = plugin.asesinoManager.getAsesinoDelJugador(player) ?: return

        // 5. Validación de Item
        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) return

        // Ejecución
        event.isCancelled = true
        asesino.usarHabilidad(player, slot)
    }

    /**
     * Lógica de impacto: Habilidades basadas en proyectiles (Entity 303 / Infección).
     * Optimizado para Paper 1.21.4 con soporte para Display Names como Componentes.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return

        // Obtenemos el nombre del proyectil de forma eficiente (Component -> String simple)
        val nameComp = snowball.customName() ?: return
        val rawName = plain.serialize(nameComp)

        if (rawName == "303_infection") {
            val loc = snowball.location
            val world = loc.world ?: return

            // --- 1. EFECTOS VISUALES (Cero recursos en hilos de red) ---
            world.spawnParticle(Particle.ENCHANTED_HIT, loc, 15, 0.3, 0.3, 0.3, 0.1)

            // Fix de partículas 1.21.4 (DustOptions optimizado)
            val dust = Particle.DustOptions(Color.fromRGB(0, 255, 240), 1.0f)
            world.spawnParticle(Particle.DUST, loc, 10, 0.2, 0.2, 0.2, 0.1, dust)

            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f)
            world.playSound(loc, Sound.ENTITY_ITEM_BREAK, 0.8f, 0.1f)

            // --- 2. LÓGICA DE IMPACTO EN SUPERVIVIENTE ---
            val victim = event.hitEntity as? Player ?: return

            // Evitar fuego amigo entre asesinos (si hubiera más de uno)
            if (plugin.asesinoManager.esElAsesino(victim)) return

            // Aplicar efectos de Hacker (Costo: 0.001ms)
            victim.apply {
                addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
                addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))

                // Usamos el CombatManager centralizado para reducir la vida del humano
                plugin.gameManager.combatManager.takeDamage(this)

                // Partículas estéticas en víctima
                world.spawnParticle(Particle.ANGRY_VILLAGER, location.add(0.0, 1.5, 0.0), 5, 0.2, 0.2, 0.2, 0.1)
                playSound(location, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.5f)

                // Mensaje de terror
                sendMessage(mm.deserialize("<red><bold>[!]</bold> <gray>SISTEMA CORROMPIDO: <white>Has sido infectado por la Estrella del Error."))
            }
        }
    }
}
