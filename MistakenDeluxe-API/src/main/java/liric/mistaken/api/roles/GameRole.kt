package liric.mistaken.api.roles

import org.bukkit.entity.Player

/**
 * Interfaz base para unificar Killer y Survivor en AbstractRoleManager.
 */
interface GameRole {
    val id: String
    val nombre: String
    fun equip(player: Player)
    fun cleanup(player: Player?)
}
