package liric.mistaken.api

import liric.mistaken.Mistaken
import liric.mistaken.api.managers.IAsesinoManager
import liric.mistaken.api.managers.IConfigManager
import liric.mistaken.api.managers.ISessionManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.Plugin
import java.util.logging.Logger

class MistakenAPIImpl(private val _plugin: Mistaken) : MistakenAPI {
    override val plugin: Plugin
        get() = _plugin
    override val asesinoManager: IAsesinoManager
        get() = _plugin.asesinoManager
    override val sessionManager: ISessionManager
        get() = _plugin.sessionManager
    override val configManager: IConfigManager
        get() = _plugin.configManager
    override val playerDataManager: liric.mistaken.api.managers.IPlayerDataManager
        get() = _plugin.playerDataManager
    override val messages: pumpking.lib.messages.IMessageService
        get() = pumpking.lib.service.PumpkingServiceManager.messages
    override val mm: MiniMessage
        get() = _plugin.mm
    override val logger: Logger
        get() = _plugin.logger

    override fun isIgnored(player: org.bukkit.entity.Player): Boolean {
        return _plugin.isIgnored(player)
    }
}

