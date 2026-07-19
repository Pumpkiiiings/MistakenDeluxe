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
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle
import kotlin.math.cos
import kotlin.math.sin

class TetoProfile : CinematicProfile {
    override val id: String = "teto"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<red>TERRITORY</red>"), pumpking.lib.color.ColorTranslator.translate("<white>¡Eres tan tonto!"))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<dark_red><bold>¡MASCARADA FINAL!</bold>"), pumpking.lib.color.ColorTranslator.translate("<gray><b>\$realName</b> <white>ha reclamado todas las almas."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<red>¡Baka baka baka!", "<white>El pan es mío.")
        else listOf("<red>¿Ves? Te lo dije.", "<white>Nadie me gana.")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        dummy.rightArmPose = EulerAngle(Math.toRadians(-120.0), Math.toRadians(-20.0), 0.0)
        dummy.leftArmPose = EulerAngle(Math.toRadians(-30.0), Math.toRadians(30.0), 0.0)
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)
        dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.BREAD))
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        world.playSound(loc, Sound.ENTITY_GHAST_SCREAM, 0.5f, 2f)
        displayManager.spawnRotatingItem(loc.clone().add(0.0, 2.0, 0.0), Material.BREAD, 2.0f)
        world.spawnParticle(Particle.DUST, loc, 200, 3.0, 3.0, 3.0, Particle.DustOptions(Color.RED, 1.5f))
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val angulo = Math.PI / 2 + sin(ticks * 0.02) * 2.0
        val radio = 6.0
        camLoc.add(radio * cos(angulo), 2.5, radio * sin(angulo))
        camLoc.setDirection(center.clone().add(0.0, 2.5, 0.0).toVector().subtract(camLoc.toVector()))
    }
}
