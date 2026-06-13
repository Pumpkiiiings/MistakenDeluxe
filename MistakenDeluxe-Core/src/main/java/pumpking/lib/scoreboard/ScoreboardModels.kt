package pumpking.lib.scoreboard

import net.kyori.adventure.text.Component
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard

/**
 * Static template: title and lines are fixed strings (may contain placeholders like %player%).
 */
data class ScoreboardTemplate(
    val id: String,
    val title: String,
    val lines: List<String>,
    val animatedTitle: Boolean = false
) {
    // Pre-parse the dependency of each line when the template is created
    val isLineDynamic: BooleanArray = BooleanArray(lines.size) { i ->
        val line = lines[i]
        line.contains("%") || line.contains("<anim>")
    }
}

class ScoreboardContext(
    val scoreboard: Scoreboard,
    val objective: Objective,
    var templateId: String? = null
) {
    // Initialization State
    var initialized = false
    var activeLines = 0
    
    // Dirty Flags
    var titleChanged = true
    var layoutChanged = true
    val lineChanged = BooleanArray(15) { true }
    
    fun markAllClean() {
        titleChanged = false
        layoutChanged = false
        for (i in 0 until 15) {
            lineChanged[i] = false
        }
    }
    
    fun isDirty(): Boolean {
        if (titleChanged || layoutChanged) return true
        for (i in 0 until activeLines) {
            if (lineChanged[i]) return true
        }
        return false
    }

    // Cache Arrays (Zero Allocations in render loop)
    val lineCache = arrayOfNulls<String>(15)
    var titleCache: String? = null
    
    // Static parsed components for lines that never change
    val staticLineCache = arrayOfNulls<Component>(15)
    
    // Animation specific caching
    var strippedTitleCache: String? = null
    val strippedLineCache = arrayOfNulls<String>(15)
    var animTick = 0
}
