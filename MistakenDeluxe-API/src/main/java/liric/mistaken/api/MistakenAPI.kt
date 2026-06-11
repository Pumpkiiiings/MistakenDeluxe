package liric.mistaken.api

import liric.mistaken.api.managers.IAsesinoManager
import liric.mistaken.api.managers.ISessionManager
import liric.mistaken.api.managers.IConfigManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.Plugin
import java.util.logging.Logger

/**
 * [MistakenDeluxe]
 * Interfaz principal de la API.
 */
interface MistakenAPI {
    val plugin: Plugin
    val asesinoManager: IAsesinoManager
    val sessionManager: ISessionManager
    val configManager: IConfigManager
    val mm: MiniMessage
    val logger: Logger
    
    fun isIgnored(player: org.bukkit.entity.Player): Boolean
}

