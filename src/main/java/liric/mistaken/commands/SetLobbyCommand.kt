package liric.mistaken.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.Mistaken
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * [LIRIC-MISTAKEN 2.0]
 * SetLobbyCommand: Establece el punto de spawn global.
 * Optimizado para Paper 1.21.4 con Brigadier.
 */
class SetLobbyCommand(private val plugin: Mistaken) : BasicCommand {

    private val mm = Mistaken.mm

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender

        // 1. Identificar al ejecutor de forma segura (Cast as? Player)
        val player = sender as? Player ?: run {
            sender.sendMessage("Este comando solo puede ser ejecutado por jugadores.")
            return
        }

        // 2. Persistencia física
        // El método setLobbyLocation ya está optimizado en el Main para guardar en config.
        plugin.setLobbyLocation(player.location)

        // 3. Feedback visual y auditivo localizado
        player.sendMessage(plugin.messageConfig.getMessage(player, "admin.lobby-set"))
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)

        // 4. Registro de auditoría para la consola (MiniMessage)
        plugin.logger.info("Lobby actualizado en ${player.world.name} por ${player.name}")
    }

    /**
     * Define quién puede usar el comando y lo oculta de la lista de autocompletado
     * para usuarios sin permisos.
     */
    override fun canUse(stack: CommandSourceStack): Boolean {
        return stack.sender.hasPermission("mistaken.admin")
    }

    /**
     * Al no tener argumentos, el autocompletado devuelve una lista vacía.
     */
    override fun suggest(stack: CommandSourceStack, args: Array<String>): List<String> {
        return emptyList()
    }
}
