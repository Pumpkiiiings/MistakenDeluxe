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
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle
import kotlin.math.cos
import kotlin.math.sin

class MariachiProfile : CinematicProfile {
    override val id: String = "mariachi"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<red><bold>EL CHARRO NEGRO</bold>"), plugin.mm.deserialize("<gold>¡Ay ay ay! Canta y no llores..."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<gold><bold>¡SALUD!</bold>"), plugin.mm.deserialize("<yellow>*Toma un trago de tequila*"))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<gold>¡Vamos a darle vuelo a la hilacha!", "<red>¡Yee-haw!")
        else listOf("<gold>Un buen tequila...", "<yellow>Para las penas del alma.")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        dummy.rightArmPose = EulerAngle(Math.toRadians(-60.0), Math.toRadians(-30.0), 0.0)
        dummy.leftArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(45.0), 0.0)
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)
        dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.NOTE_BLOCK))
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 2f, 1f)
        world.spawnParticle(Particle.NOTE, loc.clone().add(0.0, 2.0, 0.0), 100, 2.0, 2.0, 2.0, 1.0)
        if (!isIntro) displayManager.spawnRotatingItem(loc.clone().add(0.0, 2.5, 0.0), Material.HONEY_BOTTLE, 2f)
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val angulo = ticks * 0.04
        val radio = 6.5
        camLoc.add(radio * cos(angulo), 2.5 + (ticks * 0.005), radio * sin(angulo))
        camLoc.setDirection(center.clone().add(0.0, 1.2, 0.0).toVector().subtract(camLoc.toVector()))
    }
}
