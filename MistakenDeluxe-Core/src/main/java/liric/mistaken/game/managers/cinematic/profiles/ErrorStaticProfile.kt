package liric.mistaken.game.managers.cinematic.profiles

import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.CinematicProfile
import liric.mistaken.game.managers.cinematic.DisplayManager
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.EulerAngle
import java.util.function.Consumer
import pumpking.lib.color.ColorTranslator

class ErrorStaticProfile : CinematicProfile {
    override val id: String = "error_estatico"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<white><obfuscated>||</obfuscated> ERROR <obfuscated>||</obfuscated>"), ColorTranslator.translate("<gray>S1st3m4 C0rrupt0..."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<dark_aqua><obfuscated>|</obfuscated> <aqua>STATIC</aqua> <dark_aqua><obfuscated>|</obfuscated>"), ColorTranslator.translate("<gray>T H I S   I S   H O W   I T   S H O U L D   B E."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<gray>*Estática de Televisión*", "<white>E-E-Error 404.")
        else listOf("<aqua>Cerrando sistema.", "<dark_gray>This is how it should be.")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        if (!isIntro) {
            dummy.rightArmPose = EulerAngle(Math.toRadians(-90.0), 0.0, 0.0)
            dummy.leftArmPose = EulerAngle(Math.toRadians(-90.0), 0.0, 0.0)
        } else {
            dummy.leftArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
            dummy.rightArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
        }
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)

        if (isIntro) dummy.isInvisible = true
        else dummy.setItem(EquipmentSlot.HAND, inv.itemInMainHand)
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        if (isIntro) {
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 0.1f)
            displayManager.spawnStaticBlock(loc.clone().add(0.0, 1.0, 0.0), Material.SEA_LANTERN, 1.5f)
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (!dummy.isValid) { task.cancel(); return@Consumer }
                world.spawnParticle(
                    Particle.DUST, loc.clone().add(0.0, 1.5, 0.0),
                    10, 0.5, 0.5, 0.5, Particle.DustOptions(Color.FUCHSIA, 1f)
                )
            }, 1L, 2L)
        } else {
            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.1f)
            displayManager.spawnGlitchBlock(loc, Material.BLACK_CONCRETE)
            plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
                world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f)
            }, 100L)
        }
    }
}
