package liric.mistaken.commands.player

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.entity.Player

object LanguageCommand {
    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("language")
            .then(
                Commands.argument("lang", StringArgumentType.word())
                    .suggests { _, builder: SuggestionsBuilder ->
                        MessageService.getLoadedLanguages().forEach { lang ->
                            if (lang.startsWith(builder.remainingLowerCase)) {
                                builder.suggest(lang)
                            }
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: return@executes 0
                        val targetLang = StringArgumentType.getString(ctx, "lang").lowercase()

                        if (MessageService.getLoadedLanguages().contains(targetLang)) {
                            plugin.playerDataManager.setLanguage(player.uniqueId, targetLang)
                            player.sendMessage(MessageService.getComponent(player, "admin.lang-set", Placeholder.parsed("langs", targetLang)))
                            player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1f, 1f)
                        } else {
                            player.sendMessage(MessageService.getComponent(player, "errors.lang-not-found"))
                        }
                        1
                    }
            )
            .executes { ctx ->
                val player = ctx.source.sender as? Player ?: return@executes 0
                player.sendMessage(MessageService.getComponent(player, "admin.usage-lang"))
                1
            }
            .build()
    }
}
