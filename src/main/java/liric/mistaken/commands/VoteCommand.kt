package liric.mistaken.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * [LIRIC-MISTAKEN 2.0]
 * VoteCommand: Sistema de democracia de mapas.
 * Optimizado para Paper 1.21.4 utilizando Brigadier nativo.
 */
class VoteCommand(private val plugin: Mistaken) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        // 1. Solo jugadores pueden votar
        val player = stack.sender as? Player ?: run {
            stack.sender.sendMessage(plugin.messageConfig.getMessage(null, "errors.only-players"))
            return
        }

        // 2. Verificación de estado de la partida
        if (plugin.gameManager.currentState != GameState.VOTING) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "voting.not-active"))
            return
        }

        val voteManager = plugin.gameManager.voteManager

        // 3. Verificar si el jugador ya votó
        if (voteManager.hasVoted(player.uniqueId)) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "voting.already-voted"))
            return
        }

        // 4. Validación de argumentos
        if (args.isEmpty()) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "voting.usage"))
            return
        }

        // Soporte para nombres de mapas con espacios de forma eficiente
        val inputName = args.joinToString(" ")

        // 5. Resolución del mapa (Búsqueda optimizada en el Map de arenas)
        // Usamos find para obtener la clave original respetando mayúsculas si es necesario
        val actualMapName = plugin.arenaManager.getArenas().keys.find {
            it.equals(inputName, ignoreCase = true)
        }

        if (actualMapName == null) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "voting.not-found",
                Placeholder.parsed("map", inputName)))
            return
        }

        // 6. Registro del voto
        voteManager.addVote(player.uniqueId, actualMapName)

        // Feedback visual y auditivo
        player.sendMessage(plugin.messageConfig.getMessage(player, "voting.success",
            Placeholder.parsed("map", actualMapName)))

        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.2f)
    }

    /**
     * Autocompletado optimizado nativo de Brigadier.
     * Los mapas se filtran instantáneamente mientras el usuario escribe.
     */
    override fun suggest(stack: CommandSourceStack, args: Array<String>): List<String> {
        // Solo sugerir si estamos en fase de votación para ahorrar paquetes de red inútiles
        if (plugin.gameManager.currentState != GameState.VOTING) return emptyList()

        return if (args.size == 1) {
            plugin.arenaManager.getArenas().keys
                .filter { it.startsWith(args[0], ignoreCase = true) }
        } else {
            emptyList()
        }
    }

    /**
     * Todos los jugadores pueden usar este comando por defecto.
     */
    override fun canUse(stack: CommandSourceStack): Boolean {
        return true
    }
}
