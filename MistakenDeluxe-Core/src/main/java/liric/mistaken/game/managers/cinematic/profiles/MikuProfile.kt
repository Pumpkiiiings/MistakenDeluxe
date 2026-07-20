package liric.mistaken.game.managers.cinematic.profiles

import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.CinematicProfile
import liric.mistaken.game.managers.cinematic.DisplayManager
import net.kyori.adventure.text.Component
import org.bukkit.Color
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

class MikuProfile : CinematicProfile {
    override val id: String = "miku"
    override val isFloating: Boolean = true

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<aqua>DOMINACIÓN MUNDIAL</aqua>"), ColorTranslator.translate("<white>¡El mundo es mío!"))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<aqua><bold>CONCIERTO FINAL</bold>"), ColorTranslator.translate("<white>¡Gracias a todos por venir!"))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<aqua>¿Están listos para cantar?", "<white>¡Aki Miku-chan!")
        else listOf("<aqua>¡Miku Miku BEEEEEEAAAM!", "<white>Nos vemos en la próxima función~")
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
        dummy.setItem(EquipmentSlot.HAND, inv.itemInMainHand)
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        world.playSound(loc, Sound.MUSIC_DISC_5, 1f, 1.5f)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!dummy.isValid) { task.cancel(); return@Consumer }
            world.spawnParticle(Particle.END_ROD, dummy.location.clone().add(0.0, 1.5, 0.0), 10, 5.0, 0.1, 5.0, 0.0)
            world.spawnParticle(Particle.DUST, loc, 20, 3.0, 3.0, 3.0, Particle.DustOptions(Color.AQUA, 1.5f))
        }, 1L, 2L)
    }
}
