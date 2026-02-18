package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
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
 * GeneratorManager: Gestión de generadores con Display Entities y Coroutines.
 * Optimizado para Paper 1.21.4+ (Uso de memoria mínimo).
 */
class GeneratorManager(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val generators = ConcurrentHashMap<Location, GeneratorState>()
    private val nameCache = ConcurrentHashMap<Material, String>()

    private lateinit var idleTemplate: List<String>
    private lateinit var completedTemplate: List<String>

    private val dataFile = File(plugin.dataFolder, "generator_data.yml")
    private var dataConfig = YamlConfiguration.loadConfiguration(dataFile)

    // Scope dedicado a tareas de guardado y lógica asíncrona
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class GeneratorState(
        val originalMaterial: Material,
        var progress: Int,
        var lastDisplayedProgress: Int = -1,
        var completed: Boolean,
        var displayEntity: TextDisplay? = null
    )

    init {
        setupDataFile()
        loadTemplates()
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    private fun setupDataFile() {
        if (!dataFile.exists()) {
            try {
                plugin.dataFolder.mkdirs()
                dataFile.createNewFile()
            } catch (e: IOException) {
                plugin.logger.severe("No se pudo crear generator_data.yml")
            }
        }
    }

    private fun loadTemplates() {
        // En Kotlin 2.1, usar 'run' o 'with' hace que el código sea más legible
        val langConfig = plugin.messageConfig.getLangConfig("es")
        idleTemplate = langConfig.getStringList("generators.hologram.lines-idle").ifEmpty {
            listOf("<gold><bold>{name}", "<white>Progreso: <gray>{progress}%")
        }
        completedTemplate = langConfig.getStringList("generators.hologram.lines-completed").ifEmpty {
            listOf("<green><bold>✔ COMPLETADO ✔", "<gray>¡Energía restaurada!")
        }
    }

    /**
     * Registra un generador en la sesión actual.
     * Optimizado para evitar parpadeos de bloques y accesos innecesarios al disco.
     */
    fun registerGenerator(loc: Location) {
        val blockLoc = loc.block.location
        if (generators.containsKey(blockLoc)) return

        val coordKey = locToCoordString(blockLoc)
        val session = dataConfig.getConfigurationSection("session.$coordKey")

        val savedProgress = session?.getInt("progress", 0) ?: 0
        val isDone = session?.getBoolean("completed", false) ?: false
        val savedMatName = session?.getString("original_material")
        val original = savedMatName?.let { Material.getMaterial(it) } ?: Material.RAW_IRON_BLOCK

        // Si no existía en el config, lo marcamos para el próximo guardado asíncrono
        if (savedMatName == null) {
            dataConfig.set("session.$coordKey.original_material", original.name)
        }

        val state = GeneratorState(original, savedProgress, -1, isDone)
        generators[blockLoc] = state

        // Lógica de bloques optimizada: Solo cambiar si es necesario y sin updates físicos
        val targetMat = if (isDone) Material.SEA_LANTERN else original
        if (blockLoc.block.type != targetMat) {
            blockLoc.block.setType(targetMat, false)
        }

        // Spawneamos el holograma usando la API nativa de Paper
        spawnHologram(blockLoc, state)
    }

    fun addProgress(loc: Location, amount: Int) {
        val state = generators[loc.block.location] ?: return
        if (state.completed) return

        state.progress = (state.progress + amount).coerceIn(0, 100)

        if (state.progress != state.lastDisplayedProgress) {
            updateHologramVisual(state)
        }

        if (state.progress >= 100) {
            completeGenerator(loc.block.location, state)
        }
    }

    private fun completeGenerator(loc: Location, state: GeneratorState) {
        state.completed = true

        // Efectos visuales en hilo principal
        loc.block.setType(Material.SEA_LANTERN, true)
        loc.world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f)
        loc.world.strikeLightningEffect(loc.clone().add(0.5, 0.0, 0.5))

        val coordKey = locToCoordString(loc)
        val path = "session.$coordKey"

        dataConfig.set("$path.progress", 100)
        dataConfig.set("$path.completed", true)

        // Guardado persistente asíncrono (No bloquea el juego)
        saveToFileAsync()
        updateHologramVisual(state)

        plugin.gameManager.checkWinCondition()
    }

    private fun spawnHologram(loc: Location, state: GeneratorState) {
        val holoLoc = loc.clone().add(0.5, 1.3, 0.5)

        // Usamos el Consumer de spawn de Paper para configurar la entidad ANTES de que aparezca en el mundo
        state.displayEntity = loc.world.spawn(holoLoc, TextDisplay::class.java) { display ->
            display.billboard = Display.Billboard.CENTER
            display.brightness = Display.Brightness(15, 15)
            display.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            display.isShadowed = true
            display.isPersistent = false // Importante: No queremos que se guarden en el mapa de Minecraft
            display.transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                Quaternionf(),
                Vector3f(1.1f, 1.1f, 1.1f),
                Quaternionf()
            )
        }

        updateHologramVisual(state)
    }

    private fun updateHologramVisual(state: GeneratorState) {
        val entity = state.displayEntity ?: return
        if (entity.isDead) return

        state.lastDisplayedProgress = state.progress

        // Cachear nombres de materiales para no procesar strings en cada tick
        val typeName = nameCache.getOrPut(state.originalMaterial) {
            getFriendlyName(state.originalMaterial)
        }

        val lines = if (state.completed) completedTemplate else idleTemplate

        // Usamos JoinToString para construir el componente de forma eficiente
        val content = lines.joinToString("\n<reset>") { line ->
            line.replace("{name}", typeName)
                .replace("{progress}", state.progress.toString())
        }

        entity.text(mm.deserialize(content))
    }

    private fun getFriendlyName(type: Material): String {
        val path = "generators.names.${type.name}"
        return plugin.messageConfig.getRawString(null, path, type.name.replace("_", " "))
    }

    private fun locToCoordString(loc: Location): String = "${loc.blockX}_${loc.blockY}_${loc.blockZ}"

    /**
     * Guarda en disco de forma asíncrona usando Dispatchers.IO.
     * Si hay 2 usuarios y están completando generadores, esto no lagueará el servidor.
     */
    private fun saveToFileAsync() {
        managerScope.launch(Dispatchers.IO) {
            try {
                dataConfig.save(dataFile)
            } catch (e: IOException) {
                plugin.logger.severe("Error al guardar generator_data.yml asíncronamente")
            }
        }
    }

    fun resetGenerators() {
        generators.forEach { (loc, state) ->
            state.progress = 0
            state.lastDisplayedProgress = -1
            state.completed = false
            loc.block.setType(state.originalMaterial, false)
            updateHologramVisual(state)
        }

        dataConfig.set("session", null)
        saveToFileAsync()
    }

    fun clearGenerators() {
        generators.forEach { (_, state) ->
            state.displayEntity?.remove()
        }
        generators.clear()
        nameCache.clear()
        managerScope.cancel() // Detener tareas pendientes
    }

    // Getters rápidos
    fun isCompleted(loc: Location) = generators[loc.block.location]?.completed ?: false
    fun getProgress(loc: Location) = generators[loc.block.location]?.progress ?: 0
    fun getCompletedCount() = generators.values.count { it.completed }
    fun getTotalGenerators() = generators.size
    fun getGeneratorLocations(): List<Location> = generators.keys.toList()
}
