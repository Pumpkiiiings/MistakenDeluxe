package pumpking.lib.scoreboard

/**
 * Precompiled constants to eliminate String.format() and allocation overhead
 * during the scoreboard render loop.
 */
object ScoreboardConstants {

    /**
     * Precompiled team names from 0 to 14: "sb_line_00", "sb_line_01", etc.
     */
    val TEAM_NAMES: Array<String> = Array(15) { i ->
        "sb_line_${i.toString().padStart(2, '0')}"
    }

    /**
     * Precompiled entry names (invisible color codes) from 0 to 14.
     */
    val ENTRY_NAMES: Array<String> = arrayOf(
        "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r", "§7§r",
        "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    )
}
