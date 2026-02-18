package liric.mistaken.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0]
 * LinkCommand: Sistema de vinculación con Discord.
 * Optimización: Procesamiento JDBC asíncrono mediante Coroutines (Dispatchers.IO).
 */
class LinkCommand(private val plugin: Mistaken) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val player = stack.sender as? Player ?: run {
            stack.sender.sendMessage("Este comando es exclusivo para jugadores.")
            return
        }

        // Lanzamos la lógica pesada en un hilo de Entrada/Salida (IO)
        // Esto evita que el hilo principal se detenga a esperar a la base de datos
        plugin.pluginScope.launch(Dispatchers.IO) {
            val uuid = player.uniqueId.toString()
            val name = player.name

            try {
                // Obtenemos conexión y usamos .use {} para cerrarla automáticamente al terminar
                plugin.databaseManager.connection.use { conn ->

                    // 1. Verificar si ya existe un vínculo activo
                    val checkSql = "SELECT discord_id FROM discord_links WHERE uuid = ?;"
                    conn.prepareStatement(checkSql).use { ps ->
                        ps.setString(1, uuid)
                        val rs = ps.executeQuery()

                        if (rs.next()) {
                            val discordId = rs.getString("discord_id")
                            if (!discordId.isNullOrBlank()) {
                                player.sendMessage(Mistaken.mm.deserialize("""
                                    <newline><red><bold>❌ ¡ERROR DE VINCULACIÓN!</bold></red>
                                    <gray>Tu cuenta de Minecraft ya está enlazada a un Discord.
                                    <dark_gray><i>Si perdiste acceso a tu cuenta, contacta al Staff.</i><newline>
                                """.trimIndent()))
                                return@launch
                            }
                        }
                    }

                    // 2. Generar código aleatorio de 6 dígitos
                    // ThreadLocalRandom es más eficiente que Random() en entornos multihilo
                    val code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))

                    // 3. Guardar o actualizar el código de verificación
                    val sql = """
                        INSERT INTO discord_links (uuid, username, code) VALUES (?, ?, ?) 
                        ON DUPLICATE KEY UPDATE code = ?, username = ?;
                    """.trimIndent()

                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, uuid)
                        ps.setString(2, name)
                        ps.setString(3, code)
                        ps.setString(4, code)
                        ps.setString(5, name)
                        ps.executeUpdate()
                    }

                    // 4. Feedback visual con MiniMessage y eventos de Click/Hover
                    player.sendMessage(Mistaken.mm.deserialize("""
                        <newline><gradient:#55ffff:#55ff55><bold>VINCULACIÓN</bold></gradient>
                        <gray>Tu código secreto es: <yellow><bold>$code</bold>
                        <gray>Escríbelo en Discord: <click:copy_to_clipboard:'+verificar $code'><hover:show_text:'<green>¡Click para copiar!'><aqua>+verificar $code</aqua></hover></click>
                        <dark_gray><i>Este código es de un solo uso.</i><newline>
                    """.trimIndent()))
                }
            } catch (e: SQLException) {
                player.sendMessage(Mistaken.mm.deserialize("<red><bold>[!]</bold> Error de conexión con Clever Cloud. Reintenta en unos momentos."))
                plugin.logger.severe("Error SQL en LinkCommand: ${e.message}")
            }
        }
    }

    /**
     * Define quién puede usar el comando (Brigadier Permission Check).
     */
    override fun canUse(stack: CommandSourceStack): Boolean {
        return stack.sender.hasPermission("mistaken.link")
    }
}
