package liric.mistaken.commands.player

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import org.bukkit.Sound
import org.bukkit.entity.Player

object ShopCommand {
    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("shop")
            .executes { ctx ->
                val player = ctx.source.sender as? Player ?: return@executes 0
                
                if (plugin.serverMode == "GAME_SERVER") {
                    // Check if player is in lobby
                    val gm = plugin.sessionManager.getSession(player)
                    if (gm?.currentState != liric.mistaken.game.enums.GameState.LOBBY && gm?.currentState != liric.mistaken.game.enums.GameState.VOTING && gm?.currentState != liric.mistaken.game.enums.GameState.BREAK) {
                        player.sendMessage(liric.mistaken.config.engine.core.MessageService.getComponent(player, "errors.lobby-only-command"))
                        return@executes 0
                    }
                }

                plugin.shopSelector.abrir(player)
                player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1f, 1.2f)
                1
            }
            .build()
    }
}
