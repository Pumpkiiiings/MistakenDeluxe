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
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle
import java.util.function.Consumer
import pumpking.lib.color.ColorTranslator

class DevestoProfile : CinematicProfile {
    override val id: String = "devesto"
    override val isFloating: Boolean = true

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<dark_purple><bold>[F3X]</bold>"), ColorTranslator.translate("<gray>Cargando herramientas de construcción..."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(ColorTranslator.translate("<dark_purple><bold>//SET 0</bold>"), ColorTranslator.translate("<gray>Borrado exitoso."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<blue>Cargando esquemas...", "<dark_purple>Listos para purgar.")
        else listOf("<dark_purple>Control, Alt...", "<blue>DELETE.")
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        dummy.rightArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(-45.0), 0.0)
        dummy.leftArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(45.0), 0.0)
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)
        dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.WOODEN_AXE))
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)
        if (!isIntro) {
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (!dummy.isValid) { task.cancel(); return@Consumer }
                world.spawnParticle(
                    Particle.BLOCK_MARKER,
                    loc.clone().add(Math.random() * 10 - 5, Math.random() * 5, Math.random() * 10 - 5),
                    1, Material.BARRIER.createBlockData()
                )
                world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.5f, 0.5f)
            }, 1L, 5L)
    }
    }
}
