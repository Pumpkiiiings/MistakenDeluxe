package liric.mistaken.commands.debug

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.roles.killers.Killer
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
                                source.sendMessage(pumpking.lib.color.ColorTranslator.translate("<green>Reproduciendo <bold>INTRO</bold> de: <yellow>$asesinoId"))

                                plugin.cinematicManager.playKillerIntro(source, asesinoDummy, listOf(source))
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
                                source.sendMessage(pumpking.lib.color.ColorTranslator.translate("<red>Reproduciendo <bold>OUTRO</bold> de: <yellow>$asesinoId"))

                                plugin.cinematicManager.playKillerOutro(source, asesinoDummy, listOf(source))
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
            .build()
    }

    private fun createDummy(id: String): Killer {
        return object : Killer(id, "<gold><bold>${id.uppercase()}</bold></gold>") {
            override fun equip(player: Player) {}
            override fun useSkill(player: Player, slot: Int) {}
            override fun showTrail(player: Player) {}
            override fun showPhysicalTrail(player: Player) {}
        }
    }
}