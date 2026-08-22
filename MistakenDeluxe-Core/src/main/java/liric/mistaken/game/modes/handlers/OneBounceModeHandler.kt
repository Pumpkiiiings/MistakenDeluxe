package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.modes.ModeHandler
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class OneBounceModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {
    
    override fun calculateKillersCount(onlineCount: Int): Int = (onlineCount - 1).coerceAtLeast(1)

    override fun getMaxStamina(player: Player): Double = if (!session.isKiller(player.uniqueId)) 500.0 else 100.0
    
    override fun onPlayerSpawn(player: Player, isKiller: Boolean) {
        if (!isKiller) {
            player.scheduler.runDelayed(plugin, { _ ->
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, Int.MAX_VALUE, 1, false, false, false))
                val baseHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue ?: 20.0
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = baseHealth * 3.0
                player.health = baseHealth * 3.0
                
                val user = plugin.playerDataManager.getUserData(player.uniqueId)
                if (user != null) {
                    user.stamina = 500.0
                }
            }, null, 10L)
        }
    }
}
