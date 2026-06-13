package liric.mistaken.game.managers.cinematic

import liric.mistaken.Mistaken
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import java.util.function.Consumer

class CinematicCamera(private val plugin: Mistaken) {

    fun safeSetSpectatorTarget(player: Player, target: Entity?) {
        if (!player.isOnline) return
        if (player.gameMode == GameMode.SPECTATOR) {
            try {
                if (player.spectatorTarget != target) {
                    player.spectatorTarget = target
                }
            } catch (ignored: Exception) {
            }
        }
    }

    fun iniciarCamaraDinamica(
        killer: Player,
        anchor: ArmorStand,
        dummy: ArmorStand,
        center: Location,
        profile: CinematicProfile,
        isIntro: Boolean,
        duracionTicks: Int,
        displayManager: DisplayManager,
        onFinish: () -> Unit
    ) {
        var ticks = 0

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= duracionTicks || !killer.isOnline || !anchor.isValid) {
                plugin.server.onlinePlayers.forEach {
                    safeSetSpectatorTarget(it, null)
                    it.removePotionEffect(PotionEffectType.NAUSEA)
                    it.removePotionEffect(PotionEffectType.BLINDNESS)
                    if (it == killer) it.isInvisible = false
                }
                anchor.remove()
                dummy.remove()
                displayManager.clearDisplays()

                onFinish()
                task.cancel()
                return@Consumer
            }

            // ANTI-SHIFT SEGURO
            plugin.server.onlinePlayers.forEach { p ->
                safeSetSpectatorTarget(p, anchor)
            }

            val camLoc = center.clone()
            
            profile.processCameraTick(camLoc, center, dummy, ticks, isIntro, plugin)

            anchor.teleport(camLoc)
            ticks++
        }, 1L, 1L)
    }
}
