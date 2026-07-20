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
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin
import pumpking.lib.color.ColorTranslator

class Entity303Profile : CinematicProfile {
    override val id: String = "entity303"
    override val isFloating: Boolean = true

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<red><bold>ERROR CRÍTICO</bold>"), ColorTranslator.translate("<dark_red>SYSTEM ERROR: 303 FOUND"))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<dark_red><bold>¡MASCARADA FINAL!</bold>"), ColorTranslator.translate("<gray><b>\$realName</b> <white>ha reclamado todas las almas."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return listOf("<gray><obfuscated>xd</obfuscated> D3str0y_W0rld.exe Iniciado...", "<dark_red>Su existencia ha sido borrada.")
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

        world.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 2f)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!dummy.isValid) { task.cancel(); return@Consumer }
            if (Math.random() > 0.85) {
                val tntLoc = loc.clone().add(Math.random() * 10 - 5, Math.random() * 6 + 2, Math.random() * 10 - 5)
                displayManager.spawnGlitchBlock(tntLoc, Material.TNT)
                world.playSound(tntLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f)
                world.spawnParticle(Particle.EXPLOSION_EMITTER, tntLoc, 1)
            }
        }, 1L, 5L)
    }
}
