package liric.mistaken.game.managers.engine

import liric.mistaken.game.Arena
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 *[LIRIC-MISTAKEN 2.0]
 * VoteManager: Gestión de votaciones reactiva y de alto rendimiento.
 */
class VoteManager {

    private val votes = ConcurrentHashMap<UUID, String>()

    fun addVote(uuid: UUID, mapName: String) {
        votes[uuid] = mapName
    }

    fun getWinningMap(arenas: Map<String, Arena>): String? {
        if (arenas.isEmpty()) return null

        // 🔥 MEJORA: .random() de Kotlin es más limpio que ThreadLocalRandom.nextInt
        if (votes.isEmpty()) return arenas.keys.random()

        val tally = votes.values
            .filter { arenas.containsKey(it) }
            .groupingBy { it }
            .eachCount()

        if (tally.isEmpty()) return arenas.keys.random()

        val maxVotes = tally.maxOf { it.value }
        val winners = tally.filterValues { it == maxVotes }.keys

        // Retorna el ganador directo, o elige uno al azar si hay empate
        return winners.random()
    }

    fun getVotesForMap(mapName: String): Int {
        if (votes.isEmpty()) return 0
        return votes.values.count { it.equals(mapName, ignoreCase = true) }
    }

    fun resetVotes() {
        votes.clear()
    }

    fun hasVoted(uuid: UUID): Boolean = votes.containsKey(uuid)

    fun getTotalVotes(): Int = votes.size

    // 🔥 NUEVO: Función para limpiar la memoria si el jugador se desconecta en pleno lobby
    fun removeVote(uuid: UUID) {
        votes.remove(uuid)
    }
}