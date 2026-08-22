package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.modes.ModeHandler
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player

class HideAndSeekModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {
    
    override fun onPlayerSpawn(player: Player, isKiller: Boolean) {
        if (isKiller) {
            player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 1200, 1, false, false, false))
            player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 1200, 255, false, false, false))
            player.sendMessage(liric.mistaken.utils.color.ColorTranslator.translate("<green>Has sido cegado e inmovilizado por 1 minuto para darles ventaja a los supervivientes."))

            player.scheduler.runDelayed(plugin, { _ ->
                if (player.isOnline && session.currentState == GameState.INGAME) {
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST)
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE)
                    player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                    player.showTitle(Title.title(
                        MessageService.getComponent(player, "game.killer-released-title"),
                        MessageService.getComponent(player, "game.killer-released-subtitle")
                    ))
                }
            }, null, 1200L)
        } else {
            player.sendMessage(liric.mistaken.utils.color.ColorTranslator.translate("<green>¡Tienes 1 minuto para esconderte antes de que el asesino sea liberado!"))
            player.scheduler.runDelayed(plugin, { _ ->
                if (player.isOnline && session.currentState == GameState.INGAME) {
                    player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.8f)
                    player.showTitle(Title.title(
                        MessageService.getComponent(player, "game.killer-released-title"),
                        MessageService.getComponent(player, "game.killer-released-subtitle")
                    ))
                }
            }, null, 1200L) // 60 segundos
        }
    }
}
