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

class BendyProfile : CinematicProfile {
    override val id: String = "bendy"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<black><bold>LA TINTA LLAMA</bold>"), pumpking.lib.color.ColorTranslator.translate("<dark_gray>Él ha sido liberado..."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<black><bold>CONSUMIDOS</bold>"), pumpking.lib.color.ColorTranslator.translate("<dark_gray>El estudio reclamó lo que es suyo."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<black>T i n t a . . .", "<dark_gray>V e n  a q u í . . .")
        else listOf("<black>T o d o   e s   m í o .", "<dark_gray>J a j a j a . . .")
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
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        world.playSound(loc, Sound.ENTITY_SQUID_SQUIRT, 2f, 0.5f)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!dummy.isValid) { task.cancel(); return@Consumer }
            world.spawnParticle(Particle.SQUID_INK, loc.clone().add(0.0, 0.5, 0.0), 10, 2.0, 0.2, 2.0, 0.0)
            world.spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, loc.clone().add(0.0, 3.0, 0.0), 5, 0.5, 0.5, 0.5, 0.0)
        }, 1L, 2L)
    }
}