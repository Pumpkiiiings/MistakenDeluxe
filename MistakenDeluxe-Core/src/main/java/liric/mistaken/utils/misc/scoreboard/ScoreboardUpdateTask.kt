package liric.mistaken.utils.scoreboard

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import liric.mistaken.MistakenLib

class ScoreboardUpdateTask : BukkitRunnable() {
    private var tickCount = 0

    override fun run() {
        val renderer = ScoreboardManager.getRenderer()
        val supportsAnimations = ScoreboardManager.supportsAnimations()

        for (player in Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId
            val context = ScoreboardManager.getContext(uuid) ?: continue

            val templateId = context.templateId ?: continue
            val template = ScoreboardManager.getTemplate(templateId) ?: continue

            try {
                
                if (supportsAnimations) {
                    context.animTick++
                    if (template.animatedTitle) {
                        context.titleChanged = true
                    }
                    for (i in 0 until template.lines.size) {
                        if (template.isLineDynamic[i]) {
                            context.lineChanged[i] = true
                        }
                    }
                }

                if (!context.isDirty() && context.initialized) {
                    ScoreboardProfiler.recordSkip()
                    continue
                }

                val startNs = System.nanoTime()
                
                renderer.render(player, context, template)
                
                
                context.markAllClean()

                val elapsedNs = System.nanoTime() - startNs
                ScoreboardProfiler.recordRender(elapsedNs)

            } catch (e: Exception) {
                MistakenLib.logError(
                    MistakenLib.LogCategory.SCOREBOARD,
                    "FAIL SAFE ERROR rendering for ${player.name}: ${e.message}"
                )
            }
        }
    }
}
