package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.event.Listener
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * GeneratorManager: Gestión de generadores ultra-optimizada y multilingüe.
 */
class GeneratorManager(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()

    // Cache en RAM pura para acceso instantáneo
    private val generators = ConcurrentHashMap<Location, GeneratorState>()
    private val nameCache = ConcurrentHashMap<Material, String>()

    // Plantillas de texto cargadas desde el idioma del servidor
    private var idleLines: List<String> = emptyList()
    private var completedLines: List<String> = emptyList()

    private val dataFile = File(plugin.dataFolder, "generator_data.yml")
    private var dataConfig = YamlConfiguration()

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class GeneratorState(
        val originalMaterial: Material,
        var progress: Int,
        var completed: Boolean,
        var displayEntity: TextDisplay? = null
    )

    init {
        loadTemplates()
    }

    /**
     * Carga las líneas del holograma y nombres desde el idioma configurado.
     */
    fun loadTemplates() {
        // 1. Obtenemos el idioma principal (default "es")
        val defaultLang = plugin.config.getString("settings.default-lang", "es") ?: "es"
        val langConfig = plugin.messageConfig.getLangConfig(defaultLang)

        // 2. Cargamos las plantillas del YAML
        idleLines = langConfig.getStringList("generators.hologram.lines-idle").ifEmpty {
            listOf("<gold>{name}", "<white>Progreso: <gray>{progress}%")
        }
        completedLines = langConfig.getStringList("generators.hologram.lines-completed").ifEmpty {
            listOf("<green><bold>✔ ENERGÍA RESTAURADA ✔")
        }

        // Limpiamos el caché de nombres por si el idioma cambió en un reload
        nameCache.clear()

        plugin.componentLogger.info(mm.deserialize("<gray>[Generadores] Plantillas cargadas correctamente (<white>$defaultLang</white>)."))
    }

    /**
     * Obtiene el nombre estético del bloque según el idioma del servidor.
     */
    private fun getFriendlyName(material: Material): String {
        return nameCache.getOrPut(material) {
            val lang = plugin.config.getString("settings.default-lang", "es") ?: "es"
            val langConfig = plugin.messageConfig.getLangConfig(lang)

            // Busca en 'generators.names.MATERIAL_NAME'
            // Fallback: Formatea el nombre del Material (RAW_IRON_BLOCK -> Raw Iron Block)
            langConfig.getString("generators.names.${material.name}")
                ?: material.name.lowercase().replace("_", " ").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    fun prepareArenaGenerators(locations: List<Location>) {
        clearGenerators()

        managerScope.launch {
            if (dataFile.exists()) {
                dataConfig = YamlConfiguration.loadConfiguration(dataFile)
            }

            withContext(plugin.mainThread) {
                locations.forEach { loc ->
                    val blockLoc = loc.block.location
                    val coordKey = "${blockLoc.blockX}_${blockLoc.blockY}_${blockLoc.blockZ}"

                    val savedProgress = dataConfig.getInt("session.$coordKey.progress", 0)
                    val isDone = dataConfig.getBoolean("session.$coordKey.completed", false)
                    val original = Material.getMaterial(dataConfig.getString("session.$coordKey.original_material", "RAW_IRON_BLOCK")!!)
                        ?: Material.RAW_IRON_BLOCK

                    val state = GeneratorState(original, savedProgress, isDone)
                    generators[blockLoc] = state

                    if (isDone) blockLoc.block.setType(Material.SEA_LANTERN, false)
                    spawnHologram(blockLoc, state)
                }
            }
        }
    }

    fun registerGenerator(loc: Location) {
        if (generators.containsKey(loc.block.location)) return

        val blockLoc = loc.block.location
        val state = GeneratorState(Material.RAW_IRON_BLOCK, 0, false)
        generators[blockLoc] = state
        spawnHologram(blockLoc, state)
    }

    fun addProgress(loc: Location, amount: Int) {
        val blockLoc = loc.block.location
        val state = generators[blockLoc] ?: return
        if (state.completed) return

        val oldProgress = state.progress
        state.progress = (state.progress + amount).coerceIn(0, 100)

        if (state.progress != oldProgress) {
            updateHologramVisual(state)
        }

        if (state.progress >= 100) {
            completeGenerator(blockLoc, state)
        }
    }

    private fun completeGenerator(loc: Location, state: GeneratorState) {
        state.completed = true
        loc.block.setType(Material.SEA_LANTERN, false)
        loc.world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f)

        saveStateToConfig(loc, state)
        updateHologramVisual(state)

        plugin.gameManager.checkWinCondition()
    }

    private fun spawnHologram(loc: Location, state: GeneratorState) {
        val holoLoc = loc.clone().add(0.5, 1.3, 0.5)

        state.displayEntity = loc.world.spawn(holoLoc, TextDisplay::class.java) { display ->
            display.billboard = Display.Billboard.CENTER
            display.brightness = Display.Brightness(15, 15)
            display.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            display.isPersistent = false
            display.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(1.1f, 1.1f, 1.1f), Quaternionf())
        }
        updateHologramVisual(state)
    }

    private fun updateHologramVisual(state: GeneratorState) {
        val entity = state.displayEntity ?: return
        if (entity.isDead) return

        // Obtenemos el nombre dinámico traducido
        val typeName = getFriendlyName(state.originalMaterial)
        val lines = if (state.completed) completedLines else idleLines

        // Build Component eficiente con placeholders dinámicos
        val text = lines.joinToString("\n") { line ->
            line.replace("{name}", typeName)
                .replace("{progress}", state.progress.toString())
        }

        entity.text(mm.deserialize(text))
    }

    private fun saveStateToConfig(loc: Location, state: GeneratorState) {
        val key = "session.${loc.blockX}_${loc.blockY}_${loc.blockZ}"
        dataConfig.set("$key.progress", state.progress)
        dataConfig.set("$key.completed", state.completed)
        dataConfig.set("$key.original_material", state.originalMaterial.name)

        saveToFileAsync()
    }

    private fun saveToFileAsync() {
        managerScope.launch {
            try {
                dataConfig.save(dataFile)
            } catch (e: Exception) { /* Silencioso */ }
        }
    }

    fun clearGenerators() {
        generators.forEach { (_, state) -> state.displayEntity?.remove() }
        generators.clear()
        // No borramos nameCache aquí para mantener el rendimiento en reloads rápidos
    }

    fun isCompleted(loc: Location) = generators[loc.block.location]?.completed ?: false
    fun getProgress(loc: Location) = generators[loc.block.location]?.progress ?: 0

    fun resetGenerators() {
        generators.forEach { (loc, state) ->
            state.progress = 0
            state.completed = false
            loc.block.setType(state.originalMaterial, false)
            updateHologramVisual(state)
        }
        dataConfig.set("session", null)
        saveToFileAsync()
    }

    fun getCompletedCount(): Int = generators.values.count { it.completed }
    fun getTotalGenerators(): Int = generators.size
    fun getGeneratorLocations(): List<Location> = generators.keys.toList()
}
