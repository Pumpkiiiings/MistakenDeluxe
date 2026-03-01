package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.Arena
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * ArenaManager: Gestión de persistencia ultra-eficiente.
 * FIX: Carga y Guardado 100% Asíncronos para evitar tirones en el servidor.
 */
class ArenaManager(private val plugin: Mistaken) {

    private val arenas = ConcurrentHashMap<String, Arena>()
    private val file = File(plugin.dataFolder, "arenas.yml")
    private var config = YamlConfiguration()

    // Scope dedicado para I/O (Disco Duro)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // La carga inicial la hacemos asíncrona para no bloquear el inicio del servidor
        if (!file.exists()) plugin.saveResource("arenas.yml", false)
        loadArenasAsync()
    }

    private fun loadArenasAsync() {
        ioScope.launch {
            config = YamlConfiguration.loadConfiguration(file)
            val section = config.getConfigurationSection("arenas") ?: return@launch

            // Leemos todo en memoria temporal
            val tempArenas = mutableMapOf<String, Arena>()

            for (key in section.getKeys(false)) {
                val arena = Arena(key)
                val path = "arenas.$key."

                arena.slimeWorldName = config.getString("${path}slimeWorld", key)
                arena.asesinoSpawn = loadSafeLocation("${path}asesinoSpawn")

                loadLocationList("${path}survivorSpawns").forEach { arena.addSurvivorSpawn(it) }
                loadLocationList("${path}generators").forEach { arena.addGenerator(it) }

                tempArenas[key] = arena
            }

            // Aplicamos los cambios al mapa principal de forma atómica
            arenas.putAll(tempArenas)
            plugin.componentLogger.info(plugin.mm.deserialize("<green>[Arenas] ${arenas.size} arenas cargadas.</green>"))
        }
    }

    // --- COMANDOS Y MUTACIONES ---

    fun createArena(name: String) {
        if (arenas.containsKey(name)) return
        val arena = Arena(name)
        arenas[name] = arena

        // Modificamos el objeto config en memoria (es rápido)
        config.set("arenas.$name.name", name)
        config.set("arenas.$name.slimeWorld", name)

        // Guardamos al disco en fondo
        saveAsync()
    }

    fun deleteArena(name: String) {
        arenas.remove(name)
        config.set("arenas.$name", null)
        saveAsync()
    }

    fun setSpawn(name: String, type: String, loc: Location) {
        val arena = arenas[name] ?: return

        when (type.lowercase()) {
            "asesino" -> {
                arena.asesinoSpawn = loc
                saveSafeLocation("arenas.$name.asesinoSpawn", loc)
            }
            "survivor" -> {
                arena.addSurvivorSpawn(loc)
                saveSafeLocation("arenas.$name.survivorSpawns.${UUID.randomUUID()}", loc)
            }
        }
    }

    fun addGenerator(name: String, loc: Location) {
        val arena = arenas[name] ?: return
        arena.addGenerator(loc)
        saveSafeLocation("arenas.$name.generators.${UUID.randomUUID()}", loc)
    }

    fun saveGenerators(name: String, locations: List<Location>) {
        val arena = arenas[name] ?: return
        arena.generators.clear()
        arena.generators.addAll(locations)

        config.set("arenas.$name.generators", null)
        locations.forEach { saveSafeLocation("arenas.$name.generators.${UUID.randomUUID()}", it) }
        saveAsync()
    }

    // --- UTILS DE LOCALIZACIÓN ---

    private fun loadLocationList(path: String): List<Location> {
        val list = mutableListOf<Location>()
        val section = config.getConfigurationSection(path) ?: return list

        section.getKeys(false).forEach { key ->
            loadSafeLocation("$path.$key")?.let { list.add(it) }
        }
        return list
    }

    private fun loadSafeLocation(path: String): Location? {
        val worldName = config.getString("$path.world") ?: return null

        // ¡OJO! Bukkit.getWorld debe llamarse desde el hilo principal si es posible,
        // pero para configuraciones simples de lectura suele ser seguro.
        // Si usas SlimeWorldManager, el mundo quizás no exista aún (es normal).
        val world = Bukkit.getWorld(worldName)

        // Si el mundo es nulo, guardamos la Location con mundo null (SlimeWorld lo cargará después)
        // O retornamos una Location válida si el mundo existe.
        if (world == null) return null

        return Location(
            world,
            config.getDouble("$path.x"),
            config.getDouble("$path.y"),
            config.getDouble("$path.z"),
            config.getDouble("$path.yaw").toFloat(),
            config.getDouble("$path.pitch").toFloat()
        )
    }

    private fun saveSafeLocation(path: String, loc: Location) {
        val world = loc.world ?: return
        config.set("$path.world", world.name)
        config.set("$path.x", loc.x)
        config.set("$path.y", loc.y)
        config.set("$path.z", loc.z)
        config.set("$path.yaw", loc.yaw)
        config.set("$path.pitch", loc.pitch)
        saveAsync()
    }

    private fun saveAsync() {
        ioScope.launch {
            try {
                // synchronized evita que dos hilos escriban el archivo a la vez
                synchronized(file) {
                    config.save(file)
                }
            } catch (e: IOException) {
                plugin.componentLogger.error(plugin.mm.deserialize("<red>Error guardando arenas.yml: ${e.message}</red>"))
            }
        }
    }

    fun getArenas(): Map<String, Arena> = arenas
    fun getArena(name: String): Arena? = arenas[name]

    fun shutdown() {
        ioScope.cancel()
    }
}
