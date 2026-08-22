package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.modes.ModeHandler
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent

class FreezeTagModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {

    override fun onPlayerHit(attacker: Player, victim: Player, event: EntityDamageByEntityEvent) {
        val isAttackerKiller = session.isKiller(attacker.uniqueId)
        val isVictimKiller = session.isKiller(victim.uniqueId)
        
        // Survivor salva a Survivor congelado
        if (!isAttackerKiller && !isVictimKiller) {
            if (plugin.combatManager.isFrozen(victim)) {
                plugin.combatManager.resetPlayer(victim)
                victim.sendMessage(liric.mistaken.utils.color.ColorTranslator.translate("<green>¡${attacker.name} te ha descongelado!"))
                attacker.sendMessage(liric.mistaken.utils.color.ColorTranslator.translate("<green>¡Has descongelado a ${victim.name}!"))
            } else {
                event.isCancelled = true // Fuego amigo no hace daño si no está congelado
            }
        }
    }
}
