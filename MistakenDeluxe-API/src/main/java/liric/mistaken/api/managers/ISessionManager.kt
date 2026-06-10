package liric.mistaken.api.managers

import org.bukkit.entity.Player
import java.util.UUID

interface ISessionManager {
    fun getSession(player: Player): ISession?
    fun getSession(uuid: UUID): ISession?
}
