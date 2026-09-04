package liric.mistaken.data.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerStatsTest {

    @Test
    fun `increments known statistics case insensitively`() {
        val stats = PlayerStats()

        stats.incrementStat("KILLS", 3)
        stats.incrementStat("deaths", 2)

        assertEquals(3, stats.getStatValue("kills"))
        assertEquals(2, stats.getStatValue("DEATHS"))
        assertEquals(1.5, stats.getKDR())
    }

    @Test
    fun `calculates aggregates from loaded values`() {
        val stats = PlayerStats()

        stats.load(ws = 2, wa = 3, ls = 4, la = 5, k = 6, d = 0, gr = 7, c = 8)

        assertEquals(5, stats.totalWins)
        assertEquals(9, stats.totalLosses)
        assertEquals(14, stats.gamesPlayed)
        assertEquals(6.0, stats.getKDR())
        assertEquals(7, stats.generatorsRepaired.get())
        assertEquals(8, stats.coins.get())
    }

    @Test
    fun `unknown statistics are ignored`() {
        val stats = PlayerStats()

        stats.incrementStat("not-a-stat", 10)

        assertEquals(0, stats.getStatValue("not-a-stat"))
        assertEquals(0, stats.gamesPlayed)
    }
}
