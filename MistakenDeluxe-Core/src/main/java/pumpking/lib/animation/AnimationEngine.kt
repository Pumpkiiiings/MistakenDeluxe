package pumpking.lib.animation

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import pumpking.lib.core.PumpkingLib
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Core Animation Engine for Pumpking Framework.
 * Ticks all registered animations and applies them to their bound targets.
 */
class AnimationEngine(private val plugin: JavaPlugin) {

    private val animations = ConcurrentHashMap<String, FrameAnimation>()
    private val activePlayers = ConcurrentHashMap<UUID, MutableSet<String>>()
    private var taskId: Int = -1

    fun init() {
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { tick() }, 0L, 2L).taskId
        PumpkingLib.log(PumpkingLib.LogCategory.CORE, "[Animation] Engine initialized.")
    }

    fun shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId)
        }
        animations.clear()
        activePlayers.clear()
    }

    fun register(id: String, animation: FrameAnimation) {
        animations[id] = animation
    }

    fun unregister(id: String) {
        animations.remove(id)
        activePlayers.values.forEach { it.remove(id) }
    }

    fun play(player: Player, animationId: String) {
        activePlayers.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }.add(animationId)
    }

    fun stop(player: Player, animationId: String) {
        activePlayers[player.uniqueId]?.remove(animationId)
    }

    fun stopAll(player: Player) {
        activePlayers.remove(player.uniqueId)
    }

    private fun tick() {
        animations.values.forEach { it.tick() }

        // Process players
        for ((uuid, activeAnims) in activePlayers) {
            val player = Bukkit.getPlayer(uuid)
            if (player == null || !player.isOnline) {
                activePlayers.remove(uuid)
                continue
            }

            for (animId in activeAnims) {
                val animation = animations[animId] ?: continue
                animation.applyTo(player)
            }
        }
    }
}
