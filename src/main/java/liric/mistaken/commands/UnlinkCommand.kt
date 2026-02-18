package liric.mistaken.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.BasicCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit

/**
 * UnlinkCommand - El martillo de desvinculación (Versión Kotlin Brigadier).
 *
 * Optimización:
 * - Argumentos tipados (StringArgumentType).
 * - Sugestiones asíncronas de jugadores.
 * - Ejecución de SQL en Dispatchers.IO (Corrutinas).
 */
object UnlinkCommand {

    private val mm = MiniMessage.miniMessage()

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("unlink")
            // 1. FILTRO NINJA (Si no tiene permiso, el comando "no existe")
            .requires { stack ->
                stack.sender.hasPermission("mistaken.admin")
            }
            // 2. ARGUMENTO OBLIGATORIO: <target>
            .then(
                Commands.argument("target", StringArgumentType.word())
                    // Sugerir nombres de jugadores online (pero permitir escribir cualquiera)
                    .suggests { _, builder ->
                        Bukkit.getOnlinePlayers().forEach { player ->
                            builder.suggest(player.name)
                        }
                        builder.buildFuture()
                    }
                    // 3. EJECUCIÓN
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        val targetName = StringArgumentType.getString(ctx, "target")

                        // Feedback inmediato
                        sender.sendMessage(mm.deserialize("<gray>Procesando desvinculación para <yellow>$targetName</yellow>...</gray>"))

                        // 4. LÓGICA ASÍNCRONA (Corrutinas)
                        // Usamos un Scope global o del plugin para lanzar la tarea al hilo de IO
                        CoroutineScope(Dispatchers.IO).launch {
                            val sql = "UPDATE discord_links SET discord_id = NULL, code = NULL WHERE username = ?;"

                            try {
                                // try-with-resources al estilo Kotlin (.use)
                                plugin.databaseManager.connection.use { conn ->
                                    conn.prepareStatement(sql).use { stmt ->
                                        stmt.setString(1, targetName)
                                        val rowsAffected = stmt.executeUpdate()

                                        // Paper permite enviar mensajes asíncronamente con Adventure API
                                        if (rowsAffected > 0) {
                                            sender.sendMessage(mm.deserialize("<newline><green>✅ ¡Vínculo eliminado!</green>"))
                                            sender.sendMessage(mm.deserialize("<gray>El jugador <white>$targetName</white> ya puede usar <aqua>/link</aqua> de nuevo.<newline>"))
                                        } else {
                                            sender.sendMessage(mm.deserialize("<red>❌ No se encontró a <white>$targetName</white> en la base de datos."))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                sender.sendMessage(mm.deserialize("<red><bold>[!]</bold> Error crítico de DB. Revisa la consola."))
                                e.printStackTrace()
                            }
                        }

                        1 // Éxito en la sintaxis del comando
                    }
            )
            .build()
    }
}
