package liric.mistaken.api.managers

import java.util.UUID

interface IPlayerDataManager {
    fun hasKiller(uuid: UUID, killerId: String): Boolean
    fun buyKiller(uuid: UUID, killerId: String)
    fun tieneSurvivor(uuid: UUID, survivorId: String): Boolean
    fun comprarSurvivor(uuid: UUID, survivorId: String)
}
