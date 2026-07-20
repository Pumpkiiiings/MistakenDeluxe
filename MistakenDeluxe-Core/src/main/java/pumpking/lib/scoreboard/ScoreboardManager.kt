package pumpking.lib.scoreboard

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import pumpking.lib.core.PumpkingLib
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import pumpking.lib.color.ColorTranslator

object ScoreboardManager {

    private val contexts = ConcurrentHashMap<UUID, ScoreboardContext>()

    // Static templates (fixed strings, resolved by renderer)
    private val templates = ConcurrentHashMap<String, ScoreboardTemplate>()


    private var updateTask: ScoreboardUpdateTask? = null

    // Active renderer - selected at init time
    private lateinit var renderer: IScoreboardRenderer

    // --- Capability Flags ---

    /** Returns true if the active renderer supports animated RGB gradients and titles. */
    fun supportsAnimations(): Boolean = if (::renderer.isInitialized) renderer.supportsAnimations else false

    /** Returns true if the active renderer supports packet-level rendering optimizations. */
    fun supportsAdvancedRendering(): Boolean = if (::renderer.isInitialized) renderer.supportsAdvancedRendering else false

    // --- Lifecycle ---

    fun init(plugin: JavaPlugin) {
        renderer = detectRenderer()
        updateTask = ScoreboardUpdateTask()
        val interval = if (supportsAnimations()) 2L else 10L
        updateTask!!.runTaskTimer(plugin, 10L, interval)
    }

    fun shutdown() {
        updateTask?.cancel()
        contexts.clear()
        templates.clear()
    }

    // --- Renderer Detection ---

    private fun detectRenderer(): IScoreboardRenderer {
        val hasPacketEvents = runCatching {
            Bukkit.getPluginManager().isPluginEnabled("packetevents")
        }.getOrElse { false }

        return if (hasPacketEvents) {
            PumpkingLib.log(PumpkingLib.LogCategory.SCOREBOARD, "PacketEvents detected.")
            PumpkingLib.log(PumpkingLib.LogCategory.SCOREBOARD, "Advanced renderer enabled.")
            PacketEventsRenderer()
        } else {
            PumpkingLib.log(PumpkingLib.LogCategory.SCOREBOARD, "PacketEvents not detected.")
            PumpkingLib.log(PumpkingLib.LogCategory.SCOREBOARD, "Falling back to Bukkit renderer.")
            PumpkingLib.log(PumpkingLib.LogCategory.SCOREBOARD, "Advanced animations disabled.")
            BukkitRenderer()
        }
    }

    // --- Static Template Management ---

    fun registerTemplate(template: ScoreboardTemplate) {
        val old = templates.put(template.id, template)
        for (context in contexts.values) {
            if (context.templateId == template.id) {
                if (old == null || old.title != template.title || old.animatedTitle != template.animatedTitle) {
                    context.titleChanged = true
                }
                if (old == null || old.lines.size != template.lines.size) {
                    context.layoutChanged = true
                }
                for (i in template.lines.indices) {
                    if (old == null || i >= old.lines.size || old.lines[i] != template.lines[i]) {
                        context.lineChanged[i] = true
                        if (!template.isLineDynamic[i]) {
                            context.staticLineCache[i] = ColorTranslator.translate(template.lines[i])
                        } else {
                            context.staticLineCache[i] = null
                        }
                    }
                }
            }
        }
    }

    fun getTemplate(id: String): ScoreboardTemplate? = templates[id]


    // --- Scoreboard Assignment ---

    fun assignScoreboard(player: Player, templateId: String) {
        val uuid = player.uniqueId
        val context = ensureContext(player)
        
        if (context.templateId != templateId) {
            context.templateId = templateId
            context.initialized = false
            context.activeLines = 0
            context.markAllClean()
            context.layoutChanged = true
            context.titleChanged = true
            
            // Re-populate static cache for the new template
            val template = getTemplate(templateId)
            if (template != null) {
                for (i in template.lines.indices) {
                    if (!template.isLineDynamic[i]) {
                        context.staticLineCache[i] = ColorTranslator.translate(template.lines[i])
                    } else {
                        context.staticLineCache[i] = null
                    }
                    context.lineChanged[i] = true
                }
            }
        }
    }

    fun removeScoreboard(player: Player) {
        val uuid = player.uniqueId
        contexts.remove(uuid)
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }

    private fun ensureContext(player: Player): ScoreboardContext {
        val uuid = player.uniqueId
        return contexts.getOrPut(uuid) {
            val scoreboard = Bukkit.getScoreboardManager().newScoreboard
            val objective = scoreboard.registerNewObjective(
                "pumpking_sb",
                Criteria.DUMMY,
                Component.empty()
            )
            objective.displaySlot = DisplaySlot.SIDEBAR
            try {
                objective.numberFormat(NumberFormat.blank())
            } catch (ignored: Throwable) {}

            val ctx = ScoreboardContext(scoreboard, objective)
            player.scoreboard = scoreboard
            ctx
        }
    }

    // --- Internal ---

    internal fun getContext(uuid: UUID): ScoreboardContext? = contexts[uuid]

    // FIX #5: Guard against calling getRenderer() before init() (possible on /reload).
    internal fun getRenderer(): IScoreboardRenderer {
        check(::renderer.isInitialized) {
            "ScoreboardManager.getRenderer() called before init(). Make sure PumpkingLib.init() runs in onEnable()."
        }
        return renderer
    }
}
