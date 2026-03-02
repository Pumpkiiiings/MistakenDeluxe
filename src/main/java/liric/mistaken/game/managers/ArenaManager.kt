package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.Arena
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * ArenaManager: Gestión de persistencia ultra-eficiente.
 * Las operaciones de disco ahora son no-bloqueantes.
 */
class ArenaManager(private val plugin: Mistaken) {

    private val arenas = ConcurrentHashMap<String, Arena>()
    private val file = File(plugin.dataFolder, "arenas.yml")
    private var config = YamlConfiguration.loadConfiguration(file)
    private val mm = MiniMessage.miniMessage()

    // Scope para operaciones de disco (I/O)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        if (!file.exists()) plugin.saveResource("arenas.yml", false)
        loadArenas()
    }

    private fun loadArenas() {
        val section = config.getConfigurationSection("arenas") ?: return
        arenas.clear()

        for (key in section.getKeys(false)) {
            val arena = Arena(key)
            val path = "arenas.$key."

            arena.slimeWorldName = config.getString("${path}slimeWorld", key)
            arena.asesinoSpawn = loadSafeLocation("${path}asesinoSpawn")

            // Cargar listas usando utilidades de Kotlin
            loadLocationList("${path}survivorSpawns").forEach { arena.addSurvivorSpawn(it) }
            loadLocationList("${path}generators").forEach { arena.addGenerator(it) }

            arenas[key] = arena
        }
    }

    // --- COMANDOS Y MUTACIONES ---

    fun createArena(name: String) {
        if (arenas.containsKey(name)) return
        val arena = Arena(name)
        arenas[name] = arena

        config.set("arenas.$name.name", name)
        config.set("arenas.$name.slimeWorld", name)
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
                // Usamos UUID para evitar colisiones en el YAML
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
        var world = Bukkit.getWorld(worldName)

        // Fallback para SlimeWorldManager / ASP
        if (world == null && Bukkit.getWorlds().isNotEmpty()) {
            world = Bukkit.getWorlds()[0]
        }

        world ?: return null

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

    /**
     * Guarda la configuración en disco de forma ASÍNCRONA.
     * Esto evita que el servidor principal sufra tirones (lag spikes).
     */
    private fun saveAsync() {
        ioScope.launch {
            try {
                config.save(file)
            } catch (e: IOException) {
                plugin.logger.severe("No se pudo guardar arenas.yml: ${e.message}")
            }
        }
    }

    fun getArenas(): Map<String, Arena> = arenas
    fun getArena(name: String): Arena? = arenas[name]

    fun reloadConfig() {
        config = YamlConfiguration.loadConfiguration(file)
        loadArenas()
    }
}

