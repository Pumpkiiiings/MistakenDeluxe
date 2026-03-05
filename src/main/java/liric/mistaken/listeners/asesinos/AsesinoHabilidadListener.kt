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
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoHabilidadListener: Gestión de disparadores y proyectiles especiales.
 * FIX: Prevención de doble-ejecución (MainHand/OffHand) y fusión de código duplicado.
 */
class AsesinoHabilidadListener(private val plugin: Mistaken) : Listener {

    private val mm = plugin.mm
    private val plain = PlainTextComponentSerializer.plainText()

    /**
     * Trigger: Activación de habilidades activas (Click Derecho).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseAbility(event: PlayerInteractEvent) {
        // 1. 🔥 FIX CRÍTICO: Evitar doble ejecución.
        // Bukkit dispara este evento para la mano principal y la secundaria. Solo nos importa la principal.
        if (event.hand != EquipmentSlot.HAND) return

        // 2. Filtro Rápido de Acción
        if (!event.action.isRightClick) return

        // 3. Filtro de Estado
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val player = event.player
        val slot = player.inventory.heldItemSlot

        // 4. Filtro de Slot (Solo slots del 1 al 4 disparan habilidades)
        if (slot !in 1..4) return

        // 5. Búsqueda de Clase (Operación O(1))
        val asesino = plugin.asesinoManager.getAsesinoDelJugador(player) ?: return

        // 6. Validación de Item
        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) return

        // Ejecución
        event.isCancelled = true
        asesino.usarHabilidad(player, slot)
    }

    /**
     * Lógica de impacto: Habilidades basadas en proyectiles.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        // Casting rápido para no gastar recursos si es una flecha de esqueleto vanilla
        val snowball = event.entity as? Snowball ?: return

        val nameComp = snowball.customName() ?: return
        val rawName = plain.serialize(nameComp)

        if (rawName == "303_infection") {
            val loc = snowball.location
            val world = loc.world ?: return

            // --- 1. EFECTOS VISUALES ---
            world.spawnParticle(Particle.ENCHANTED_HIT, loc, 15, 0.3, 0.3, 0.3, 0.1)

            val dust = Particle.DustOptions(Color.fromRGB(0, 255, 240), 1.0f)
            world.spawnParticle(Particle.DUST, loc, 10, 0.2, 0.2, 0.2, 0.1, dust)

            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f)
            world.playSound(loc, Sound.ENTITY_ITEM_BREAK, 0.8f, 0.1f)

            // --- 2. LÓGICA DE IMPACTO EN SUPERVIVIENTE ---
            val victim = event.hitEntity as? Player ?: return

            if (plugin.asesinoManager.esElAsesino(victim)) return

            victim.apply {
                addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
                addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))

                plugin.gameManager.combatManager.takeDamage(this)

                world.spawnParticle(Particle.ANGRY_VILLAGER, location.add(0.0, 1.5, 0.0), 5, 0.2, 0.2, 0.2, 0.1)
                playSound(location, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.5f)

                sendMessage(mm.deserialize("<red><bold>[!]</bold> <gray>SISTEMA CORROMPIDO: <white>Has sido infectado por la Estrella del Error."))
            }
        }
    }
}
