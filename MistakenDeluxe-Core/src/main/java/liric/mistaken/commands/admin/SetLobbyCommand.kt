package liric.mistaken.commands.admin

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Sound
import org.bukkit.entity.Player
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService

/**
 * SetLobbyCommand - Kotlin Edition (Paper 1.21.4+)
 *
 * Optimizaci�n:
 * - Usa Brigadier Node para inyecci�n directa en el Dispatcher.
 * - Validaci�n de permisos nativa (.requires).
 * - Cero "reflection" de Bukkit antiguo.
 */
object SetLobbyCommand {

    private val mm = MiniMessage.miniMessage()

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("setlobby")
            
            
            .requires { source ->
                source.sender.hasPermission("mistaken.admin")
            }
            
            .executes { ctx ->
                val sender = ctx.source.sender

                
                val player = sender as? Player

                
                if (player == null) {
                    sender.sendMessage(MessageService.getComponent(null, "errors.player-only"))
                    return@executes 0 
                }

                

                
                plugin.setLobbyLocationConfig(player.location)

                
                val message = MessageService.getComponent(player, "admin.lobby-set")
                player.sendMessage(message)

                
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)

                
                
                plugin.componentLogger.info(ColorTranslator.translate(
                    "<gray>[Mistaken]</gray> <green>Lobby location updated in </green><white>${player.world.name}</white><green> by </green><white>${player.name}</white>"
                ))

                1 
            }
            .build()
    }
}
