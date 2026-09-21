package liric.mistaken.level.manager

import liric.mistaken.level.LevelAddonPlugin
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.util.UUID

class BoosterBossBarManager(private val plugin: LevelAddonPlugin) : Runnable {

    private val bossBars = mutableMapOf<UUID, BossBar>()
    private val mm = MiniMessage.miniMessage()

    override fun run() {
        val now = System.currentTimeMillis()
        for (player in Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId
            val booster = plugin.boosterManager.getBestBooster(uuid)

            if (booster != null) {
                val remainingMillis = booster.expiry - now
                if (remainingMillis > 0) {
                    val remainingSeconds = (remainingMillis / 1000).toInt()
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)

                    val msgRaw = plugin.messagesConfig.getString("booster.bossbar", "<gold><bold>⚡ BOOSTER</bold> <yellow>%multiplier%x <gray>| <white>%time%")!!
                    val msg = msgRaw.replace("%multiplier%", booster.multiplier.toString()).replace("%time%", timeStr)

                    var bossBar = bossBars[uuid]
                    if (bossBar == null) {
                        bossBar = BossBar.bossBar(
                            mm.deserialize(msg),
                            1.0f,
                            BossBar.Color.YELLOW,
                            BossBar.Overlay.PROGRESS
                        )
                        bossBars[uuid] = bossBar
                        player.showBossBar(bossBar)
                    } else {
                        bossBar.name(mm.deserialize(msg))
                        // Update progress? We don't know total time. We could just leave it at 1.0, or animate it if we store total duration.
                        // Let's just keep it at 1.0f for now since we only have expiry.
                    }
                } else {
                    removeBossBar(uuid)
                }
            } else {
                removeBossBar(uuid)
            }
        }
    }

    fun removeBossBar(uuid: UUID) {
        val bossBar = bossBars.remove(uuid)
        if (bossBar != null) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                player.hideBossBar(bossBar)
            }
        }
    }
}
