package liric.mistaken.commands.player

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import org.bukkit.Sound
import org.bukkit.entity.Player

object AfkCommand {
    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("afk")
            .executes { ctx ->
                val player = ctx.source.sender as? Player ?: return@executes 0
                val uuid = player.uniqueId
                val gm = plugin.sessionManager.getSession(player)
                
                if (plugin.afkPlayers.contains(uuid)) {
                    plugin.afkPlayers.remove(uuid)
                    player.sendMessage(MessageService.getComponent(player, "game.afk-disable"))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
                } else {
                    plugin.afkPlayers.add(uuid)
                    player.sendMessage(MessageService.getComponent(player, "game.afk-enable"))
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.5f)

                    gm?.playerController?.checkWinCondition()
                }
                1
            }
            .build()
    }
}
