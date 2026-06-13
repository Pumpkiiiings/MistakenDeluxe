package liric.mistaken.game.managers.cinematic.profiles

import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.CinematicProfile
import liric.mistaken.game.managers.cinematic.DisplayManager
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle

class SlasherProfile : CinematicProfile {
    override val id: String = "slasher"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<dark_red>LA EJECUCIÓN"), plugin.mm.deserialize("<red>Nadie escapa de White Pumpkin."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<dark_red><bold>¡LO TENGO!</bold>"), plugin.mm.deserialize("<red>Por fin... mi pedernal y acero."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<dark_red>Más sangre para mi hacha...", "<red>Griten todo lo que quieran.") 
        else listOf("<white>¡JAJAJAJA!", "<white>¡El pedernal y acero por fin es MÍO!")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        if (isIntro) {
            dummy.rightArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
        } else {
            dummy.headPose = EulerAngle(Math.toRadians(-20.0), 0.0, 0.0)
            dummy.rightArmPose = EulerAngle(Math.toRadians(-160.0), 0.0, 0.0)
            dummy.leftArmPose = EulerAngle(Math.toRadians(-160.0), 0.0, 0.0)
        }
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)
        
        if (!isIntro) {
            dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.FLINT))
            dummy.setItem(EquipmentSlot.OFF_HAND, ItemStack(Material.IRON_INGOT))
        } else {
            dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.DIAMOND_AXE))
        }
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(org.bukkit.Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)
        
        if (!isIntro) {
            world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)
            displayManager.spawnRotatingItem(loc.clone().add(-1.0, 2.0, 0.0), Material.IRON_INGOT, 1.5f)
            displayManager.spawnRotatingItem(loc.clone().add(1.0, 2.0, 0.0), Material.FLINT, 1.5f)
        } else {
            world.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
        }
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val dist = 3.0
        camLoc.add(0.0, 0.2, dist)
        camLoc.setDirection(center.clone().add(0.0, 1.8, 0.0).toVector().subtract(camLoc.toVector()))
        if (!isIntro) dummy.setRotation((ticks * 0.5f) % 360, dummy.location.pitch)
    }
}
