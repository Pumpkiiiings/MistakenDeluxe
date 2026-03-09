package liric.mistaken.commands

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom

/**
 *[LIRIC-MISTAKEN 2.0]
 * LinkCommand: Sistema de vinculación con Discord (Versión Nodo).
 * Optimización: JDBC asíncrono y permisos nativos.
 */
object LinkCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("link")
            // Aquí manejamos el permiso, ¡adiós al error de canUse!
            .requires { it.sender.hasPermission("mistaken.link") }
            .executes { ctx ->
                val sender = ctx.source.sender
                val player = sender as? Player ?: run {
                    sender.sendMessage("§cEste comando es solo para jugadores.")
                    return@executes 0
                }

                // 🔥 ARREGLADO: Usamos el AsyncScheduler nativo de Paper
                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    val uuid = player.uniqueId.toString()
                    val name = player.name

                    try {
                        // OJO: Asegúrate que en Mistaken.kt se llame 'databaseManager'
                        plugin.databaseManager.connection.use { conn ->

                            // 1. Verificar vínculo previo
                            val checkSql = "SELECT discord_id FROM discord_links WHERE uuid = ?;"
                            conn.prepareStatement(checkSql).use { ps ->
                                ps.setString(1, uuid)
                                val rs = ps.executeQuery()

                                if (rs.next()) {
                                    val discordId = rs.getString("discord_id")
                                    if (!discordId.isNullOrBlank()) {
                                        player.sendMessage(plugin.mm.deserialize("""
                                            <newline><red><bold>❌ ¡ERROR DE VINCULACIÓN!</bold></red>
                                            <gray>Tu cuenta de Minecraft ya está enlazada a un Discord.
                                            <dark_gray><i>Si perdiste acceso a tu cuenta, contacta al Staff.</i><newline>
                                        """.trimIndent()))
                                        // 🔥 ARREGLADO: El return ahora apunta a runNow
                                        return@runNow
                                    }
                                }
                            }

                            // 2. Generar código secreto
                            val code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))

                            // 3. Guardar código en la DB
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

                            // 4. Feedback chulo con MiniMessage
                            player.sendMessage(plugin.mm.deserialize("""
                                <newline><gradient:#55ffff:#55ff55><bold>VINCULACIÓN</bold></gradient>
                                <gray>Tu código secreto es: <yellow><bold>$code</bold>
                                <gray>Escríbelo en Discord: <click:copy_to_clipboard:'+verificar $code'><hover:show_text:'<green>¡Click para copiar!'><aqua>+verificar $code</aqua></hover></click>
                                <dark_gray><i>Este código es de un solo uso.</i><newline>
                            """.trimIndent()))
                        }
                    } catch (e: SQLException) {
                        player.sendMessage(plugin.mm.deserialize("<red><bold>[!]</bold> Error de conexión con Clever Cloud."))
                        plugin.componentLogger.error("Fallo en LinkCommand SQL: ${e.message}")
                    }
                }
                1 // Éxito para Brigadier
            }
            .build()
    }
}
