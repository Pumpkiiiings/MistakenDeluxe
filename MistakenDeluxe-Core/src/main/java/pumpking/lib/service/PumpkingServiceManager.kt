package pumpking.lib.service

import org.bukkit.plugin.java.JavaPlugin
import pumpking.lib.animation.AnimationEngine
import pumpking.lib.messages.MessageService
import pumpking.lib.command.PumpkingCommand

object PumpkingServiceManager {
    lateinit var messages: MessageService
        private set

    lateinit var animation: AnimationEngine
        private set

    fun init(plugin: JavaPlugin) {
        messages = MessageService()
        messages.init()

        animation = AnimationEngine(plugin)
        animation.init()

        // Register Command
        try {
            val manager = plugin.lifecycleManager
            manager.registerEventHandler(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS) { event: io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent<io.papermc.paper.command.brigadier.Commands> ->
                event.registrar().register("pumpking", "Pumpking Framework Commands", listOf("pk"), PumpkingCommand())
            }
        } catch (e: Exception) {
            pumpking.lib.core.PumpkingLib.logError(pumpking.lib.core.PumpkingLib.LogCategory.CORE, "Failed to register pumpking command via lifecycle")
        }
    }

    fun shutdown() {
        animation.shutdown()
    }
}
