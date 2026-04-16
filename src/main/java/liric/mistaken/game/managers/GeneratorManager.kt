package liric.mistaken.game.managers

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

/**
 * [LIRIC-MISTAKEN 2.0]
 * GeneratorManager: Gestión optimizada de generadores y hologramas.
 */
class GeneratorManager(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val generators = ConcurrentHashMap<Location, GeneratorState>()
    private val nameCache = ConcurrentHashMap<Material, String>()

    private var idleLines: List<String> = emptyList()
    private var completedLines: List<String> = emptyList()

    private val dataFile = File(plugin.dataFolder, "generator_data.yml")
    private var dataConfig = YamlConfiguration()
    private val fileLock = Any()

    data class GeneratorState(
        val originalMaterial: Material,
        var progress: Int,
        var completed: Boolean,
        var displayEntity: TextDisplay? = null
    )

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        val langConfig = plugin.messageConfig.getSpecificFile(null, "messages")

        idleLines = langConfig.getStringList("generators.hologram.lines-idle").ifEmpty {
            listOf("<gold><bold>{name}", "<white>Progreso: <gray>{progress}%", "<yellow>¡Click para reparar!")
        }
        completedLines = langConfig.getStringList("generators.hologram.lines-completed").ifEmpty {
            listOf("<green><bold>✔ ENERGÍA RESTAURADA ✔", "<gray>¡Buen trabajo!")
        }
        nameCache.clear()
    }

    private fun getFriendlyName(material: Material): String {
        return nameCache.getOrPut(material) {
            val langConfig = plugin.messageConfig.getSpecificFile(null, "messages")
            langConfig.getString("generators.names.${material.name}")
                ?: material.name.lowercase().replace("_", " ").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    fun prepareArenaGenerators(locations: List<Location>) {
        val targetWorld = locations.firstOrNull()?.world
        if (targetWorld != null) {
            clearGeneratorsInWorld(targetWorld)
        }

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            synchronized(fileLock) {
                if (dataFile.exists()) dataConfig = YamlConfiguration.loadConfiguration(dataFile)
            }

            locations.forEach { loc ->
                val blockLoc = loc.block.location
                // Folia: Modificación del mundo siempre en el RegionScheduler
                plugin.server.regionScheduler.run(plugin, blockLoc, { _ ->
                    val coordKey = "${blockLoc.world.name}_${blockLoc.blockX}_${blockLoc.blockY}_${blockLoc.blockZ}"
                    var savedProgress = 0
                    var isDone = false
                    var original = Material.RAW_IRON_BLOCK

                    synchronized(fileLock) {
                        savedProgress = dataConfig.getInt("session.$coordKey.progress", 0)
                        isDone = dataConfig.getBoolean("session.$coordKey.completed", false)
                        original = Material.getMaterial(dataConfig.getString("session.$coordKey.original_material", "RAW_IRON_BLOCK")!!) ?: Material.RAW_IRON_BLOCK
                    }

                    val state = GeneratorState(original, savedProgress, isDone)
                    generators[blockLoc] = state

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

        plugin.server.regionScheduler.run(plugin, blockLoc, { _ ->
            val oldProgress = state.progress
            state.progress = (state.progress + amount).coerceIn(0, 100)

            if (state.progress != oldProgress) updateHologramVisual(state)
            if (state.progress >= 100) completeGenerator(blockLoc, state)
        })
    }

    private fun completeGenerator(loc: Location, state: GeneratorState) {
        state.completed = true
        loc.block.setType(Material.SEA_LANTERN, false)
        loc.world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f)

        saveStateToConfigAsync(loc, state)
        updateHologramVisual(state)

        val session = plugin.sessionManager?.activeSessions?.values?.find { s ->
            s.getPlayers().any { p -> p.world == loc.world }
        }
        session?.playerController?.checkWinCondition()
    }

    private fun spawnHologram(loc: Location, state: GeneratorState) {
        val holoLoc = loc.clone().add(0.5, 1.3, 0.5)
        state.displayEntity?.remove()

        state.displayEntity = holoLoc.world.spawn(holoLoc, TextDisplay::class.java) { display ->
            display.billboard = Display.Billboard.CENTER
            display.brightness = Display.Brightness(15, 15)
            display.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            display.isPersistent = false
            display.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(1.1f, 1.1f, 1.1f), Quaternionf())
            updateHologramVisual(state, display)
        }
    }

    private fun updateHologramVisual(state: GeneratorState, directEntity: TextDisplay? = null) {
        val entity = directEntity ?: state.displayEntity ?: return
        if (entity.isDead) return

        val typeName = getFriendlyName(state.originalMaterial)
        val lines = if (state.completed) completedLines else idleLines

        val text = lines.joinToString("<newline><reset>") { line ->
            line.replace("{name}", typeName).replace("{progress}", state.progress.toString())
        }

        entity.scheduler.run(plugin, { _ ->
            entity.text(mm.deserialize("<reset>$text"))
        }, null)
    }

    private fun saveStateToConfigAsync(loc: Location, state: GeneratorState) {
        val key = "session.${loc.world.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            synchronized(fileLock) {
                dataConfig.set("$key.progress", state.progress)
                dataConfig.set("$key.completed", state.completed)
                dataConfig.set("$key.original_material", state.originalMaterial.name)
                try { dataConfig.save(dataFile) } catch (e: Exception) { }
            }
        }
    }

    fun clearGenerators() {
        generators.forEach { (_, state) -> state.displayEntity?.remove() }
        generators.clear()
    }

    fun clearGeneratorsInWorld(world: World) {
        generators.entries.removeIf { (loc, state) ->
            if (loc.world == world) {
                state.displayEntity?.remove()
                true
            } else false
        }
    }

    fun isCompleted(loc: Location) = generators[loc.block.location]?.completed ?: false

    fun resetGenerators() {
        generators.forEach { (loc, state) ->
            state.progress = 0
            state.completed = false
            plugin.server.regionScheduler.run(plugin, loc, { _ ->
                loc.block.setType(state.originalMaterial, false)
                updateHologramVisual(state)
            })
        }
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            synchronized(fileLock) {
                dataConfig.set("session", null)
                try { dataConfig.save(dataFile) } catch (e: Exception) { }
            }
        }
    }

    fun getCompletedCountInWorld(world: World): Int = generators.entries.count { it.key.world == world && it.value.completed }
    fun getTotalGeneratorsInWorld(world: World): Int = generators.keys.count { it.world == world }
}
