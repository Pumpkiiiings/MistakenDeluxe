package liric.mistaken.game.managers.cinematic.profiles

import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.CinematicProfile
import liric.mistaken.game.managers.cinematic.DisplayManager
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.EulerAngle
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class SowoulProfile : CinematicProfile {
    override val id: String = "sowoul"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<dark_purple>EL MAGO HA LLEGADO"), pumpking.lib.color.ColorTranslator.translate("<light_purple>Que comience el espectáculo..."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<dark_red><bold>¡MASCARADA FINAL!</bold>"), pumpking.lib.color.ColorTranslator.translate("<gray><b>\$realName</b> <white>ha reclamado todas las almas."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<light_purple>Damas y caballeros...", "<light_purple>¡Bienvenidos a la función final!")
        else listOf("<light_purple>¡Muchas gracias audiencia!", "<light_purple>¡Gracias por ver mi gran espectáculo!")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        if (isIntro) {
            dummy.leftArmPose = EulerAngle(Math.toRadians(-60.0), Math.toRadians(-45.0), 0.0)
            dummy.rightArmPose = EulerAngle(Math.toRadians(-60.0), Math.toRadians(45.0), 0.0)
        } else {
            dummy.bodyPose = EulerAngle(Math.toRadians(20.0), 0.0, 0.0)
            dummy.headPose = EulerAngle(Math.toRadians(30.0), 0.0, 0.0)
            dummy.rightArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(-45.0), 0.0)
            dummy.leftArmPose = EulerAngle(Math.toRadians(45.0), 0.0, 0.0)
        }
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        if (isIntro) {
            world.playSound(loc, Sound.ITEM_TRIDENT_RETURN, 2f, 1f)
            world.spawnParticle(Particle.WITCH, loc, 100, 2.0, 1.0, 2.0, 0.1)
        } else {
            world.playSound(loc, Sound.ENTITY_VILLAGER_CELEBRATE, 2f, 1f)
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 2f, 1f)
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (!dummy.isValid) { task.cancel(); return@Consumer }
                val roseLoc = loc.clone().add(Math.random() * 8 - 4, 6.0, Math.random() * 8 - 4)
                displayManager.spawnFallingItem(roseLoc, Material.POPPY)
            }, 1L, 5L)
        }
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val angulo = ticks * 0.04
        val radio = 6.5
        camLoc.add(radio * cos(angulo), 2.5 + (ticks * 0.005), radio * sin(angulo))
        camLoc.setDirection(center.clone().add(0.0, 1.2, 0.0).toVector().subtract(camLoc.toVector()))
    }
}
