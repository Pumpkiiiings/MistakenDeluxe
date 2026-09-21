package liric.mistaken.level.manager

import java.util.UUID

object MatchStatsTracker {
    val stats = HashMap<UUID, MutableMap<String, Int>>()

    fun addStat(uuid: UUID, type: String, amount: Int = 1) {
        stats.computeIfAbsent(uuid) { mutableMapOf() }
        val current = stats[uuid]?.get(type) ?: 0
        stats[uuid]!![type] = current + amount
    }

    fun getStats(uuid: UUID): Map<String, Int> {
        return stats[uuid] ?: emptyMap()
    }
    
    fun clear() {
        stats.clear()
    }
}
