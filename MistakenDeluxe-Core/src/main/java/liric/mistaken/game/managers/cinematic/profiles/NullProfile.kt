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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.EulerAngle
import java.util.function.Consumer

class NullProfile : CinematicProfile {
    override val id: String = "null" // Or nullasesino
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<red>LA CAZA COMIENZA"), pumpking.lib.color.ColorTranslator.translate("<gray>El Killer es: \$realName"))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<dark_red><bold>¡MASCARADA FINAL!</bold>"), pumpking.lib.color.ColorTranslator.translate("<gray><b>\$realName</b> <white>ha reclamado todas las almas."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return listOf("<dark_gray>...", "<black>...")
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

        world.playSound(loc, Sound.AMBIENT_CAVE, 2f, 0.5f)
        var dTicks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!dummy.isValid) { task.cancel(); return@Consumer }
            world.spawnParticle(Particle.SQUID_INK, loc.clone().add(0.0, dTicks * 0.02, 0.0), 20, 1.5, 0.5, 1.5, 0.0)
            world.spawnParticle(Particle.SMOKE, loc, 30, 2.0, 2.0, 2.0, 0.05)
            if (!isIntro && dTicks == 100) dummy.isInvisible = true
            dTicks++
        }, 1L, 1L)
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val dist = 5.0 - (ticks * 0.02)
        camLoc.add(0.0, 1.0, dist)
        camLoc.setDirection(center.clone().add(0.0, 1.0, 0.0).toVector().subtract(camLoc.toVector()))
        if (!isIntro && ticks > 120) plugin.server.onlinePlayers.forEach {
            it.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
        }
    }
}
