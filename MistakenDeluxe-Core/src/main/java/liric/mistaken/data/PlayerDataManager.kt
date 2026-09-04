package liric.mistaken.data

import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.api.managers.IPlayerDataManager


class PlayerDataManager(private val plugin: Mistaken) : IPlayerDataManager {

    
    private val userDataCache = ConcurrentHashMap<UUID, MistakenUser>()

    data class MistakenUser(
        var language: String = "es",
        val unlockedKillers: MutableSet<String> = mutableSetOf("slasher"),
        var selectedKiller: String = "slasher",
        val unlockedSurvivors: MutableSet<String> = mutableSetOf("civil"),
        var selectedSurvivor: String = "civil",
        var nickname: String = "",
        var skinName: String = "",
        var stamina: Double = 100.0 
    )

    /**
     * Carga los datos desde MySQL a la memoria RAM.
     * DEBE llamarse desde un hilo asíncrono (Como en PlayerListener).
     */
    fun loadPlayerData(player: Player) {
        val uuid = player.uniqueId
        val user = MistakenUser()

        
        val data = plugin.databaseManager.loadPlayerData(uuid.toString())

        if (data != null) {
            user.language = data["lang"] ?: "es"
            user.selectedKiller = data["killer_selected"] ?: "slasher"
            user.selectedSurvivor = data["survivor_selected"] ?: "civil"
            user.nickname = data["nick"] ?: ""
            user.skinName = data["skin_source"] ?: ""

            
            data["killers_owned"]?.split(",")?.filter { it.isNotBlank() }?.forEach { user.unlockedKillers.add(it.lowercase()) }
            data["survivors_owned"]?.split(",")?.filter { it.isNotBlank() }?.forEach { user.unlockedSurvivors.add(it.lowercase()) }

            
            user.unlockedKillers.add("slasher")
            user.unlockedSurvivors.add("civil")

        } else {
            
            saveDataAsync(uuid, user)
        }

        userDataCache[uuid] = user
    }

    /**
     * Guarda el estado actual del player en MySQL asíncronamente.
     */
    private fun saveDataAsync(uuid: UUID, user: MistakenUser? = null) {
        val u = user ?: userDataCache[uuid] ?: return

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.databaseManager.savePlayerDataRaw(
                uuid.toString(),
                u.language,
                u.unlockedKillers.joinToString(","),
                u.selectedKiller,
                u.unlockedSurvivors.joinToString(","),
                u.selectedSurvivor,
                u.nickname,
                u.skinName
            )
        }
    }

    
    fun saveAllSync() {
        userDataCache.forEach { (uuid, user) ->
            saveDataSync(uuid, user)
        }
    }

    fun unloadPlayer(uuid: UUID) {
        val user = userDataCache.remove(uuid) ?: return
        saveDataSync(uuid, user)
    }

    private fun saveDataSync(uuid: UUID, user: MistakenUser) {
        plugin.databaseManager.savePlayerDataRaw(
            uuid.toString(), user.language, user.unlockedKillers.joinToString(","), user.selectedKiller,
            user.unlockedSurvivors.joinToString(","), user.selectedSurvivor, user.nickname, user.skinName
        )
    }

    

    fun consumeStamina(uuid: UUID, amount: Double) {
        userDataCache[uuid]?.let { user ->
            user.stamina = (user.stamina - amount).coerceIn(0.0, 100.0)
        }
    }

    fun setLanguage(uuid: UUID, lang: String) {
        userDataCache[uuid]?.let { user ->
            user.language = lang.lowercase()
            saveDataAsync(uuid)
        }
    }

    

    override fun hasKiller(uuid: UUID, killerId: String): Boolean {
        val user = userDataCache[uuid] ?: return false
        return killerId.equals("slasher", true) || user.unlockedKillers.contains(killerId.lowercase())
    }

    override fun buyKiller(uuid: UUID, killerId: String) {
        userDataCache[uuid]?.let { user ->
            val id = killerId.lowercase()
            if (user.unlockedKillers.add(id)) {
                saveDataAsync(uuid)
            }
        }
    }

    fun getSelectedKiller(uuid: UUID): String = userDataCache[uuid]?.selectedKiller ?: "slasher"

    fun setSelectedKiller(uuid: UUID, killerId: String) {
        val user = userDataCache[uuid] ?: return
        if (hasKiller(uuid, killerId)) {
            val killer = killerId.lowercase()
            user.selectedKiller = killer
            saveDataAsync(uuid)
        }
    }

    

    override fun tieneSurvivor(uuid: UUID, survivorId: String): Boolean {
        val user = userDataCache[uuid] ?: return false
        return survivorId.equals("civil", true) || user.unlockedSurvivors.contains(survivorId.lowercase())
    }

    override fun comprarSurvivor(uuid: UUID, survivorId: String) {
        userDataCache[uuid]?.let { user ->
            val id = survivorId.lowercase()
            if (user.unlockedSurvivors.add(id)) {
                saveDataAsync(uuid)
            }
        }
    }

    fun getSelectedSurvivor(uuid: UUID): String = userDataCache[uuid]?.selectedSurvivor ?: "civil"

    fun setSelectedSurvivor(uuid: UUID, survivorId: String) {
        val user = userDataCache[uuid] ?: return
        if (tieneSurvivor(uuid, survivorId)) {
            user.selectedSurvivor = survivorId.lowercase()
            saveDataAsync(uuid)
        }
    }

    
    fun getStamina(uuid: UUID) = userDataCache[uuid]?.stamina ?: 100.0
    fun getLanguage(uuid: UUID) = userDataCache[uuid]?.language ?: "es"
    fun getUserData(uuid: UUID) = userDataCache[uuid]
}
