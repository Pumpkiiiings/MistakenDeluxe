package liric.mistaken.data

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * PlayerDataManager: Gestión de perfiles con persistencia asíncrona.
 * Optimizado para minimizar el impacto en el Main Thread.
 */
class PlayerDataManager(private val plugin: Mistaken) {

    private val mm = plugin.mm
    private val userData = ConcurrentHashMap<UUID, MistakenUser>()

    private lateinit var file: File
    private lateinit var config: YamlConfiguration

    // Mutex para evitar corrupción de archivos en guardados concurrentes
    private val saveMutex = Mutex()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        setupFile()
    }

    /**
     * Modelo de datos del jugador.
     */
    data class MistakenUser(
        var stamina: Double = 100.0,
        var selectedKiller: String = "slasher",
        var selectedSurvivor: String = "civil",
        var language: String = "en",
        val unlockedKillers: MutableList<String> = mutableListOf("slasher"),
        val unlockedSurvivors: MutableList<String> = mutableListOf("civil"),
        var nickname: String? = null,
        var skinName: String? = null
    )

    private fun setupFile() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdir()
        file = File(plugin.dataFolder, "players.yml")
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                plugin.logger.severe("No se pudo crear players.yml")
            }
        }
        config = YamlConfiguration.loadConfiguration(file)
    }

    /**
     * Carga los datos desde el YAML a la memoria RAM.
     */
    fun loadPlayerData(player: Player) {
        val uuid = player.uniqueId
        val user = MistakenUser()

        val path = uuid.toString()

        // Cargar Asesinos
        config.getStringList("$path.comprados").forEach { k ->
            if (k.lowercase() !in user.unlockedKillers) user.unlockedKillers.add(k.lowercase())
        }
        user.selectedKiller = config.getString("$path.seleccionado", "slasher") ?: "slasher"

        // Cargar Supervivientes
        config.getStringList("$path.supervivientes_comprados").forEach { s ->
            if (s.lowercase() !in user.unlockedSurvivors) user.unlockedSurvivors.add(s.lowercase())
        }
        user.selectedSurvivor = config.getString("$path.superviviente_seleccionado", "civil") ?: "civil"

        // Lenguaje y Nick
        user.language = config.getString("$path.lang", player.locale().language) ?: "en"
        user.nickname = config.getString("$path.nick")
        user.skinName = config.getString("$path.skin_source")

        userData[uuid] = user

        // Actualizar stats (Si el manager de stats ya está en Kotlin)
        plugin.playerStatsManager.updateSelectedKiller(uuid, player.name, user.selectedKiller)
    }

    // --- LÓGICA DE TIENDA ---

    fun tieneAsesino(uuid: UUID, killerId: String): Boolean {
        val user = userData[uuid] ?: return false
        return killerId.equals("slasher", true) || user.unlockedKillers.contains(killerId.lowercase())
    }

    fun tieneSuperviviente(uuid: UUID, survivorId: String): Boolean {
        val user = userData[uuid] ?: return false
        return survivorId.equals("civil", true) || user.unlockedSurvivors.contains(survivorId.lowercase())
    }

    // --- ACCIONES ---

    fun consumeStamina(uuid: UUID, amount: Double) {
        userData[uuid]?.let { user ->
            user.stamina = (user.stamina - amount).coerceIn(0.0, 100.0)
        }
    }

    fun setLanguage(uuid: UUID, lang: String) {
        userData[uuid]?.let { user ->
            val l = lang.lowercase()
            user.language = l
            config.set("$uuid.lang", l)
            saveConfigAsync()
        }
    }

    fun comprarAsesino(uuid: UUID, killerId: String) {
        userData[uuid]?.let { user ->
            val id = killerId.lowercase()
            if (id !in user.unlockedKillers) {
                user.unlockedKillers.add(id)
                config.set("$uuid.comprados", user.unlockedKillers)
                saveConfigAsync()
            }
        }
    }

    fun comprarSuperviviente(uuid: UUID, survivorId: String) {
        userData[uuid]?.let { user ->
            val id = survivorId.lowercase()
            if (id !in user.unlockedSurvivors) {
                user.unlockedSurvivors.add(id)
                config.set("$uuid.supervivientes_comprados", user.unlockedSurvivors)
                saveConfigAsync()
            }
        }
    }

    fun setSelectedKiller(uuid: UUID, killerId: String) {
        val user = userData[uuid] ?: return
        if (tieneAsesino(uuid, killerId)) {
            val killer = killerId.lowercase()
            user.selectedKiller = killer
            config.set("$uuid.seleccionado", killer)
            saveConfigAsync()

            Bukkit.getPlayer(uuid)?.let {
                plugin.playerStatsManager.updateSelectedKiller(uuid, it.name, killer)
            }
        }
    }

    fun setSelectedSurvivor(uuid: UUID, survivorId: String) {
        val user = userData[uuid] ?: return
        if (tieneSuperviviente(uuid, survivorId)) {
            val survivor = survivorId.lowercase()
            user.selectedSurvivor = survivor
            config.set("$uuid.superviviente_seleccionado", survivor)
            saveConfigAsync()
        }
    }

    // --- GETTERS ---

    fun getStamina(uuid: UUID) = userData[uuid]?.stamina ?: 100.0
    fun getLanguage(uuid: UUID) = userData[uuid]?.language ?: "en"
    fun getSelectedKiller(uuid: UUID) = userData[uuid]?.selectedKiller ?: "slasher"
    fun getSelectedSurvivor(uuid: UUID) = userData[uuid]?.selectedSurvivor ?: "civil"
    fun getUserData(uuid: UUID) = userData[uuid]

    // --- PERSISTENCIA ASÍNCRONA ---

    private fun saveConfigAsync() {
        managerScope.launch {
            saveMutex.withLock {
                try {
                    config.save(file)
                } catch (e: IOException) {
                    plugin.logger.severe("Error al guardar players.yml: ${e.message}")
                }
            }
        }
    }

    /**
     * Guardado síncrono para el onDisable
     */
    fun saveConfigSync() {
        runBlocking {
            saveMutex.withLock {
                try { config.save(file) } catch (ignored: Exception) {}
            }
        }
    }

    fun removeData(uuid: UUID) { userData.remove(uuid) }
}
