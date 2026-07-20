package liric.mistaken.api

import liric.mistaken.api.managers.IKillerManager
import liric.mistaken.api.managers.ISessionManager
import liric.mistaken.api.managers.IConfigManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.Plugin
import java.util.logging.Logger
import liric.mistaken.api.managers.IPlayerDataManager
import org.bukkit.entity.Player
import pumpking.lib.messages.IMessageService

/**
 * [MistakenDeluxe]
 * Interfaz principal de la API.
 */
interface MistakenAPI {
    val plugin: Plugin
    val asesinoManager: IKillerManager
    val sessionManager: ISessionManager
    val configManager: IConfigManager
    val playerDataManager: IPlayerDataManager
    val messages: IMessageService
    val mm: MiniMessage
    val logger: Logger

    fun isIgnored(player: Player): Boolean
}

