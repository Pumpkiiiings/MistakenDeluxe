package liric.mistaken.level.database

import java.util.UUID

data class PlayerLevelData(
    val uuid: UUID,
    var level: Int,
    var experience: Long,
    var totalExperience: Long,
    var prestige: Int
)
