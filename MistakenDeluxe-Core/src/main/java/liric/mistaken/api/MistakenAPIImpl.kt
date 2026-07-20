package liric.mistaken.api

import liric.mistaken.Mistaken
import liric.mistaken.api.managers.IKillerManager
import liric.mistaken.api.managers.IConfigManager
import liric.mistaken.api.managers.ISessionManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.Plugin
import java.util.logging.Logger
import liric.mistaken.api.managers.IPlayerDataManager
import org.bukkit.entity.Player
import pumpking.lib.messages.IMessageService
import pumpking.lib.service.PumpkingServiceManager

class MistakenAPIImpl(private val _plugin: Mistaken) : MistakenAPI {
    override val plugin: Plugin
        get() = _plugin
    override val asesinoManager: IKillerManager
        get() = _plugin.asesinoManager
    override val sessionManager: ISessionManager
        get() = _plugin.sessionManager
    override val configManager: IConfigManager
        get() = _plugin.configManager
    override val playerDataManager: IPlayerDataManager
        get() = _plugin.playerDataManager
    override val messages: IMessageService
        get() = PumpkingServiceManager.messages
    override val mm: MiniMessage
        get() = _plugin.mm
    override val logger: Logger
        get() = _plugin.logger

    override fun isIgnored(player: Player): Boolean {
        return _plugin.isIgnored(player)
    }
}

