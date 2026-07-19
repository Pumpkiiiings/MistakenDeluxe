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

class PizzanoProfile : CinematicProfile {
    override val id: String = "pizzano"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<red>LA CAZA COMIENZA"), plugin.mm.deserialize("<gray>El Killer es: \$realName"))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(plugin.mm.deserialize("<dark_red><bold>¡MASCARADA FINAL!</bold>"), plugin.mm.deserialize("<gray><b>\$realName</b> <white>ha reclamado todas las almas."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<yellow>¡Demasiada energía!", "<gold>¡Corran, corran, corran!")
        else listOf("<yellow>Ufff... eso fue un buen ejercicio.", "<gold>¿Alguien tiene un poco de azúcar?")
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
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        if (isIntro) {
            world.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1.5f, 1f)
            world.spawnParticle(Particle.CLOUD, loc, 100, 1.0, 1.0, 1.0, 0.5)
        } else {
            world.playSound(loc, Sound.ENTITY_PLAYER_BURP, 1.5f, 0.8f)
            displayManager.spawnRotatingItem(loc.clone().add(1.5, 1.0, 0.0), Material.COOKIE, 1.5f)
            displayManager.spawnRotatingItem(loc.clone().add(-1.5, 1.5, 0.0), Material.SUGAR, 1.5f)
        }
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val angulo = ticks * 0.04
        val radio = 6.5
        camLoc.add(radio * cos(angulo), 2.5 + (ticks * 0.005), radio * sin(angulo))
        camLoc.setDirection(center.clone().add(0.0, 1.2, 0.0).toVector().subtract(camLoc.toVector()))
    }
}
