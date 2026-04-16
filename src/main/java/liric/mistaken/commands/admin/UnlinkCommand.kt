package liric.mistaken.commands.admin

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit

object UnlinkCommand {

    private val mm = MiniMessage.miniMessage()

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("unlink")
            .requires { stack -> stack.sender.hasPermission("mistaken.admin") }
            .then(
                Commands.argument("target", StringArgumentType.word())
                    .suggests { _, builder: SuggestionsBuilder ->
                        Bukkit.getOnlinePlayers().forEach { player ->
                            builder.suggest(player.name)
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        val targetName = StringArgumentType.getString(ctx, "target")

                        sender.sendMessage(mm.deserialize("<gray>Procesando desvinculación para <yellow>$targetName</yellow>...</gray>"))

                        CoroutineScope(Dispatchers.IO).launch {
                            val sql = "UPDATE discord_links SET discord_id = NULL, code = NULL WHERE username = ?;"
                            try {
                                plugin.databaseManager.connection.use { conn ->
                                    conn.prepareStatement(sql).use { stmt ->
                                        stmt.setString(1, targetName)
                                        val rowsAffected = stmt.executeUpdate()

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
                        1
                    }
            )
            .build()
    }
}