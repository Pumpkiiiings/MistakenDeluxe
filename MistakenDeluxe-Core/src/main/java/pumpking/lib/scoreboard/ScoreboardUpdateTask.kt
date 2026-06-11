package pumpking.lib.scoreboard

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import pumpking.lib.core.PumpkingLib

class ScoreboardUpdateTask : BukkitRunnable() {
    override fun run() {
        val renderer = ScoreboardManager.getRenderer()

        for (player in Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId
            val context = ScoreboardManager.getContext(uuid) ?: continue

            try {
                val staticId = context.templateId

                if (staticId != null) {
                    val template = ScoreboardManager.getTemplate(staticId) ?: continue
                    renderer.render(player, context, template)
                }
            } catch (e: Exception) {
                PumpkingLib.logError(
                    PumpkingLib.LogCategory.SCOREBOARD,
                    "FAIL SAFE ERROR rendering for ${player.name}: ${e.message}"
                )
            }
        }
    }
}
