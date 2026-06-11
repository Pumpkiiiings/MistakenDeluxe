package pumpking.lib.animation

import org.bukkit.entity.Player

/**
 * Base abstract class for a Frame Animation.
 */
abstract class FrameAnimation(
    val id: String,
    val frames: List<String>,
    private val tickInterval: Int = 1 // how many engine ticks before advancing frame
) {
    private var currentTick = 0
    private var currentFrameIndex = 0

    fun tick() {
        currentTick++
        if (currentTick >= tickInterval) {
            currentTick = 0
            currentFrameIndex++
            if (currentFrameIndex >= frames.size) {
                currentFrameIndex = 0
            }
        }
    }

    fun getCurrentFrame(): String {
        if (frames.isEmpty()) return ""
        return frames[currentFrameIndex]
    }

    /**
     * Define how this animation applies to the player (e.g. update BossBar, ActionBar, Scoreboard).
     */
    abstract fun applyTo(player: Player)
}
