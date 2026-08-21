package liric.mistaken.roles.common.triggers.traps

import org.bukkit.Location

data class BlockKey(val worldUid: java.util.UUID, val x: Int, val y: Int, val z: Int) {
    companion object {
        fun fromLocation(loc: Location): BlockKey {
            return BlockKey(loc.world!!.uid, loc.blockX, loc.blockY, loc.blockZ)
        }
    }
}
