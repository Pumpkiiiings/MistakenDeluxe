package liric.mistaken.commands.misc

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.utils.HitboxVisualizer

object HitboxCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("hitbox")
            // Solo admins pueden usar este comando
            .requires { source -> source.sender.hasPermission("mistaken.admin") }
            .executes { context ->
                val sender = context.source.sender

                // 🔥 Usamos el nuevo método toggle() que creamos en HitboxVisualizer
                val isNowEnabled = HitboxVisualizer.toggle()

                // Mensaje con colores dependiendo de si se activó o desactivó
                val estado = if (isNowEnabled) "<green><bold>ACTIVADO</bold></green>" else "<red><bold>DESACTIVADO</bold></red>"
                sender.sendMessage(plugin.mm.deserialize("<gray>[<yellow>DEBUG</yellow>] <white>Visor de Hitboxes: $estado"))

                Command.SINGLE_SUCCESS
            }
            .build()
    }
}