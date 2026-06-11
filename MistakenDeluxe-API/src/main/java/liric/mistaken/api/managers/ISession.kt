package liric.mistaken.api.managers

import liric.mistaken.game.enums.GameState
import java.util.UUID

interface ISession {
    val id: String
    var currentState: GameState
    val asesinosUUIDs: Set<UUID>

    fun esAsesino(uuid: UUID): Boolean
}
