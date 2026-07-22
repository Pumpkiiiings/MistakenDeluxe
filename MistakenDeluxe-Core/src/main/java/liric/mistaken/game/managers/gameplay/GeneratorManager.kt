package liric.mistaken.game.managers.gameplay

import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.event.Listener
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualTextDisplay
import org.bukkit.Bukkit
import pumpking.lib.color.ColorTranslator
import pumpking.lib.config.ConfigManager
import pumpking.lib.service.PumpkingServiceManager
import liric.mistaken.game.objectives.ObjectiveType

/**
 * [LIRIC-MISTAKEN 2.0]
 * GeneratorManager: Gesti鏮 de generadores adaptada a MULTIARENA.
 * FIX: M彋odos de conteo por mundo a鎙didos para evitar mezcla de datos entre arenas.
 */
class GeneratorManager(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()

    // Cache en RAM pura para acceso instant嫕eo
    private val generators = ConcurrentHashMap<Location, GeneratorState>()
    private val nameCache = ConcurrentHashMap<Material, String>()

    private var idleLines: List<String> = emptyList()
    private var completedLines: List<String> = emptyList()

    private val configProvider = ConfigManager.get("generator_data.yml")
    private var dataConfig = configProvider.getRaw()
    private val fileLock = Any()

    data class GeneratorState(
        val originalMaterial: Material,
        var progress: Int,
        var completed: Boolean,
        var displayEntity: VirtualTextDisplay? = null,
        var type: ObjectiveType = ObjectiveType.CLASSIC_GENERATOR
    )

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        val langConfig = PumpkingServiceManager.messages.getSpecificFile(null, "messages")

        idleLines = langConfig.getStringList("generators.hologram.lines-idle").ifEmpty {
            listOf("<gold><bold>{name}", "<white>Progreso: <gray>{progress}%", "<yellow>。lick para reparar!")
        }

        completedLines = langConfig.getStringList("generators.hologram.lines-completed").ifEmpty {
            listOf("<green><bold>? ENERG�A RESTAURADA ?", "<gray>、uen trabajo!")
        }

        nameCache.clear()
    }

    private fun getFriendlyName(type: ObjectiveType): String {
        return when (type) {
            ObjectiveType.CLASSIC_GENERATOR -> "Generador"
            ObjectiveType.HACK_TERMINAL -> "Terminal de Hackeo"
            ObjectiveType.KEYPAD_CODE -> "Panel de Código"
        }
    }

    fun prepareArenaGenerators(locations: List<Location>) {
        // ?? MULTIARENA FIX: Ya no usamos clearGenerators() global.
        // Solo limpiamos los que pertenezcan al mundo que estamos cargando ahora.
        val targetWorld = locations.firstOrNull()?.world
        if (targetWorld != null) {
            clearGeneratorsInWorld(targetWorld)
        }

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            synchronized(fileLock) {
                configProvider.load()
                dataConfig = configProvider.getRaw()
            }

            // --- REPARTO DINÁMICO DE OBJETIVOS ---
            val shuffled = locations.shuffled()
            val total = shuffled.size
            val genCount = Math.ceil(total * 0.60).toInt()
            val hackCount = Math.floor(total * 0.30).toInt()
            val codeCount = total - genCount - hackCount

            val assignments = mutableMapOf<Location, ObjectiveType>()
            shuffled.take(genCount).forEach { assignments[it] = ObjectiveType.CLASSIC_GENERATOR }
            shuffled.drop(genCount).take(hackCount).forEach { assignments[it] = ObjectiveType.HACK_TERMINAL }
            shuffled.drop(genCount + hackCount).forEach { assignments[it] = ObjectiveType.KEYPAD_CODE }

            locations.forEach { loc ->
                val blockLoc = loc.block.location
                plugin.server.regionScheduler.execute(plugin, blockLoc, Runnable {
                    val coordKey = "${blockLoc.world.name}_${blockLoc.blockX}_${blockLoc.blockY}_${blockLoc.blockZ}"

                    var savedProgress = 0
                    var isDone = false
                    var objTypeStr = ""

                    synchronized(fileLock) {
                        savedProgress = dataConfig.getInt("session.$coordKey.progress", 0)
                        isDone = dataConfig.getBoolean("session.$coordKey.completed", false)
                        objTypeStr = dataConfig.getString("session.$coordKey.type", "") ?: ""
                    }

                    val objType = if (objTypeStr.isNotEmpty()) {
                        ObjectiveType.valueOf(objTypeStr)
                    } else {
                        assignments[loc] ?: ObjectiveType.CLASSIC_GENERATOR
                    }

                    val requiredMaterial = when (objType) {
                        ObjectiveType.CLASSIC_GENERATOR -> Material.RAW_IRON_BLOCK
                        ObjectiveType.HACK_TERMINAL -> Material.OBSERVER
                        ObjectiveType.KEYPAD_CODE -> Material.AMETHYST_BLOCK
                    }

                    val state = GeneratorState(requiredMaterial, savedProgress, isDone, type = objType)
                    generators[blockLoc] = state

                    blockLoc.block.setType(requiredMaterial, false)
                    if (isDone) blockLoc.block.setType(Material.SEA_LANTERN, false)

                    spawnHologram(blockLoc, state)
                })
            }
        }
    }

    fun addProgress(loc: Location, amount: Int) {
        val blockLoc = loc.block.location
        val state = generators[blockLoc] ?: return
        if (state.completed) return

        val oldProgress = state.progress
        state.progress = (state.progress + amount).coerceIn(0, 100)

        if (state.progress != oldProgress) updateHologramVisual(state)
        if (state.progress >= 100) completeGenerator(blockLoc, state)
    }

    private fun completeGenerator(loc: Location, state: GeneratorState) {
        state.completed = true
        loc.block.setType(Material.SEA_LANTERN, false)
        loc.world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f)

        saveStateToConfigAsync(loc, state)
        updateHologramVisual(state)

        // Buscamos la sesi鏮 del mundo actual para el check de victoria
        val session = plugin.sessionManager.activeSessions.values.find { s ->
            s.getPlayers().any { p -> p.world == loc.world }
        }
        session?.playerController?.checkWinCondition()
    }

    private fun spawnHologram(loc: Location, state: GeneratorState) {
        val holoLoc = loc.clone().add(0.5, 1.3, 0.5)
        plugin.server.regionScheduler.execute(plugin, holoLoc, Runnable {
            state.displayEntity?.remove()
            state.displayEntity = PacketFactory.displays.buildTextDisplay(Bukkit.getOnlinePlayers().toList(), holoLoc) { display ->
                display.billboard = Display.Billboard.CENTER
                display.brightness = Display.Brightness(15, 15)
                display.backgroundColor = Color.fromARGB(0, 0, 0, 0)
                display.isShadowed = true
                display.isPersistent = false
                display.transformation =
                    Transformation(Vector3f(), Quaternionf(), Vector3f(1.1f, 1.1f, 1.1f), Quaternionf())
                updateHologramVisual(state, display)
            }
        })
    }

    private fun updateHologramVisual(state: GeneratorState, directEntity: VirtualTextDisplay? = null) {
        val entity = directEntity ?: state.displayEntity ?: return
        if (entity?.isValid == false) return

        val typeName = getFriendlyName(state.type)
        val lines = if (state.completed) completedLines else idleLines

        val text = lines.joinToString("<newline><reset>") { line ->
            line.replace("{name}", typeName).replace("{progress}", state.progress.toString())
        }
        entity.text = ColorTranslator.translate("<reset>$text")
    }

    private fun saveStateToConfigAsync(loc: Location, state: GeneratorState) {
        val key = "session.${loc.world.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            synchronized(fileLock) {
                dataConfig.set("$key.progress", state.progress)
                dataConfig.set("$key.completed", state.completed)
                dataConfig.set("$key.type", state.type.name)
                try { configProvider.save() } catch (e: Exception) { }
            }
        }
    }

    fun clearGenerators() {
        generators.forEach { (_, state) -> state.displayEntity?.remove() }
        generators.clear()
    }

    // ?? NUEVO: Limpieza selectiva para Multiarena
    fun clearGeneratorsInWorld(world: World) {
        generators.entries.removeIf { (loc, state) ->
            if (loc.world == world) {
                state.displayEntity?.remove()
                true
            } else false
        }
    }

    fun isCompleted(loc: Location) = generators[loc.block.location]?.completed ?: false
    fun getProgress(loc: Location): Int = generators[loc.block.location]?.progress ?: 0

    fun resetGenerators() {
        generators.forEach { (loc, state) ->
            state.progress = 0
            state.completed = false
            plugin.server.regionScheduler.execute(plugin, loc, Runnable {
                loc.block.setType(state.originalMaterial, false)
                updateHologramVisual(state)
            })
        }
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            synchronized(fileLock) {
                dataConfig.set("session", null)
                try { configProvider.save() } catch (e: Exception) { }
            }
        }
    }

    // =========================================================================
    // ?? M仈ODOS CONTEXTUALES PARA MULTIARENA
    // =========================================================================

    /**
     * Cuenta cu嫕tos generadores han sido completados en un mundo espec璗ico.
     */
    fun getCompletedCountInWorld(world: World): Int {
        return generators.entries.count { (loc, state) ->
            loc.world == world && state.completed
        }
    }

    /**
     * Devuelve el total de generadores registrados en un mundo espec璗ico.
     */
    fun getTotalGeneratorsInWorld(world: World): Int {
        return generators.keys.count { it.world == world }
    }

    fun getCompletedCount(): Int = generators.values.count { it.completed }
    fun getTotalGenerators(): Int = generators.size
    fun getGeneratorLocations(): List<Location> = generators.keys.toList()
}






