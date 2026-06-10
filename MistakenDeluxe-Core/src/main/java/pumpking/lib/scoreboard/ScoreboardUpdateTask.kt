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
                // Prefer dynamic template (live game data) over static template
                val dynamicId = context.dynamicTemplateId
                val staticId = context.templateId

                when {
                    dynamicId != null -> {
                        val dynTemplate = ScoreboardManager.getDynamicTemplate(dynamicId) ?: continue
                        // Resolve live data into a static snapshot, then render normally
                        val snapshot = ScoreboardTemplate(
                            id = dynTemplate.id,
                            title = dynTemplate.titleSupplier(player),
                            lines = dynTemplate.linesSupplier(player),
                            animatedTitle = dynTemplate.animatedTitle
                        )
                        renderer.render(player, context, snapshot)
                    }
                    staticId != null -> {
                        val template = ScoreboardManager.getTemplate(staticId) ?: continue
                        renderer.render(player, context, template)
                    }
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
