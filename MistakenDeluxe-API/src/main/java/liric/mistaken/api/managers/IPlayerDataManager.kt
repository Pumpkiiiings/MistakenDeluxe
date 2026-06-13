package liric.mistaken.api.managers

import java.util.UUID

interface IPlayerDataManager {
    fun tieneAsesino(uuid: UUID, killerId: String): Boolean
    fun comprarAsesino(uuid: UUID, killerId: String)
    fun tieneSuperviviente(uuid: UUID, survivorId: String): Boolean
    fun comprarSuperviviente(uuid: UUID, survivorId: String)
}
