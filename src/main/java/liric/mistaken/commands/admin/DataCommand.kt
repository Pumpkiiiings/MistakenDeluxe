package liric.mistaken.commands.admin

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object DataCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("data")
            .requires { source -> source.sender.hasPermission("mistaken.admin") }
            .then(
                Commands.literal("transfer")
                    .then(
                        Commands.argument("file", StringArgumentType.word())
                            .suggests { _, builder ->
                                builder.suggest("players.yml")
                                builder.buildFuture()
                            }
                            .executes { context ->
                                val sender = context.source.sender
                                val fileName = StringArgumentType.getString(context, "file")

                                if (fileName != "players.yml") {
                                    sender.sendMessage(plugin.mm.deserialize("<red>Solo se soporta players.yml por ahora."))
                                    return@executes 0
                                }

                                val file = File(plugin.dataFolder, fileName)
                                if (!file.exists()) {
                                    sender.sendMessage(plugin.mm.deserialize("<red>No se encontró el archivo $fileName"))
                                    return@executes 0
                                }

                                sender.sendMessage(plugin.mm.deserialize("<yellow>Iniciando transferencia de datos a MySQL... Esto puede tardar unos segundos.</yellow>"))

                                // Ejecutar asíncronamente para no congelar el servidor
                                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                                    try {
                                        val yaml = YamlConfiguration.loadConfiguration(file)
                                        val uuids = yaml.getKeys(false)
                                        var count = 0

                                        for (uuidStr in uuids) {
                                            val section = yaml.getConfigurationSection(uuidStr) ?: continue

                                            val lang = section.getString("lang", "es") ?: "es"
                                            val comprados = section.getStringList("comprados").joinToString(",")
                                            val seleccionado = section.getString("seleccionado", "none") ?: "none"
                                            val survComprados = section.getStringList("supervivientes_comprados").joinToString(",")
                                            val survSeleccionado = section.getString("superviviente_seleccionado", "civil") ?: "civil"
                                            val nick = section.getString("nick", "") ?: ""
                                            val skin = section.getString("skin_source", "") ?: ""

                                            // Enviar a DatabaseManager
                                            plugin.databaseManager.savePlayerDataRaw(
                                                uuidStr, lang, comprados, seleccionado, survComprados, survSeleccionado, nick, skin
                                            )
                                            count++
                                        }

                                        sender.sendMessage(plugin.mm.deserialize("<green><bold>¡ÉXITO!</bold> Se han migrado los datos de $count jugadores a la base de datos.</green>"))

                                        // Renombrar el archivo para que no se vuelva a usar por error
                                        file.renameTo(File(plugin.dataFolder, "players_OLD_BACKUP.yml"))

                                    } catch (e: Exception) {
                                        sender.sendMessage(plugin.mm.deserialize("<red>Error durante la migración: ${e.message}"))
                                        e.printStackTrace()
                                    }
                                }

                                Command.SINGLE_SUCCESS
                            }
                    )
            )
            .build()
    }
}