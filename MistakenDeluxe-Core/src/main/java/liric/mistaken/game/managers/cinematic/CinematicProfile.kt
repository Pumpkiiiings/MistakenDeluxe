package liric.mistaken.game.managers.cinematic

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import liric.mistaken.Mistaken

interface CinematicProfile {
    val id: String
    val isFloating: Boolean

    fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component>
    fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component>
    fun getDialogs(isIntro: Boolean): List<String>
    
    fun applyPose(dummy: ArmorStand, isIntro: Boolean)
    fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean)
    
    fun playEffects(plugin: Mistaken, loc: Location, dummy: ArmorStand, isIntro: Boolean, displayManager: DisplayManager)
    
    fun processCameraTick(camLoc: Location, center: Location, dummy: ArmorStand, ticks: Int, isIntro: Boolean, plugin: Mistaken)
}
