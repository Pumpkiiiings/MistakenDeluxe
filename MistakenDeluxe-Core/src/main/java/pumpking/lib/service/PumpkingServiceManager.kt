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
        val cmd = plugin.getCommand("pumpking")
        if (cmd != null) {
            val executor = PumpkingCommand()
            cmd.setExecutor(executor)
            cmd.setTabCompleter(executor)
        }
    }

    fun shutdown() {
        animation.shutdown()
    }
}
