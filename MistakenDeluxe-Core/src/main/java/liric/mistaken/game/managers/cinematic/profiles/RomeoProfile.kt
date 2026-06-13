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
import kotlin.math.cos
import kotlin.math.sin

class RomeoProfile : CinematicProfile {
    override val id: String = "romeo" // Or romeodebuff
    override val isFloating: Boolean = true

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<dark_red>EL ADMINISTRADOR"), plugin.mm.deserialize("<red>Este mundo me pertenece."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<dark_red><bold>¡MASCARADA FINAL!</bold>"), plugin.mm.deserialize("<gray><b>\$realName</b> <white>ha reclamado todas las almas."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<dark_red>¿Creen que pueden vencerme?", "<red>Yo escribí las reglas de este mundo.")
        else listOf("<dark_red>Patético.", "<red>Nadie supera a un Admin.")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        dummy.rightArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(-45.0), 0.0)
        dummy.leftArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(45.0), 0.0)
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

        world.playSound(loc, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 2f)
        displayManager.spawnOrbitingBlock(loc, Material.COMMAND_BLOCK, 2.0f, 4.0, 0.05, 1.0)
        displayManager.spawnOrbitingBlock(loc, Material.BEDROCK, 1.5f, 3.5, -0.06, -1.0)
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val angulo = ticks * 0.04
        val radio = 6.5
        camLoc.add(radio * cos(angulo), 2.5 + (ticks * 0.005), radio * sin(angulo))
        camLoc.setDirection(center.clone().add(0.0, 1.2, 0.0).toVector().subtract(camLoc.toVector()))
    }
}
