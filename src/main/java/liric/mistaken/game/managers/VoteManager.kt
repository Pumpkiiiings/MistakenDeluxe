package liric.mistaken.game.managers

import liric.mistaken.game.Arena
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0]
 * VoteManager: Gestión de votaciones reactiva y de alto rendimiento.
 * Optimizado para minimizar la creación de objetos innecesarios.
 */
class VoteManager {

    // ConcurrentHashMap permite que el hilo del menú (Triumph-GUI) y el hilo principal
    // interactúen sin causar excepciones de concurrencia.
    private val votes = ConcurrentHashMap<UUID, String>()

    /**
     * Registra o actualiza el voto de un jugador.
     */
    fun addVote(uuid: UUID, mapName: String) {
        votes[uuid] = mapName
    }

    /**
     * Calcula el mapa ganador con resolución de empates optimizada.
     */
    fun getWinningMap(arenas: Map<String, Arena>): String? {
        if (arenas.isEmpty()) return null

        val random = ThreadLocalRandom.current()

        // Si nadie votó, selección aleatoria directa desde las llaves del mapa de arenas
        if (votes.isEmpty()) {
            val mapNames = arenas.keys.toList()
            return mapNames[random.nextInt(mapNames.size)]
        }

        // 1. Contar votos: Filtramos mapas válidos y agrupamos
        // .values devuelve una vista, .filter y .groupingBy son muy eficientes en Kotlin
        val tally = votes.values
            .filter { arenas.containsKey(it) }
            .groupingBy { it }
            .eachCount() // Retorna Map<String, Int>

        if (tally.isEmpty()) {
            return arenas.keys.toList().let { it[random.nextInt(it.size)] }
        }

        // 2. Encontrar el valor máximo de votos
        val maxVotes = tally.maxOf { it.value }

        // 3. Obtener todos los mapas que alcanzaron ese máximo (para empates)
        val winners = tally.filterValues { it == maxVotes }.keys.toList()

        // 4. Retornar ganador (uno solo o uno al azar si hay empate)
        return if (winners.size > 1) {
            winners[random.nextInt(winners.size)]
        } else {
            winners[0]
        }
    }

    /**
     * Obtiene el conteo actual de un mapa específico.
     * Útil para actualizar GUIs o Scoreboards en tiempo real.
     */
    fun getVotesForMap(mapName: String): Int {
        if (votes.isEmpty()) return 0
        // count { ... } es una función inline, extremadamente rápida
        return votes.values.count { it.equals(mapName, ignoreCase = true) }
    }

    /**
     * Limpia los datos de la votación para la siguiente partida.
     */
    fun resetVotes() {
        votes.clear()
    }

    /**
     * Verifica si un jugador ya emitió su voto.
     */
    fun hasVoted(uuid: UUID): Boolean = votes.containsKey(uuid)

    /**
     * Retorna la cantidad total de votos emitidos.
     */
    fun getTotalVotes(): Int = votes.size
}
