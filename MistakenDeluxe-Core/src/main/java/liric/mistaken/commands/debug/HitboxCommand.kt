package liric.mistaken.commands.debug

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.utils.misc.HitboxVisualizer
import liric.mistaken.utils.color.ColorTranslator

object HitboxCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("hitbox")
            
            .requires { source -> source.sender.hasPermission("mistaken.admin") }
            .executes { context ->
                val sender = context.source.sender

                
                val isNowEnabled = HitboxVisualizer.toggle()

                
                val state = if (isNowEnabled) "<green><bold>ENABLED</bold></green>" else "<red><bold>DISABLED</bold></red>"
                sender.sendMessage(ColorTranslator.translate("<gray>[<yellow>DEBUG</yellow>] <white>Hitbox Visualizer: $state"))

                Command.SINGLE_SUCCESS
            }
            .build()
    }
}
