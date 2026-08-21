package liric.mistaken.roles.common.triggers.traps

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

data class TrapDefinition(
    val ownerUuid: UUID,
    val killerId: String,
    val location: Location,
    val onTrigger: (Player, Location) -> Unit
)
