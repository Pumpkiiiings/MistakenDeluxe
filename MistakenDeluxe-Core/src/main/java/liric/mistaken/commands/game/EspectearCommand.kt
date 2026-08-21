package liric.mistaken.commands.game

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.GameMode
import org.bukkit.entity.Player
import liric.mistaken.utils.color.ColorTranslator


class EspectearCommand(private val plugin: Mistaken) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val player = stack.sender as? Player ?: return

        
        val session = plugin.sessionManager.getSession(player)

        if (session == null) {
            player.sendMessage(ColorTranslator.translate("<red>No estás en ninguna partida activa."))
            return
        }

        
        if (session.currentState != GameState.INGAME) {
            player.sendMessage(ColorTranslator.translate("<red>Solo puedes usar esto mientras tu partida está en curso."))
            return
        }

        
        val isSpectator = plugin.spectatorManager.isSpectator(player)

        if (!isSpectator) {
            player.sendMessage(ColorTranslator.translate("<red>¡Aún estás participando en la caza! No puedes entrar en modo espectador."))
            return
        }

        
        player.sendMessage(ColorTranslator.translate("<green>Reactivando herramientas de espectador..."))
        plugin.spectatorManager.setCustomSpectator(player)
    }
}
