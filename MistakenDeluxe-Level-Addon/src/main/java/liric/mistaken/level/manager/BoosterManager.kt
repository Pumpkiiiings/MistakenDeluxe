package liric.mistaken.level.manager

import liric.mistaken.level.LevelAddonPlugin
import liric.mistaken.level.database.BoosterData
import java.util.UUID

class BoosterManager(private val plugin: LevelAddonPlugin) {
    private val activeBoosters = mutableMapOf<String, BoosterData>()

    fun loadAll() {
        val loaded = plugin.boosterRepository.loadAll()
        val now = System.currentTimeMillis()
        for (booster in loaded) {
            if (booster.expiry > now) {
                activeBoosters[booster.uuid] = booster
            } else {
                plugin.boosterRepository.delete(booster.uuid)
            }
        }
    }

    fun giveBooster(uuid: String, multiplier: Double, durationMillis: Long) {
        val expiry = System.currentTimeMillis() + durationMillis
        val booster = BoosterData(uuid, multiplier, expiry)
        activeBoosters[uuid] = booster
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            plugin.boosterRepository.save(booster)
        })
    }

    fun getMultiplier(uuid: UUID): Double {
        return getBestBooster(uuid)?.multiplier ?: 1.0
    }

    fun getBestBooster(uuid: UUID): BoosterData? {
        cleanExpired()
        val global = activeBoosters["global"]
        val personal = activeBoosters[uuid.toString()]
        
        if (global == null) return personal
        if (personal == null) return global
        
        return if (global.multiplier >= personal.multiplier) global else personal
    }

    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        val iterator = activeBoosters.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.expiry <= now) {
                iterator.remove()
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    plugin.boosterRepository.delete(entry.key)
                })
            }
        }
    }
}
