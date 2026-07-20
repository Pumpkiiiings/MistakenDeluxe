package liric.mistaken.game.managers.cinematic.profiles

import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.CinematicProfile
import liric.mistaken.game.managers.cinematic.DisplayManager
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.EulerAngle
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin
import pumpking.lib.color.ColorTranslator

class CharlieProfile : CinematicProfile {
    override val id: String = "charlie" // Or charlieinferno
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<gold>CAÍDO DEL CIELO"), ColorTranslator.translate("<red>Bienvenido a mi infierno."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<dark_red><bold>CENIZAS</bold>"), ColorTranslator.translate("<gold>¿Es este el fin de Charlie?"))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<gold>Charlie ha aterrizado.", "<red>¡Y ustedes arderán conmigo!")
        else listOf("<gold>Todo arde...", "<dark_gray>¿Acaso este es el final del camino?")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        if (isIntro) {
            dummy.rightArmPose = EulerAngle(Math.toRadians(-180.0), Math.toRadians(45.0), 0.0)
            dummy.leftArmPose = EulerAngle(Math.toRadians(-180.0), Math.toRadians(-45.0), 0.0)
            dummy.rightLegPose = EulerAngle(Math.toRadians(20.0), 0.0, Math.toRadians(20.0))
            dummy.leftLegPose = EulerAngle(Math.toRadians(20.0), 0.0, Math.toRadians(-20.0))
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

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        if (isIntro) {
            world.playSound(loc, Sound.ENTITY_GHAST_SCREAM, 2f, 0.5f)
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (!dummy.isValid) { task.cancel(); return@Consumer }
                world.spawnParticle(Particle.FLAME, dummy.location.add(0.0, 1.0, 0.0), 20, 0.5, 1.0, 0.5, 0.0)
            }, 1L, 2L)
        } else {
            world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 2f, 1f)
            world.playSound(loc, Sound.MUSIC_DISC_11, 1f, 0.5f)
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (!dummy.isValid) { task.cancel(); return@Consumer }
                val angle = Math.random() * Math.PI * 2
                val radius = Math.random() * 5 + 2
                val pLoc = loc.clone().add(radius * cos(angle), 0.0, radius * sin(angle))
                world.spawnParticle(Particle.FLAME, pLoc, 10, 0.2, 2.0, 0.2, 0.1)
                world.spawnParticle(Particle.LAVA, pLoc, 2)
            }, 1L, 2L)
        }
    }
}
