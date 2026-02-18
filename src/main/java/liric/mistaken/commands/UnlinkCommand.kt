package liric.mistaken.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import java.sql.SQLException

/**
 * [LIRIC-MISTAKEN 2.0]
 * UnlinkCommand: El martillo de desvinculación (Brigadier Edition).
 * Optimización: Operaciones JDBC en Dispatchers.IO para evitar micro-tirones.
 */
class UnlinkCommand(private val plugin: Mistaken) : BasicCommand {

    private val mm = Mistaken.mm

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender

        // 1. FILTRO NINJA: Si no es admin, fingimos que el comando no existe.
        if (!sender.hasPermission("mistaken.admin")) {
            sender.sendMessage(mm.deserialize("<red>Unknown command. Type \"/help\" for help."))
            return
        }

        // 2. Validación de argumentos
        if (args.isEmpty()) {
            sender.sendMessage(mm.deserialize("<yellow>⚠️ Uso correcto: /unlink <jugador>"))
            return
        }

        val targetName = args[0]

        // 3. Limpieza de base de datos asíncrona (Coroutine IO)
        // No usamos el scheduler de Bukkit para ahorrar recursos de tareas.
        plugin.pluginScope.launch(Dispatchers.IO) {
            val sql = "UPDATE discord_links SET discord_id = NULL, code = NULL WHERE username = ?;"

            try {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, targetName)
                        val rowsAffected = ps.executeUpdate()

                        if (rowsAffected > 0) {
                            sender.sendMessage(mm.deserialize("""
                                <newline><green>✅ ¡Vínculo eliminado!</green>
                                <gray>El jugador <white>$targetName</white> ya puede usar <aqua>/link</aqua> de nuevo.<newline>
                            """.trimIndent()))
                        } else {
                            sender.sendMessage(mm.deserialize("<red>❌ No se encontró a <white>$targetName</white> en la base de datos."))
                        }
                    }
                }
            } catch (e: SQLException) {
                sender.sendMessage(mm.deserialize("<red><bold>[!]</bold> Error de DB al desvincular. Revisa la consola."))
                plugin.logger.severe("Fallo SQL en UnlinkCommand: ${e.message}")
            }
        }
    }

    /**
     * Autocompletado inteligente (Brigadier).
     * Solo muestra sugerencias si el usuario es administrador.
     */
    override fun suggest(stack: CommandSourceStack, args: Array<String>): List<String> {
        if (!stack.sender.hasPermission("mistaken.admin")) return emptyList()

        return if (args.size == 1) {
            Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(args[0], ignoreCase = true) }
        } else {
            emptyList()
        }
    }

    /**
     * Este método oculta el comando del autocompletado del cliente si no tiene permiso.
     */
    override fun canUse(stack: CommandSourceStack): Boolean {
        return stack.sender.hasPermission("mistaken.admin")
    }
}
