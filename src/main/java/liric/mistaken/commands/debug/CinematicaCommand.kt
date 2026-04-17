package liric.mistaken.commands.debug

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.roles.asesinos.Asesino
import org.bukkit.entity.Player

object CinematicaCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        val asesinosList = listOf(
            "sowoul", "pizzano", "errorestatico", "charlieinferno",
            "colorandelectricity", "rome", "romeodebuff", "slasher",
            "herobrine", "nullasesino", "entity303", "bendy", "kasaneteto", "mariachi"
        )

        return Commands.literal("cinematica")
            .requires { source -> source.sender.hasPermission("mistaken.admin") }
            // --- SUBCOMANDO: INTRO ---
            .then(
                Commands.literal("intro")
                    .then(
                        Commands.argument("asesino", StringArgumentType.word())
                            .suggests { _, builder ->
                                asesinosList.forEach { if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .executes { context ->
                                val source = context.source.sender as? Player ?: return@executes 0
                                val asesinoId = StringArgumentType.getString(context, "asesino")

                                val asesinoDummy = createDummy(asesinoId)
                                source.sendMessage(plugin.mm.deserialize("<green>Reproduciendo <bold>INTRO</bold> de: <yellow>$asesinoId"))

                                plugin.cinematicManager.playKillerIntro(source, asesinoDummy)
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
            // --- SUBCOMANDO: OUTRO (Victoria) ---
            .then(
                Commands.literal("outro")
                    .then(
                        Commands.argument("asesino", StringArgumentType.word())
                            .suggests { _, builder ->
                                asesinosList.forEach { if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .executes { context ->
                                val source = context.source.sender as? Player ?: return@executes 0
                                val asesinoId = StringArgumentType.getString(context, "asesino")

                                val asesinoDummy = createDummy(asesinoId)
                                source.sendMessage(plugin.mm.deserialize("<red>Reproduciendo <bold>OUTRO</bold> de: <yellow>$asesinoId"))

                                plugin.cinematicManager.playKillerOutro(source, asesinoDummy)
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
            .build()
    }

    private fun createDummy(id: String): Asesino {
        return object : Asesino(id, "<gold><bold>${id.uppercase()}</bold></gold>") {
            override fun equipar(player: Player) {}
            override fun usarHabilidad(player: Player, slot: Int) {}
            override fun mostrarTrail(player: Player) {}
            override fun mostrarTrailFisico(player: Player) {}
        }
    }
}