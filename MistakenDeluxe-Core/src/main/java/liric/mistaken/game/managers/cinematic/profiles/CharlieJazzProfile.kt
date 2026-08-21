package liric.mistaken.game.managers.cinematic.profiles

import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.CinematicProfile
import liric.mistaken.game.managers.cinematic.DisplayManager
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.entity.BlockDisplay
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.EulerAngle
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin
import pumpking.lib.color.ColorTranslator

class CharlieJazzProfile : CinematicProfile {
    override val id: String = "charliejazz"
    override val isFloating: Boolean = false
    override val introCameraStyle = liric.mistaken.game.managers.cinematic.CameraStyle.DRONE_SPIRAL
    override val outroCameraStyle = liric.mistaken.game.managers.cinematic.CameraStyle.DRONE_SPIRAL

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<gradient:#e0b0ff:#ffd700>ESTRELLA CA�DA</gradient>"), ColorTranslator.translate("<gold>Que comience el espect�culo."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<dark_purple><bold>TEL�N</bold>"), ColorTranslator.translate("<gold>El show termin�."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<gold>El jazz nunca muere.", "<light_purple>�Y ustedes tampoco sobrevivir�n!")
        else listOf("<gold>Silencio...", "<dark_gray>La m�sica se detuvo.")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        if (isIntro) {
            // Girar de espaldas
            val loc = dummy.location
            loc.yaw = loc.yaw + 180f
            dummy.teleport(loc)

            dummy.headPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
            dummy.rightArmPose = EulerAngle(Math.toRadians(-20.0), 0.0, Math.toRadians(15.0))
            dummy.leftArmPose = EulerAngle(Math.toRadians(-20.0), 0.0, Math.toRadians(-15.0))
            dummy.rightLegPose = EulerAngle(0.0, 0.0, Math.toRadians(5.0))
            dummy.leftLegPose = EulerAngle(0.0, 0.0, Math.toRadians(-5.0))
        } else {
            dummy.headPose = EulerAngle(Math.toRadians(40.0), 0.0, 0.0)
            dummy.bodyPose = EulerAngle(Math.toRadians(15.0), 0.0, 0.0)
            dummy.rightArmPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
            dummy.leftArmPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
        }
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)
        dummy.setItem(EquipmentSlot.HAND, inv.itemInMainHand)
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager, viewers: List<Player>) {
        val world = loc.world ?: return
        
        viewers.forEach { p ->
            if (isIntro) {
                liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 150, 0, 255, 0.6f, 60)
                liric.mistaken.utils.hooks.ObserverHook.playScreenshake(p, 1.0f, 40)
                p.playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.5f)
            } else {
                liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 200, 150, 0, 0.7f, 80)
                liric.mistaken.utils.hooks.ObserverHook.playScreenshake(p, 1.0f, 30)
                p.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.5f)
            }
        }

        if (isIntro) {
            world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.5f)
            
            // Spawn orbiting blocks
            val b1 = world.spawn(dummy.location, BlockDisplay::class.java) { bd -> bd.block = Material.AMETHYST_BLOCK.createBlockData() }
            val b2 = world.spawn(dummy.location, BlockDisplay::class.java) { bd -> bd.block = Material.GOLD_BLOCK.createBlockData() }
            
            var ticks = 0
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (!dummy.isValid) { 
                    b1.remove()
                    b2.remove()
                    task.cancel()
                    return@Consumer 
                }
                val speed = 0.5 // High speed
                val angle = ticks * speed
                val radius = 1.5
                
                b1.teleport(dummy.location.clone().add(radius * cos(angle), 1.2, radius * sin(angle)))
                b2.teleport(dummy.location.clone().add(radius * cos(angle + Math.PI), 1.2, radius * sin(angle + Math.PI)))
                
                world.spawnParticle(Particle.END_ROD, dummy.location.add(0.0, 1.0, 0.0), 3, 0.5, 1.0, 0.5, 0.0)
                ticks++
            }, 1L, 1L)
        }
    }
}
