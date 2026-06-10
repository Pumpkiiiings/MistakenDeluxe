package pumpking.lib.scoreboard

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import pumpking.lib.core.PumpkingLib
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ScoreboardManager {

    private val contexts = ConcurrentHashMap<UUID, ScoreboardContext>()

    // Static templates (fixed strings, resolved by renderer)
    private val templates = ConcurrentHashMap<String, ScoreboardTemplate>()

    // Dynamic templates (live-data lambdas resolved per-player per-tick)
    private val dynamicTemplates = ConcurrentHashMap<String, DynamicScoreboardTemplate>()

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
        dynamicTemplates.clear()
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
        templates[template.id] = template
    }

    fun getTemplate(id: String): ScoreboardTemplate? = templates[id]

    // --- Dynamic Template Management ---

    fun registerDynamicTemplate(template: DynamicScoreboardTemplate) {
        dynamicTemplates[template.id] = template
    }

    fun getDynamicTemplate(id: String): DynamicScoreboardTemplate? = dynamicTemplates[id]

    // --- Scoreboard Assignment ---

    fun assignScoreboard(player: Player, templateId: String) {
        val uuid = player.uniqueId
        val context = ensureContext(player)
        context.templateId = templateId
        context.dynamicTemplateId = null
        renderer.clearCache(uuid)
    }

    fun assignDynamicScoreboard(player: Player, templateId: String) {
        val uuid = player.uniqueId
        val context = ensureContext(player)
        context.dynamicTemplateId = templateId
        context.templateId = null
        renderer.clearCache(uuid)
    }

    fun removeScoreboard(player: Player) {
        val uuid = player.uniqueId
        contexts.remove(uuid)
        renderer.clearCache(uuid)
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }

    private fun ensureContext(player: Player): ScoreboardContext {
        val uuid = player.uniqueId
        return contexts.getOrPut(uuid) {
            val scoreboard = Bukkit.getScoreboardManager().newScoreboard
            val objective = scoreboard.registerNewObjective(
                "pumpking_sb",
                Criteria.DUMMY,
                net.kyori.adventure.text.Component.empty()
            )
            objective.displaySlot = DisplaySlot.SIDEBAR
            val ctx = ScoreboardContext(scoreboard, objective)
            player.scoreboard = scoreboard
            ctx
        }
    }

    // --- Internal ---

    internal fun getContext(uuid: UUID): ScoreboardContext? = contexts[uuid]

    internal fun getRenderer(): IScoreboardRenderer = renderer
}
