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

                // 1. Buscamos si el player est� en alguna sesi�n activa
                val session = plugin.sessionManager.getSession(player)

                if (session == null) {
                    player.sendMessage(ColorTranslator.translate("<red>No est�s en ninguna partida activa en este momento."))
                    return@executes 0
                }

                player.sendMessage(ColorTranslator.translate("<yellow>Saliendo de la partida..."))

                
                if (session.currentState == GameState.INGAME) {
                    if (session.isKiller(player.uniqueId)) {
                        // Si el killer se rinde
                        plugin.killerManager.removeKiller(player)
                        session.killersUUIDs.remove(player.uniqueId)

                        if (session.killersUUIDs.isEmpty()) {
                            session.stateController.endGame("game.killer-disconnected", false)
                        }
                    } else {
                        // Si el survivor se rinde (Lo tratamos como si hubiera sido asesinado)
                        session.playerController.handlePlayerDeath(player)
                    }
                }

                // 3. LIMPIEZA F�SICA PARA EL LOBBY
                // Evita que lleguen al lobby del Multiarena volando, con pociones o con �tems del juego
                if (plugin.spectatorManager.isSpectator(player)) {
                    plugin.spectatorManager.removeCustomSpectator(player)
                }

                player.inventory.clear()
                player.inventory.armorContents = arrayOfNulls(4)
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                player.gameMode = GameMode.SURVIVAL
                player.isGlowing = false
                player.isSwimming = false

                // 4. SALIDA OFICIAL DE LA SESI�N
                // Esto dispara el BungeeUtils (Velocity) o el Teleport al Lobby (Multiarena)
                plugin.sessionManager.leaveSession(player)

                // Actualizamos su scoreboard al del Lobby
                plugin.scoreboardManager.updatePlayer(player)

                Command.SINGLE_SUCCESS
            }
            .build()
    }
}
