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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.EulerAngle
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class ColorAndElectricityProfile : CinematicProfile {
    override val id: String = "colorandelectricity" // Or colorsito
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<aqua>COLOR & ELECTRICITY"), pumpking.lib.color.ColorTranslator.translate("<yellow>Mmm... deliciosos colores..."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<dark_red><bold>¡QUÉ HE HECHO!</bold>"), pumpking.lib.color.ColorTranslator.translate("<red>M-Mi color... todo se deforma..."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<yellow>*Comiendo colores rápidamente*", "<aqua>Aún tengo hambre...")
        else listOf("<red>Oh no... m-me comí a uno...", "<dark_red>M-Me estoy volviendo rojo... quiero ser normal...")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        if (isIntro) {
            dummy.headPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
            dummy.rightArmPose = EulerAngle(Math.toRadians(-120.0), Math.toRadians(-30.0), 0.0)
        } else {
            dummy.headPose = EulerAngle(Math.toRadians(45.0), 0.0, 0.0)
            dummy.rightArmPose = EulerAngle(Math.toRadians(-140.0), Math.toRadians(-45.0), 0.0)
            dummy.leftArmPose = EulerAngle(Math.toRadians(-140.0), Math.toRadians(45.0), 0.0)
        }
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)
        if (isIntro) dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.TRIDENT))
        else dummy.setItem(EquipmentSlot.HAND, inv.itemInMainHand)
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        if (isIntro) {
            world.playSound(loc, Sound.ENTITY_PLAYER_BURP, 2f, 1f)
            val colors = listOf(Color.RED, Color.YELLOW, Color.AQUA, Color.LIME)
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (!dummy.isValid) { task.cancel(); return@Consumer }
                world.spawnParticle(
                    Particle.DUST, dummy.location.clone().add(0.0, 1.5, 0.0),
                    10, 0.2, 0.2, 0.2, Particle.DustOptions(colors.random(), 1.5f)
                )
            }, 1L, 5L)
        } else {
            world.playSound(loc, Sound.ENTITY_GHAST_WARN, 1f, 0.5f)
            world.playSound(loc, Sound.ENTITY_WITCH_DRINK, 1f, 0.8f)
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (!dummy.isValid) { task.cancel(); return@Consumer }
                world.spawnParticle(Particle.SPLASH, dummy.location.add(0.0, 1.6, 0.0), 10, 0.1, 0.0, 0.1, 0.1)
                world.spawnParticle(
                    Particle.DUST, dummy.location.add(0.0, 1.0, 0.0),
                    20, 0.5, 0.5, 0.5, Particle.DustOptions(Color.RED, 2f)
                )
            }, 1L, 2L)
        }
    }
}
