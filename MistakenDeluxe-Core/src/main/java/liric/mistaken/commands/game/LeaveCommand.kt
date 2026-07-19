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
 * FIX: Maneja correctamente el abandono en medio del juego (Victoria/Derrota automática).
 */
object LeaveCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("salir")
            .executes { context ->
                val sender = context.source.sender
                val player = sender as? Player

                if (player == null) {
                    sender.sendMessage(pumpking.lib.color.ColorTranslator.translate("<red>Solo los jugadores pueden usar este comando."))
                    return@executes 0
                }

                // 1. Buscamos si el jugador está en alguna sesión activa
                val session = plugin.sessionManager.getSession(player)

                if (session == null) {
                    player.sendMessage(pumpking.lib.color.ColorTranslator.translate("<red>No estás en ninguna partida activa en este momento."))
                    return@executes 0
                }

                player.sendMessage(pumpking.lib.color.ColorTranslator.translate("<yellow>Saliendo de la partida..."))

                // 2. LÓGICA DE ABANDONO (Si la partida ya empezó)
                if (session.currentState == GameState.INGAME) {
                    if (session.isKiller(player.uniqueId)) {
                        // Si el asesino se rinde
                        plugin.asesinoManager.removeKiller(player)
                        session.asesinosUUIDs.remove(player.uniqueId)

                        if (session.asesinosUUIDs.isEmpty()) {
                            session.stateController.endGame("game.killer-disconnected", false)
                        }
                    } else {
                        // Si el superviviente se rinde (Lo tratamos como si hubiera sido asesinado)
                        session.playerController.handlePlayerDeath(player)
                    }
                }

                // 3. LIMPIEZA FÍSICA PARA EL LOBBY
                // Evita que lleguen al lobby del Multiarena volando, con pociones o con ítems del juego
                if (plugin.spectatorManager.isSpectator(player)) {
                    plugin.spectatorManager.removeCustomSpectator(player)
                }

                player.inventory.clear()
                player.inventory.armorContents = arrayOfNulls(4)
                player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                player.gameMode = GameMode.SURVIVAL
                player.isGlowing = false
                player.isSwimming = false

                // 4. SALIDA OFICIAL DE LA SESIÓN
                // Esto dispara el BungeeUtils (Velocity) o el Teleport al Lobby (Multiarena)
                plugin.sessionManager.leaveSession(player)

                // Actualizamos su scoreboard al del Lobby
                plugin.scoreboardManager.updatePlayer(player)

                Command.SINGLE_SUCCESS
            }
            .build()
    }
}