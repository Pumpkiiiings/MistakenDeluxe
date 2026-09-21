package liric.mistaken.game.managers.gameplay

import liric.mistaken.utils.worldViewers
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
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.ConfigManager
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.game.objectives.ObjectiveType

/**
 * [LIRIC-MISTAKEN 2.0]
 * GeneratorManager: Gesti鏮 de generadores adaptada a MULTIARENA.
 * FIX: M彋odos de conteo por world a鎙didos para evitar mezcla de datos entre arenas.
 */
class GeneratorManager(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()

    
    private val generators = ConcurrentHashMap<String, GeneratorState>()
    private val nameCache = ConcurrentHashMap<Material, String>()

    private var idleLines: List<String> = emptyList()
    private var completedLines: List<String> = emptyList()

    data class GeneratorState(
        val originalMaterial: Material,
        var progress: Int,
        var completed: Boolean,
        var displayEntity: VirtualTextDisplay? = null,
        var type: ObjectiveType = ObjectiveType.CLASSIC_GENERATOR,
        val location: Location
    )

    init {
        loadTemplates()
    }
    
    private fun toKey(loc: Location): String = "${loc.world?.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"

    fun loadTemplates() {
        val langConfig = MessageService.getSpecificFile(null, "messages")

        idleLines = langConfig.getStringList("generators.hologram.lines-idle").ifEmpty {
            listOf("<gold><bold>{name}", "<white>Progreso: <gray>{progress}%", "<yellow>?lick para reparar!")
        }

        completedLines = langConfig.getStringList("generators.hologram.lines-completed").ifEmpty {
            listOf("<green><bold>? ENERG?A RESTAURADA ?", "<gray>?uen trabajo!")
        }

        nameCache.clear()
    }

    private fun getFriendlyName(type: ObjectiveType): String {
        return when (type) {
            ObjectiveType.CLASSIC_GENERATOR -> "Generador"
            ObjectiveType.HACK_TERMINAL -> "Terminal de Hackeo"
            ObjectiveType.KEYPAD_CODE -> "Panel de Cdigo"
        }
    }

    fun prepareArenaGenerators(locations: List<Location>) {
        
        
        val targetWorld = locations.firstOrNull()?.world
        if (targetWorld != null) {
            clearGeneratorsInWorld(targetWorld)
        }

        plugin.server.asyncScheduler.runNow(plugin) { _ ->

            
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
                    val key = toKey(blockLoc)

                    val objType = assignments[loc] ?: ObjectiveType.CLASSIC_GENERATOR

                    val requiredMaterial = when (objType) {
                        ObjectiveType.CLASSIC_GENERATOR -> Material.RAW_IRON_BLOCK
                        ObjectiveType.HACK_TERMINAL -> Material.OBSERVER
                        ObjectiveType.KEYPAD_CODE -> Material.AMETHYST_BLOCK
                    }

                    val state = GeneratorState(requiredMaterial, 0, false, type = objType, location = blockLoc)
                    generators[key] = state

                    blockLoc.block.setType(requiredMaterial, false)

                    spawnHologram(blockLoc, state)
                })
            }
        }
    }

    fun addProgress(loc: Location, amount: Int) {
        val key = toKey(loc)
        val state = generators[key] ?: return
        if (state.completed) return

        val oldProgress = state.progress
        state.progress = (state.progress + amount).coerceIn(0, 100)

        if (state.progress != oldProgress) updateHologramVisual(state)
        if (state.progress >= 100) completeGenerator(state.location, state)
    }

    private fun completeGenerator(loc: Location, state: GeneratorState) {
        state.completed = true
        loc.block.setType(Material.SEA_LANTERN, false)
        loc.world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f)

        updateHologramVisual(state)

        
        val session = plugin.sessionManager.activeSessions.values.find { s ->
            s.getPlayers().any { p -> p.world == loc.world }
        }
        session?.playerController?.checkWinCondition()
    }

    private fun spawnHologram(loc: Location, state: GeneratorState) {
        val holoLoc = loc.clone().add(0.5, 1.3, 0.5)
        plugin.server.regionScheduler.execute(plugin, holoLoc, Runnable {
            state.displayEntity?.remove()
            state.displayEntity = PacketFactory.displays.buildTextDisplay(holoLoc.worldViewers(), holoLoc) { display ->
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
        if (!entity.isValid) return

        val typeName = getFriendlyName(state.type)
        val lines = if (state.completed) completedLines else idleLines

        val text = lines.joinToString("<newline><reset>") { line ->
            line.replace("{name}", typeName).replace("{progress}", state.progress.toString())
        }
        entity.text = ColorTranslator.translate("<reset>$text")
    }

    fun clearGenerators() {
        generators.forEach { (_, state) -> state.displayEntity?.remove() }
        generators.clear()
    }

    
    fun clearGeneratorsInWorld(world: World) {
        generators.entries.removeIf { (_, state) ->
            if (state.location.world?.name == world.name) {
                state.displayEntity?.remove()
                true
            } else false
        }
    }

    fun isCompleted(loc: Location) = generators[toKey(loc)]?.completed ?: false
    fun getProgress(loc: Location): Int = generators[toKey(loc)]?.progress ?: 0

    fun resetGenerators() {
        generators.forEach { (_, state) ->
            state.progress = 0
            state.completed = false
            plugin.server.regionScheduler.execute(plugin, state.location, Runnable {
                state.location.block.setType(state.originalMaterial, false)
                updateHologramVisual(state)
            })
        }
    }

    
    
    

    /**
     * Cuenta cu?tos generadores han sido completados en un world espec?ico.
     */
    fun getCompletedCountInWorld(world: World): Int {
        return generators.values.count { state ->
            state.location.world?.name == world.name && state.completed
        }
    }

    /**
     * Devuelve el total de generadores registrados en un world espec?ico.
     */
    fun getTotalGeneratorsInWorld(world: World): Int {
        return generators.values.count { it.location.world?.name == world.name }
    }

    fun getCompletedCount(): Int = generators.values.count { it.completed }
    fun getTotalGenerators(): Int = generators.size
    fun getGeneratorLocations(): List<Location> = generators.values.map { it.location }.toList()
}
