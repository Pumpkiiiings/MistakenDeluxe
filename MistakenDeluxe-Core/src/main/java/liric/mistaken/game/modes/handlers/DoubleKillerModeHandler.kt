package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.modes.ModeHandler

class DoubleKillerModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {
    
    override fun calculateKillersCount(onlineCount: Int): Int = if (onlineCount >= 4) 2 else 1
    
    override fun getKillerBaseDamage(): Double = 4.0
    
    // Fuego amigo entre killers permitido (Double Killer original)
}
