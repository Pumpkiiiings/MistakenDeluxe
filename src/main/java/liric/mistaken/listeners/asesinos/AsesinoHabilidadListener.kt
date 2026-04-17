package liric.mistaken.listeners.asesinos

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Color
import org.bukkit.GameMode
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
 * AsesinoHabilidadListener: Gestión de disparadores adaptada a MULTIARENA.
 * FIX: Ahora detecta la sesión individual del asesino para activar habilidades.
 */
class AsesinoHabilidadListener(private val plugin: Mistaken) : Listener {

    private val mm = plugin.mm
    private val plain = PlainTextComponentSerializer.plainText()

    /**
     * Trigger: Activación de habilidades activas (Click Derecho).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseAbility(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.isRightClick) return

        val player = event.player

        // 🔥 MULTIARENA: Buscamos la sesión específica del asesino
        val session = plugin.sessionManager.getSession(player) ?: return
        if (session.currentState != GameState.INGAME) return

        // Seguridad: Bloqueamos si el asesino está muerto/especteando o en vanish
        if (player.gameMode != GameMode.SURVIVAL || player.isInvisible) return

        val slot = player.inventory.heldItemSlot
        if (slot !in 0..3) return // Ajustado a slots 0-3 (habilidades 1-4)

        // Verificamos que sea el asesino de SU sesión
        if (!session.esAsesino(player.uniqueId)) return

        val asesino = plugin.asesinoManager.getAsesinoDelJugador(player) ?: return

        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) return

        event.isCancelled = true

        // Ejecutar habilidad (El slot de la interfaz suele ser slot+1 para la lógica interna)
        asesino.usarHabilidad(player, slot + 1)
    }

    /**
     * Lógica de impacto: Habilidades basadas en proyectiles (Ej: Entity 303).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        val shooter = snowball.shooter as? Player ?: return

        // 🔥 MULTIARENA: Detectamos la sesión del disparador
        val session = plugin.sessionManager.getSession(shooter) ?: return

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

            // --- 2. LÓGICA DE IMPACTO ---
            val victim = event.hitEntity as? Player ?: return

            // No infectar a otros asesinos de la misma sesión
            if (session.esAsesino(victim.uniqueId)) return

            // Verificamos que la víctima sea un superviviente válido en esa arena
            if (victim.gameMode != GameMode.SURVIVAL || victim.isInvisible) return

            victim.apply {
                addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
                addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))

                // 🔥 DAÑO: Usamos el combatManager de la sesión correspondiente
                session.combatManager.takeDamage(this)

                world.spawnParticle(Particle.ANGRY_VILLAGER, location.add(0.0, 1.5, 0.0), 5, 0.2, 0.2, 0.2, 0.1)
                playSound(location, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.5f)

                sendMessage(mm.deserialize("<red><bold>[!]</bold> <gray>SISTEMA CORROMPIDO: <white>Has sido infectado por la Estrella del Error."))
            }
        }
    }
}
