package liric.mistaken.commands.other

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom
import pumpking.lib.color.ColorTranslator


object LinkCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("link")
            // AquÃ­ manejamos el permiso, Â¡adiÃ³s al error de canUse!
            .requires { it.sender.hasPermission("mistaken.link") }
            .executes { ctx ->
                val sender = ctx.source.sender
                val player = sender as? Player ?: run {
                    sender.sendMessage("Â§cEste comando es solo para jugadores.")
                    return@executes 0
                }

                // ðŸ”¥ ARREGLADO: Usamos el AsyncScheduler nativo de Paper
                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    val uuid = player.uniqueId.toString()
                    val name = player.name

                    try {
                        // OJO: AsegÃºrate que en Mistaken.kt se llame 'databaseManager'
                        plugin.databaseManager.connection.use { conn ->

                            // 1. Verificar vÃ­nculo previo
                            val checkSql = "SELECT discord_id FROM discord_links WHERE uuid = ?;"
                            conn.prepareStatement(checkSql).use { ps ->
                                ps.setString(1, uuid)
                                val rs = ps.executeQuery()

                                if (rs.next()) {
                                    val discordId = rs.getString("discord_id")
                                    if (!discordId.isNullOrBlank()) {
                                        player.sendMessage(ColorTranslator.translate("""
                                            <newline><red><bold>âŒ Â¡ERROR DE VINCULACIÃ“N!</bold></red>
                                            <gray>Tu cuenta de Minecraft ya estÃ¡ enlazada a un Discord.
                                            <dark_gray><i>Si perdiste acceso a tu cuenta, contacta al Staff.</i><newline>
                                        """.trimIndent()))
                                        // ðŸ”¥ ARREGLADO: El return ahora apunta a runNow
                                        return@runNow
                                    }
                                }
                            }

                            // 2. Generar cÃ³digo secreto
                            val code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))

                            // 3. Guardar cÃ³digo en la DB
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
                            player.sendMessage(ColorTranslator.translate("""
                                <newline><gradient:#55ffff:#55ff55><bold>VINCULACIÃ“N</bold></gradient>
                                <gray>Tu cÃ³digo secreto es: <yellow><bold>$code</bold>
                                <gray>EscrÃ­belo en Discord: <click:copy_to_clipboard:'+verificar $code'><hover:show_text:'<green>Â¡Click para copiar!'><aqua>+verificar $code</aqua></hover></click>
                                <dark_gray><i>Este cÃ³digo es de un solo uso.</i><newline>
                            """.trimIndent()))
                        }
                    } catch (e: SQLException) {
                        player.sendMessage(ColorTranslator.translate("<red><bold>[!]</bold> Error de conexiÃ³n con Clever Cloud."))
                        plugin.componentLogger.error(pumpking.lib.color.ColorTranslator.translate("<red>[ERROR]</red> <gray>LinkCommand SQL failure: ${e.message}</gray>"))
                    }
                }
                1 // Ã‰xito para Brigadier
            }
            .build()
    }
}
