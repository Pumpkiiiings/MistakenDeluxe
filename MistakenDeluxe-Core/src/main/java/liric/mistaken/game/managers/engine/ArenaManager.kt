package liric.mistaken.game.managers.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import liric.mistaken.Mistaken
import liric.mistaken.game.Arena
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach

/**
 * [LIRIC-MISTAKEN 2.0]
 * ArenaManager: Gestión de persistencia ultra-eficiente.
 * FIX: Eliminación de Memory Leaks de Mundos y Guardado Thread-Safe.
 */
class ArenaManager(private val plugin: Mistaken) {

    private val arenas = ConcurrentHashMap<String, Arena>()
    private var configProvider = pumpking.lib.config.ConfigManager.get("arenas.yml")
    private var config = configProvider.getRaw()
    private val mm = MiniMessage.miniMessage()

    // Candado para evitar que el archivo se corrompa si 2 personas configuran a la vez
    private val fileLock = Any()

    // Scope para operaciones de disco (I/O)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadArenasAsync()
    }

    private fun loadArenasAsync() {
        ioScope.launch {
            synchronized(fileLock) {
                configProvider.load()
                config = configProvider.getRaw()
            }

            val section = config.getConfigurationSection("arenas") ?: return@launch
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

            // Reemplazo atómico para no causar tirones
            arenas.clear()
            arenas.putAll(tempArenas)
            plugin.componentLogger.info(mm.deserialize("[SUCCESS] [Arenas] ${arenas.size} templates loaded into secure memory."))
        }
    }

    // --- COMANDOS Y MUTACIONES ---

    fun createArena(name: String) {
        if (arenas.containsKey(name)) return
        val arena = Arena(name)
        arenas[name] = arena

        synchronized(fileLock) {
            config.set("arenas.$name.name", name)
            config.set("arenas.$name.slimeWorld", name)
        }
        saveAsync()
    }

    fun deleteArena(name: String) {
        arenas.remove(name)
        synchronized(fileLock) {
            config.set("arenas.$name", null)
        }
        saveAsync()
    }

    fun setSpawn(name: String, type: String, loc: Location) {
        val arena = arenas[name] ?: return

        when (type.lowercase()) {
            "asesino" -> {
                // Clonamos para quitarle el mundo a la copia guardada en RAM
                val cleanLoc = Location(null, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
                arena.asesinoSpawn = cleanLoc
                saveSafeLocation("arenas.$name.asesinoSpawn", loc)
            }
            "survivor" -> {
                val cleanLoc = Location(null, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
                arena.addSurvivorSpawn(cleanLoc)
                saveSafeLocation("arenas.$name.survivorSpawns.${UUID.randomUUID()}", loc)
            }
        }
    }

    fun addGenerator(name: String, loc: Location) {
        val arena = arenas[name] ?: return
        val cleanLoc = Location(null, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
        arena.addGenerator(cleanLoc)
        saveSafeLocation("arenas.$name.generators.${UUID.randomUUID()}", loc)
    }

    fun saveGenerators(name: String, locations: List<Location>) {
        val arena = arenas[name] ?: return
        arena.generators.clear()

        synchronized(fileLock) {
            config.set("arenas.$name.generators", null)
        }

        locations.forEach { loc ->
            val cleanLoc = Location(null, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
            arena.generators.add(cleanLoc)
            saveSafeLocation("arenas.$name.generators.${UUID.randomUUID()}", loc)
        }
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
        // Si no tiene X, es que el path está vacío
        if (!config.contains("$path.x")) return null

        // 🔥 LA MAGIA ANTI-LEAKS: Retornamos la Location con World en NULL.
        // Esto garantiza que el ArenaManager NUNCA sostenga un mundo descargado en la RAM.
        // El GameManager le inyectará el mundo activo al clonar la Location.
        return Location(
            null,
            config.getDouble("$path.x"),
            config.getDouble("$path.y"),
            config.getDouble("$path.z"),
            config.getDouble("$path.yaw").toFloat(),
            config.getDouble("$path.pitch").toFloat()
        )
    }

    private fun saveSafeLocation(path: String, loc: Location) {
        val worldName = loc.world?.name ?: "world"
        synchronized(fileLock) {
            config.set("$path.world", worldName)
            config.set("$path.x", loc.x)
            config.set("$path.y", loc.y)
            config.set("$path.z", loc.z)
            config.set("$path.yaw", loc.yaw)
            config.set("$path.pitch", loc.pitch)
        }
        saveAsync()
    }

    private fun saveAsync() {
        ioScope.launch {
            try {
                synchronized(fileLock) {
                    configProvider.save()
                }
            } catch (e: Exception) {
                plugin.componentLogger.error(mm.deserialize("[ERROR] [Arenas] Failed to save arenas.yml: ${e.message}"))
            }
        }
    }

    fun getArenas(): Map<String, Arena> = arenas
    fun getArena(name: String): Arena? = arenas[name]

    fun reloadConfig() {
        loadArenasAsync()
    }

    fun shutdown() {
        ioScope.cancel()
    }
}