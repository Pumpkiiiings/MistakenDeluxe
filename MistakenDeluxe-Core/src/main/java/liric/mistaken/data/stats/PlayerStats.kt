package liric.mistaken.data.stats

import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicInteger


class PlayerStats {

    // Contadores atómicos para alto rendimiento y seguridad entre hilos
    val winsSurvivor = AtomicInteger(0)
    val winsAssassin = AtomicInteger(0)
    val lossesSurvivor = AtomicInteger(0)
    val lossesAssassin = AtomicInteger(0)
    val deaths = AtomicInteger(0)
    val kills = AtomicInteger(0)
    val generatorsRepaired = AtomicInteger(0)

    companion object {
        // Objeto estático para formateo, evita instanciar uno por cada player (Ahorro de RAM)
        private val df = DecimalFormat("#.##")
    }

    /**
     * Obtiene el valor de una estadística mediante su identificador String.
     * Optimizado con el operador 'when' de Kotlin.
     */
    fun getStatValue(statName: String): Int {
        return when (statName.lowercase()) {
            "wins_survivor" -> winsSurvivor.get()
            "wins_assassin" -> winsAssassin.get()
            "losses_survivor" -> lossesSurvivor.get()
            "losses_assassin" -> lossesAssassin.get()
            "kills" -> kills.get()
            "deaths" -> deaths.get()
            "generators_repaired" -> generatorsRepaired.get()
            else -> 0
        }
    }

    /**
     * Incrementa una estadística por nombre de forma segura.
     */
    fun incrementStat(statName: String, amount: Int = 1) {
        when (statName.lowercase()) {
            "wins_survivor" -> winsSurvivor.addAndGet(amount)
            "wins_assassin" -> winsAssassin.addAndGet(amount)
            "losses_survivor" -> lossesSurvivor.addAndGet(amount)
            "losses_assassin" -> lossesAssassin.addAndGet(amount)
            "kills" -> kills.addAndGet(amount)
            "deaths" -> deaths.addAndGet(amount)
            "generators_repaired" -> generatorsRepaired.addAndGet(amount)
        }
    }

    // --- LÓGICA DE CÁLCULO ---

    /**
     * Calcula el KDR (Kill/Death Ratio).
     * @return 0.0 si no hay muertes para evitar división por cero.
     */
    fun getKDR(): Double {
        val d = deaths.get()
        val k = kills.get()
        return if (d == 0) k.toDouble() else k.toDouble() / d
    }

    /**
     * KDR formateado a dos decimales.
     */
    val formattedKDR: String
        get() = df.format(getKDR())

    /**
     * Suma total de victorias.
     */
    val totalWins: Int
        get() = winsSurvivor.get() + winsAssassin.get()

    /**
     * Suma total de derrotas.
     */
    val totalLosses: Int
        get() = lossesSurvivor.get() + lossesAssassin.get()

    /**
     * Total de partidas jugadas.
     */
    val gamesPlayed: Int
        get() = totalWins + totalLosses

    // --- MÉTODOS DE UTILIDAD PARA DB ---

    /**
     * Carga masiva de datos (usado al entrar al servidor).
     */
    fun load(ws: Int, wa: Int, ls: Int, la: Int, k: Int, d: Int, gr: Int = 0) {
        winsSurvivor.set(ws)
        winsAssassin.set(wa)
        lossesSurvivor.set(ls)
        lossesAssassin.set(la)
        kills.set(k)
        deaths.set(d)
        generatorsRepaired.set(gr)
    }
}