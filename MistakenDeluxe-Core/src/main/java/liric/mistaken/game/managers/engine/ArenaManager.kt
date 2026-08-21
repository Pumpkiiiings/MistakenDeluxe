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
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.ConfigManager


import liric.mistaken.api.managers.IArenaManager
import liric.mistaken.api.managers.IArena

class ArenaManager(private val plugin: Mistaken) : IArenaManager {

    private val arenas = ConcurrentHashMap<String, Arena>()
    private var configProvider = ConfigManager.get("arenas.yml")
    private var config = configProvider.getRaw()
    private val mm = MiniMessage.miniMessage()

    
    private val fileLock = Any()

    
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
                arena.timeMode = config.getString("${path}timeMode", "dynamic") ?: "dynamic"
                arena.killerSpawn = loadSafeLocation("${path}asesinoSpawn")

                loadLocationList("${path}survivorSpawns").forEach { arena.addSurvivorSpawn(it) }
                loadLocationList("${path}generators").forEach { arena.addGenerator(it) }

                tempArenas[key] = arena
            }

            
            arenas.clear()
            arenas.putAll(tempArenas)
            plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>${arenas.size} templates loaded into secure memory.</gray>"))
        }
    }

    

    fun createArena(name: String) {
        if (arenas.containsKey(name)) return
        val arena = Arena(name)
        arenas[name] = arena

        synchronized(fileLock) {
            config.set("arenas.$name.name", name)
            config.set("arenas.$name.slimeWorld", name)
            config.set("arenas.$name.timeMode", "dynamic")
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
                
                val cleanLoc = Location(null, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
                arena.killerSpawn = cleanLoc
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

    fun setTimeMode(name: String, timeMode: String) {
        val arena = arenas[name] ?: return
        arena.timeMode = timeMode
        synchronized(fileLock) {
            config.set("arenas.$name.timeMode", timeMode)
        }
        saveAsync()
    }

    

    private fun loadLocationList(path: String): List<Location> {
        val list = mutableListOf<Location>()
        val section = config.getConfigurationSection(path) ?: return list

        section.getKeys(false).forEach { key ->
            loadSafeLocation("$path.$key")?.let { list.add(it) }
        }
        return list
    }

    private fun loadSafeLocation(path: String): Location? {
        
        if (!config.contains("$path.x")) return null

        
        
        
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
                plugin.componentLogger.error(liric.mistaken.utils.color.ColorTranslator.translate("<red>[ERROR]</red> <gray>Failed to save arenas.yml: ${e.message}</gray>"))
            }
        }
    }

    override val defaultArena: IArena?
        get() = arenas.values.firstOrNull()

    fun getArenasMap(): Map<String, Arena> = arenas
    override fun getArenas(): List<IArena> = arenas.values.toList()
    override fun getArena(name: String): Arena? = arenas[name]

    fun reloadConfig() {
        loadArenasAsync()
    }

    fun shutdown() {
        ioScope.cancel()
    }
}
