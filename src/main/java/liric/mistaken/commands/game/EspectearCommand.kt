package liric.mistaken.commands.game

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.GameMode
import org.bukkit.entity.Player

/**
 *[LIRIC-MISTAKEN 2.0]
 * EspectearCommand: Optimizado y Seguro.
 */
class EspectearCommand(private val plugin: Mistaken) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val player = stack.sender as? Player ?: return

        // 🔥 MULTIARENA: Safe Calls
        val sessionManager = plugin.sessionManager
        if (sessionManager == null) {
            player.sendMessage(plugin.mm.deserialize("<red>El sistema de partidas está desactivado."))
            return
        }

        val session = sessionManager.getSession(player)
        if (session == null) {
            player.sendMessage(plugin.mm.deserialize("<red>No estás en ninguna partida activa."))
            return
        }

        if (session.currentState != GameState.INGAME) {
            player.sendMessage(plugin.mm.deserialize("<red>Solo puedes usar esto mientras tu partida está en curso."))
            return
        }

        val esAsesino = session.esAsesino(player.uniqueId)
        // Safe Call para el manager
        val esSuperviviente = plugin.supervivienteManager?.esSupervivienteActivo(player) == true

        if (player.gameMode == GameMode.SURVIVAL && (esAsesino || esSuperviviente)) {
            player.sendMessage(plugin.mm.deserialize("<red>¡Aún estás participando en la caza! No puedes entrar en modo espectador."))
            return
        }

        player.sendMessage(plugin.mm.deserialize("<green>Reactivando herramientas de espectador..."))
        plugin.spectatorManager?.setCustomSpectator(player)
    }
}
