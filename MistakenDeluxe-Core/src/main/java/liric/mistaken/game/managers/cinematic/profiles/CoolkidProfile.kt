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
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class CoolkidProfile : CinematicProfile {
    override val id: String = "coolkid"
    override val isFloating: Boolean = false

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<green><bold>CONNECTION ESTABLISHED</bold>"), pumpking.lib.color.ColorTranslator.translate("<gray>Inyectando paquetes malignos..."))
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        return Pair(pumpking.lib.color.ColorTranslator.translate("<green><bold>CONNECTION TERMINATED</bold>"), pumpking.lib.color.ColorTranslator.translate("<gray>El servidor ha sido desconectado."))
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) listOf("<green>Iniciando ataque DDoS...", "<green>Ping a 9999ms.")
        else listOf("<green>Error Fatal 0x000000.", "<dark_green>Host Inalcanzable.")
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
        dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.STICK))
    }

    override fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager) {
        val world = loc.world ?: return
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!dummy.isValid) { task.cancel(); return@Consumer }
            val codeLoc = loc.clone().add(Math.random() * 6 - 3, Math.random() * 5, Math.random() * 6 - 3)
            world.spawnParticle(Particle.DUST, codeLoc, 1, Particle.DustOptions(Color.LIME, 1.5f))
        }, 1L, 1L)
    }

    override fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken) {
        val angulo = ticks * 0.04
        val radio = 6.5
        camLoc.add(radio * cos(angulo), 2.5 + (ticks * 0.005), radio * sin(angulo))
        camLoc.setDirection(center.clone().add(0.0, 1.2, 0.0).toVector().subtract(camLoc.toVector()))
    }
}
