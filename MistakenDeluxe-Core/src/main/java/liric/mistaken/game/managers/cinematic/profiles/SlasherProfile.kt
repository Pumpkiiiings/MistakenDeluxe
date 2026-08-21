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
import org.bukkit.Particle
import pumpking.lib.color.ColorTranslator

class SlasherProfile : CinematicProfile {
    override val id: String = "slasher"
    override val isFloating: Boolean = false
    override val introCameraStyle = liric.mistaken.game.managers.cinematic.CameraStyle.PAN_UP_REVEAL
    override val outroCameraStyle = liric.mistaken.game.managers.cinematic.CameraStyle.PAN_UP_REVEAL

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<dark_red>LA EJECUCI�N"), ColorTranslator.translate("<red>Nadie escapa de White Pumpkin."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<dark_red><bold>�LO TENGO!</bold>"), ColorTranslator.translate("<red>Por fin... mi pedernal y acero."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<dark_red>M�s sangre para mi hacha...", "<red>Griten todo lo que quieran.") 
        else listOf("<white>�JAJAJAJA!", "<white>�El pedernal y acero por fin es M�O!")
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

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager, viewers: List<Player>) {
        val world = loc.world ?: return

        viewers.forEach { p ->
            if (isIntro) {
                liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 150, 0, 0, 0.7f, 60)
                liric.mistaken.utils.hooks.ObserverHook.playScreenshake(p, 0.8f, 40)
                p.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f)
            } else {
                liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 150, 0, 0, 0.8f, 80)
                liric.mistaken.utils.hooks.ObserverHook.playScreenshake(p, 1.0f, 30)
                p.playSound(loc, org.bukkit.Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
            }
        }
        world.spawnParticle(Particle.FIREWORK, loc.clone().add(0.0, 1.0, 0.0), 3)
        
        if (!isIntro) {
            world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)
            displayManager.spawnRotatingItem(loc.clone().add(-1.0, 2.0, 0.0), Material.IRON_INGOT, 1.5f)
            displayManager.spawnRotatingItem(loc.clone().add(1.0, 2.0, 0.0), Material.FLINT, 1.5f)
        } else {
            world.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
        }
    }
}
