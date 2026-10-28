package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.modes.ModeHandler
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.utils.hooks.ObserverHook
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration

class LightsOutModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {

    private val darknessEffect = PotionEffect(
        PotionEffectType.DARKNESS,
        Int.MAX_VALUE,
        0,
        false,
        false,
        true
    )

    override fun onGameStart() {
        val title = ColorTranslator.translate("<bold><#FF5500>¡LUCES FUERA!</#FF5500></bold>")
        val subtitle = ColorTranslator.translate("<gray>La oscuridad ha consumido todo... ¡Nadie puede ver!</gray>")
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        val titleObj = Title.title(title, subtitle, times)

        session.getPlayers().forEach { player ->
            if (!plugin.spectatorManager.isSpectator(player)) {
                applyLightsOutEffects(player)
                player.showTitle(titleObj)
                player.playSound(player.location, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.6f)
                player.playSound(player.location, Sound.AMBIENT_CAVE, 1.0f, 0.5f)
            }
        }
    }

    override fun onPlayerSpawn(player: Player, isKiller: Boolean) {
        if (session.currentState == GameState.INGAME && !plugin.spectatorManager.isSpectator(player)) {
            applyLightsOutEffects(player)
        }
    }

    override fun checkSpecialSpawn(timer: Int): Boolean {
        // Cada 3 segundos comprobamos que todos los jugadores activos mantengan el efecto de oscuridad
        if (timer % 3 == 0) {
            session.getPlayers().forEach { player ->
                if (!plugin.spectatorManager.isSpectator(player) && player.isValid) {
                    if (!player.hasPotionEffect(PotionEffectType.DARKNESS)) {
                        player.addPotionEffect(darknessEffect)
                    }
                    ObserverHook.setTrueDarkness(player, true)
                }
            }
        }
        return false
    }

    override fun onGameEnd(killerWon: Boolean) {
        session.getPlayers().forEach { player ->
            removeLightsOutEffects(player)
        }
    }

    override fun onPlayerDeath(player: Player): Boolean {
        removeLightsOutEffects(player)
        return false // Continúa con la lógica de muerte habitual (espectador)
    }

    private fun applyLightsOutEffects(player: Player) {
        player.addPotionEffect(darknessEffect)
        ObserverHook.setTrueDarkness(player, true)
    }

    private fun removeLightsOutEffects(player: Player) {
        player.removePotionEffect(PotionEffectType.DARKNESS)
        ObserverHook.setTrueDarkness(player, false)
    }
}
