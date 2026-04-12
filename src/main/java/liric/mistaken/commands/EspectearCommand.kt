package liric.mistaken.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.GameMode
import org.bukkit.entity.Player

/**
 * [LIRIC-MISTAKEN 2.0]
 * EspectearCommand: Permite a los jugadores muertos recuperar sus ítems de espectador.
 * Adaptado para MULTIARENA.
 */
class EspectearCommand(private val plugin: Mistaken) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val player = stack.sender as? Player ?: return

        // 🔥 MULTIARENA: Buscamos la sesión específica del jugador
        val session = plugin.sessionManager.getSession(player)

        if (session == null) {
            player.sendMessage(plugin.mm.deserialize("<red>No estás en ninguna partida activa."))
            return
        }

        // Verificamos el estado de SU partida
        if (session.currentState != GameState.INGAME) {
            player.sendMessage(plugin.mm.deserialize("<red>Solo puedes usar esto mientras tu partida está en curso."))
            return
        }

        // Si el jugador todavía está participando activamente (vivo)
        val esAsesino = session.esAsesino(player.uniqueId)
        val esSuperviviente = plugin.supervivienteManager.esSupervivienteActivo(player)

        if (player.gameMode == GameMode.SURVIVAL && (esAsesino || esSuperviviente)) {
            player.sendMessage(plugin.mm.deserialize("<red>¡Aún estás participando en la caza! No puedes entrar en modo espectador."))
            return
        }

        // Si ya está muerto o es un espectador legítimo de esa arena
        player.sendMessage(plugin.mm.deserialize("<green>Reactivando herramientas de espectador..."))
        plugin.spectatorManager.setCustomSpectator(player)
    }
}
