package liric.mistaken.api.level

import liric.mistaken.api.MistakenProvider
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object LevelAPI {
    private val levelProvider: LevelProvider?
        get() = Bukkit.getServicesManager().load(LevelProvider::class.java)

    private val mistakenAPI
        get() = MistakenProvider.get()

    fun getLevel(player: Player): Int {
        return levelProvider?.getLevel(player.uniqueId) ?: 1
    }

    fun addExperience(player: Player, amount: Long) {
        levelProvider?.addExperience(player.uniqueId, amount)
    }

    fun setLevel(player: Player, level: Int) {
        levelProvider?.setLevel(player.uniqueId, level)
    }

    fun unlockKiller(player: Player, killerId: String) {
        mistakenAPI.playerDataManager.comprarAsesino(player.uniqueId, killerId)
    }

    fun unlockSurvivor(player: Player, survivorId: String) {
        mistakenAPI.playerDataManager.comprarSuperviviente(player.uniqueId, survivorId)
    }

    fun hasUnlockedKiller(player: Player, killerId: String): Boolean {
        return mistakenAPI.playerDataManager.tieneAsesino(player.uniqueId, killerId)
    }

    fun hasUnlockedSurvivor(player: Player, survivorId: String): Boolean {
        return mistakenAPI.playerDataManager.tieneSuperviviente(player.uniqueId, survivorId)
    }
}
