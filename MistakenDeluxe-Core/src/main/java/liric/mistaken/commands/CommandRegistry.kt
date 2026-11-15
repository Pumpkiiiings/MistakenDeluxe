package liric.mistaken.commands

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import liric.mistaken.Mistaken
import liric.mistaken.commands.admin.ArenaCommand
import liric.mistaken.commands.admin.MistakenAdminCommand
import liric.mistaken.commands.debug.MistakenDebugCommand
import liric.mistaken.commands.game.EspectearCommand
import liric.mistaken.commands.game.JoinCommand
import liric.mistaken.commands.game.LeaveCommand
import liric.mistaken.commands.game.VoteCommand
import liric.mistaken.commands.player.AfkCommand
import liric.mistaken.commands.player.LanguageCommand
import liric.mistaken.commands.player.ShopCommand
import liric.mistaken.commands.player.StatsCommand
import liric.mistaken.utils.color.ColorTranslator

class CommandRegistry(private val plugin: Mistaken) {

    fun registerAll() {
        val manager = plugin.lifecycleManager

        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event: ReloadableRegistrarEvent<Commands> ->
            val registrar = event.registrar()

            // Top-Level Game Commands
            registrar.register(JoinCommand.get(plugin), "Join a match", listOf("play"))
            registrar.register(VoteCommand.get(plugin), "Vote for a map", emptyList())
            registrar.register(LeaveCommand.get(plugin), "Leave the current match", listOf("quit"))
            registrar.register("arena", "Arena management", ArenaCommand(plugin))
            registrar.register("spectate", "Enter spectator mode", emptyList(), EspectearCommand(plugin))
            
            // Top-Level Player Commands
            registrar.register(ShopCommand.get(plugin), "Open the shop menu", emptyList())
            registrar.register(StatsCommand.get(plugin), "View player statistics", emptyList())
            registrar.register(LanguageCommand.get(plugin), "Change your language", listOf("lang"))
            registrar.register(AfkCommand.get(plugin), "Toggle AFK mode", emptyList())

            // Admin & Debug Commands
            registrar.register(MistakenDebugCommand.get(plugin), "Debug commands", listOf("mdebug"))
            registrar.register(MistakenAdminCommand.get(plugin), "Mistaken Administration", listOf("ms", "mt"))
            
            // Economy Commands
            if (!plugin.server.pluginManager.isPluginEnabled("Vault") && !plugin.server.pluginManager.isPluginEnabled("ExcellentEconomy")) {
                registrar.register(liric.mistaken.commands.economy.EcoCommand.get(plugin), "Economy administration", emptyList())
                registrar.register(liric.mistaken.commands.economy.BalanceCommand.get(plugin), "Check your balance", listOf("balance", "bal"))
            }
        }

        plugin.componentLogger.info(ColorTranslator.translate("[SUCCESS] [CommandRegistry] Commands registered successfully."))
    }
}
