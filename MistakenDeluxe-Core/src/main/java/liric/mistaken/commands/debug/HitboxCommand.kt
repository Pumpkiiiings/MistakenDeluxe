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

                
                val estado = if (isNowEnabled) "<green><bold>ACTIVADO</bold></green>" else "<red><bold>DESACTIVADO</bold></red>"
                sender.sendMessage(ColorTranslator.translate("<gray>[<yellow>DEBUG</yellow>] <white>Visor de Hitboxes: $estado"))

                Command.SINGLE_SUCCESS
            }
            .build()
    }
}
