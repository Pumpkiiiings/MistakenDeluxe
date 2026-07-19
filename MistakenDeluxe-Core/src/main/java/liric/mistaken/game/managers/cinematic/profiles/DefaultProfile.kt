package liric.mistaken.game.managers.cinematic.profiles

import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.CinematicProfile
import liric.mistaken.game.managers.cinematic.DisplayManager
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.EulerAngle
import kotlin.math.cos
import kotlin.math.sin

class DefaultProfile : CinematicProfile {
    override val id: String = "default"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<red>LA CAZA COMIENZA"), plugin.mm.deserialize("<gray>El Killer es: \$realName"))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<dark_red><bold>¡MASCARADA FINAL!</bold>"), plugin.mm.deserialize("<gray><b>\$realName</b> <white>ha reclamado todas las almas."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return listOf("<red>No tienen escapatoria.", "<dark_red>Es el fin.")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        dummy.leftArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
        dummy.rightArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
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
        world.spawnParticle(org.bukkit.Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)
        displayManager.spawnRotatingItem(loc.clone().add(0.0, 2.0, 0.0), Material.NETHER_STAR, 2.0f)
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val angulo = ticks * 0.04
        val radio = 6.5
        camLoc.add(radio * cos(angulo), 2.5 + (ticks * 0.005), radio * sin(angulo))
        camLoc.setDirection(center.clone().add(0.0, 1.2, 0.0).toVector().subtract(camLoc.toVector()))
    }
}
