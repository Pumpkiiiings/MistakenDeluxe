package liric.mistaken.level.manager

import liric.mistaken.level.LevelAddonPlugin
import liric.mistaken.level.database.BoosterData
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.util.UUID

class BoosterManager(private val plugin: LevelAddonPlugin) {
    private val activeBoosters = mutableMapOf<String, BoosterData>()
    private val activeBossBars = mutableMapOf<UUID, BossBar>()
    
    init {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            updateBossBars()
        }, 20L, 20L)
    }

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
        val booster = BoosterData(uuid, multiplier, expiry, durationMillis)
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

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateBossBars() {
        val mm = MiniMessage.miniMessage()
        val bossbarMsgRaw = plugin.messagesConfig.getString("messages.booster-bossbar", "<gradient:#ff0000:#ffff00>Tienes un booster de %multiplier%x por %time%</gradient>")!!
        
        for (player in Bukkit.getOnlinePlayers()) {
            val bestBooster = getBestBooster(player.uniqueId)
            val currentBossBar = activeBossBars[player.uniqueId]

            if (bestBooster == null) {
                if (currentBossBar != null) {
                    player.hideBossBar(currentBossBar)
                    activeBossBars.remove(player.uniqueId)
                }
                continue
            }

            val remainingTime = bestBooster.expiry - System.currentTimeMillis()
            if (remainingTime <= 0) {
                if (currentBossBar != null) {
                    player.hideBossBar(currentBossBar)
                    activeBossBars.remove(player.uniqueId)
                }
                continue
            }

            val msg = bossbarMsgRaw
                .replace("%multiplier%", bestBooster.multiplier.toString())
                .replace("%time%", formatTime(remainingTime))
            
            val component = mm.deserialize(msg)
            
            var progress = 1.0f
            if (bestBooster.totalDuration > 0) {
                progress = remainingTime.toFloat() / bestBooster.totalDuration.toFloat()
            }
            progress = maxOf(0.0f, minOf(1.0f, progress))

            if (currentBossBar == null) {
                val newBossBar = BossBar.bossBar(component, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS)
                player.showBossBar(newBossBar)
                activeBossBars[player.uniqueId] = newBossBar
            } else {
                currentBossBar.name(component)
                currentBossBar.progress(progress)
            }
        }
    }
}
