package liric.mistaken.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import org.bukkit.GameMode
import org.bukkit.entity.Player

class EspectearCommand(private val plugin: Mistaken) : BasicCommand {
    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val player = stack.sender as? Player ?: return

        if (plugin.gameManager.currentState != GameState.INGAME) {
            player.sendMessage(plugin.mm.deserialize("<red>Solo puedes usar esto durante una partida en curso."))
            return
        }

        // Si está vivo (Superviviente activo o Asesino activo)
        if (player.gameMode == GameMode.SURVIVAL && player.health > 0) {
            player.sendMessage(plugin.mm.deserialize("<red>¡Aún estás vivo! No puedes usar este comando."))
            return
        }

        // Si está muerto, le damos la GUI y el modo vuelo invisible
        plugin.spectatorManager.setCustomSpectator(player)
    }
}
