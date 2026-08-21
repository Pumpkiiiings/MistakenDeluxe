package liric.mistaken.commands.game

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.GameMode
import org.bukkit.entity.Player
import liric.mistaken.utils.color.ColorTranslator


object LeaveCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("salir")
            .executes { context ->
                val sender = context.source.sender
                val player = sender as? Player

                if (player == null) {
                    sender.sendMessage(ColorTranslator.translate("<red>Solo los jugadores pueden usar este comando."))
                    return@executes 0
                }

                
                val session = plugin.sessionManager.getSession(player)

                if (session == null) {
                    player.sendMessage(ColorTranslator.translate("<red>No est�s en ninguna partida activa en este momento."))
                    return@executes 0
                }

                player.sendMessage(ColorTranslator.translate("<yellow>Saliendo de la partida..."))

                
                if (session.currentState == GameState.INGAME) {
                    if (session.isKiller(player.uniqueId)) {
                        
                        plugin.killerManager.removeKiller(player)
                        session.killersUUIDs.remove(player.uniqueId)

                        if (session.killersUUIDs.isEmpty()) {
                            session.stateController.endGame("game.killer-disconnected", false)
                        }
                    } else {
                        
                        session.playerController.handlePlayerDeath(player)
                    }
                }

                
                
                if (plugin.spectatorManager.isSpectator(player)) {
                    plugin.spectatorManager.removeCustomSpectator(player)
                }

                player.inventory.clear()
                player.inventory.armorContents = arrayOfNulls(4)
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                player.gameMode = GameMode.SURVIVAL
                player.isGlowing = false
                player.isSwimming = false

                
                
                plugin.sessionManager.leaveSession(player)

                
                plugin.scoreboardManager.updatePlayer(player)

                Command.SINGLE_SUCCESS
            }
            .build()
    }
}
