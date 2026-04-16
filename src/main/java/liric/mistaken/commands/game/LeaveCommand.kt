package liric.mistaken.commands.game

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.GameMode
import org.bukkit.entity.Player

/**
 *[LIRIC-MISTAKEN 2.0]
 * LeaveCommand: Permite salir de la partida actual.
 * FIX: Manejo limpio y seguro para el Scheduler del jugador.
 */
object LeaveCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("salir")
            .executes { context ->
                val sender = context.source.sender
                val player = sender as? Player

                if (player == null) {
                    sender.sendMessage(plugin.mm.deserialize("<red>Solo los jugadores pueden usar este comando."))
                    return@executes 0
                }

                val sessionManager = plugin.sessionManager
                if (sessionManager == null) {
                    player.sendMessage(plugin.mm.deserialize("<red>El sistema de partidas está desactivado."))
                    return@executes 0
                }

                val session = sessionManager.getSession(player)
                if (session == null) {
                    player.sendMessage(plugin.mm.deserialize("<red>No estás en ninguna partida activa en este momento."))
                    return@executes 0
                }

                player.sendMessage(plugin.mm.deserialize("<yellow>Saliendo de la partida..."))

                if (session.currentState == GameState.INGAME) {
                    if (session.esAsesino(player.uniqueId)) {
                        plugin.asesinoManager?.removerAsesino(player)
                        session.asesinosUUIDs.remove(player.uniqueId)

                        if (session.asesinosUUIDs.isEmpty()) {
                            session.stateController.endGame("game.killer-disconnected", false)
                        }
                    } else {
                        session.playerController.handlePlayerDeath(player)
                    }
                }

                // Limpieza física delegada al EntityScheduler
                player.scheduler.run(plugin, { _ ->
                    if (plugin.spectatorManager?.isSpectator(player) == true) {
                        plugin.spectatorManager?.removeCustomSpectator(player)
                    }

                    player.inventory.clear()
                    player.inventory.armorContents = arrayOfNulls(4)
                    player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                    player.gameMode = GameMode.SURVIVAL
                    player.isGlowing = false
                    player.isSwimming = false

                    sessionManager.leaveSession(player)
                    plugin.scoreboardManager.updatePlayer(player)
                }, null)

                Command.SINGLE_SUCCESS
            }
            .build()
    }
}
