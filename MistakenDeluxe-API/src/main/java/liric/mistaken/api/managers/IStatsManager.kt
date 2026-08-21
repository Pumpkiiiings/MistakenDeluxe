package liric.mistaken.api.managers

import java.util.UUID

interface IStatsManager {
    fun incrementStat(uuid: UUID, column: String, amount: Int = 1)
    fun getStat(uuid: UUID, statName: String): Int
}
