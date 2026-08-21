package liric.mistaken.api.managers

import liric.mistaken.game.enums.GameState
import java.util.UUID

interface ISession {
    val id: String
    var currentState: GameState
    val killersUUIDs: Set<UUID>

    fun isKiller(uuid: UUID): Boolean

    fun forceStart()
    fun forceEnd(killerWon: Boolean)

    val survivorsUUIDs: Set<UUID>
    val aliveSurvivorsUUIDs: Set<UUID>
    val spectatorsUUIDs: Set<UUID>
}
