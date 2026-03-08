package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
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
import java.util.concurrent.ConcurrentHashMap

/**
 *[LIRIC-MISTAKEN 2.0]
 * GeneratorManager: Gestión de generadores ultra-optimizada.
 * FIX: Soporte de RegionScheduler (Folia Safe), YAML Thread-Safe y Anti-Colisión de Mundos.
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

    // Candado para evitar que el archivo YAML se corrompa si 2 generadores se completan al mismo tiempo
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
        // 🔥 FIX: Leemos explícitamente del archivo "messages.yml" del idioma por defecto
        val langConfig = plugin.messageConfig.getSpecificFile(null, "messages")

        // Usamos tus nuevas líneas por defecto si el YAML llega a fallar
        idleLines = langConfig.getStringList("generators.hologram.lines-idle").ifEmpty {
            listOf(
                "<gold><bold>{name}",
                "<white>Progreso: <gray>{progress}%",
                "<yellow>¡Click para reparar!"
            )
        }

        completedLines = langConfig.getStringList("generators.hologram.lines-completed").ifEmpty {
            listOf(
                "<green><bold>✔ ENERGÍA RESTAURADA ✔",
                "<gray>¡Buen trabajo!"
            )
        }

        nameCache.clear()
        plugin.componentLogger.info(mm.deserialize("<gray>[Generadores] Plantillas y nombres cargados correctamente.</gray>"))
    }

    private fun getFriendlyName(material: Material): String {
        return nameCache.getOrPut(material) {
            // Buscamos el nombre en messages.yml -> generators.names.MATERIAL
            val langConfig = plugin.messageConfig.getSpecificFile(null, "messages")

            langConfig.getString("generators.names.${material.name}")
                ?: material.name.lowercase().replace("_", " ").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    fun prepareArenaGenerators(locations: List<Location>) {
        clearGenerators()

        // 1. Cargamos el YAML de forma asíncrona para no congelar el servidor
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            synchronized(fileLock) {
                if (dataFile.exists()) {
                    dataConfig = YamlConfiguration.loadConfiguration(dataFile)
                }
            }

            // 2. 🔥 FIX PAPER: Modificar bloques y spawnear se hace en el RegionScheduler
            locations.forEach { loc ->
                val blockLoc = loc.block.location

                // Le decimos a Paper: "Ejecuta esto en el micro-hilo que controla este bloque específico"
                plugin.server.regionScheduler.execute(plugin, blockLoc, Runnable {

                    // 🔥 FIX: Llave única que incluye el Mundo
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

    fun registerGenerator(loc: Location) {
        val blockLoc = loc.block.location
        if (generators.containsKey(blockLoc)) return

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

        saveStateToConfigAsync(loc, state)
        updateHologramVisual(state)

        // 🔥 CORREGIDO: Ahora usamos el playerController para la condición de victoria
        plugin.gameManager.playerController.checkWinCondition()
    }

    private fun spawnHologram(loc: Location, state: GeneratorState) {
        val holoLoc = loc.clone().add(0.5, 1.3, 0.5)

        // Verificamos por seguridad que estemos en el hilo regional correcto (Paper Safe)
        plugin.server.regionScheduler.execute(plugin, holoLoc, Runnable {
            // Destruir el viejo si por alguna razón había uno clonado
            state.displayEntity?.remove()

            state.displayEntity = holoLoc.world.spawn(holoLoc, TextDisplay::class.java) { display ->
                display.billboard = Display.Billboard.CENTER
                display.brightness = Display.Brightness(15, 15)
                display.backgroundColor = Color.fromARGB(0, 0, 0, 0)
                display.isPersistent = false
                display.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(1.1f, 1.1f, 1.1f), Quaternionf())
                updateHologramVisual(state, display) // Pasamos la entidad directa para evitar nulos
            }
        })
    }

    // Helper para actualizar visualmente usando la referencia directa o la del estado
    private fun updateHologramVisual(state: GeneratorState, directEntity: TextDisplay? = null) {
        val entity = directEntity ?: state.displayEntity ?: return
        if (entity.isDead) return

        val typeName = getFriendlyName(state.originalMaterial)
        val lines = if (state.completed) completedLines else idleLines

        // 🔥 FIX: Reemplazamos "\n<reset>" por "<newline><reset>"
        // En MiniMessage moderno, "\n" a veces puede romperse en entidades Display.
        // Al usar <newline>, nos aseguramos de que el TextDisplay lo renderice perfecto.
        val text = lines.joinToString("<newline><reset>") { line ->
            line.replace("{name}", typeName)
                .replace("{progress}", state.progress.toString())
        }

        // Parseamos el texto final (se inicia con un reset de seguridad)
        entity.text(mm.deserialize("<reset>$text"))
    }

    private fun saveStateToConfigAsync(loc: Location, state: GeneratorState) {
        val key = "session.${loc.world.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            // 🔥 FIX: synchronized evita que 2 hilos corrompan el YAML
            synchronized(fileLock) {
                dataConfig.set("$key.progress", state.progress)
                dataConfig.set("$key.completed", state.completed)
                dataConfig.set("$key.original_material", state.originalMaterial.name)

                try {
                    dataConfig.save(dataFile)
                } catch (e: Exception) {
                    plugin.componentLogger.warn(mm.deserialize("<yellow>Error al guardar progreso de generador: ${e.message}</yellow>"))
                }
            }
        }
    }

    fun clearGenerators() {
        generators.forEach { (_, state) -> state.displayEntity?.remove() }
        generators.clear()
    }

    fun isCompleted(loc: Location) = generators[loc.block.location]?.completed ?: false
    fun getProgress(loc: Location) = generators[loc.block.location]?.progress ?: 0

    fun resetGenerators() {
        generators.forEach { (loc, state) ->
            state.progress = 0
            state.completed = false

            // RegionScheduler obligatorio para setType
            plugin.server.regionScheduler.execute(plugin, loc, Runnable {
                loc.block.setType(state.originalMaterial, false)
                updateHologramVisual(state)
            })
        }

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            synchronized(fileLock) {
                dataConfig.set("session", null)
                try { dataConfig.save(dataFile) } catch (e: Exception) { /* Silencioso */ }
            }
        }
    }

    fun getCompletedCount(): Int = generators.values.count { it.completed }
    fun getTotalGenerators(): Int = generators.size
    fun getGeneratorLocations(): List<Location> = generators.keys.toList()
}
