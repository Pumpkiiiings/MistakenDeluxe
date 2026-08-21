package liric.mistaken.listeners.mechanics

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService

/**
 * [LIRIC-MISTAKEN 2.0]
 * StaminaListener: Sistema de resistencia adaptado a MULTIARENA.
 * FIX: Recuperación sincronizada con el estado de cada sesión individual.
 */
class StaminaListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()

    @Volatile private var lossSurvivor = 2.0
    @Volatile private var lossKiller = 1.0
    @Volatile private var recoveryRate = 1.5
    private lateinit var exhaustedMsg: Component
    private lateinit var exhaustionEffect: PotionEffect

    private var staminaTask: ScheduledTask? = null

    init {
        loadConfigValues()
        startStaminaTask()
    }

    private fun loadConfigValues() {
        val config = plugin.config
        lossSurvivor = config.getDouble("stamina.loss-survivor", 2.0)
        lossKiller = config.getDouble("stamina.loss-killer", 1.0)
        recoveryRate = config.getDouble("stamina.recovery-rate", 1.5)

        val slownessLevel = (config.getInt("stamina.exhaustion-slowness-level", 2).coerceAtLeast(1) - 1)
        exhaustionEffect = PotionEffect(PotionEffectType.SLOWNESS, 80, slownessLevel, false, false, true)

        val rawExhausted = MessageService.getRawString(null, "stamina.exhausted", "<red><bold>¡AGOTADO!</bold></red>", "messages")
        exhaustedMsg = ColorTranslator.translate(rawExhausted)
    }

    private fun startStaminaTask() {
        staminaTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate

            for (player in plugin.server.onlinePlayers) {
                
                val session = plugin.sessionManager.getSession(player)

                
                if (session == null || session.currentState != GameState.INGAME) {
                    player.scheduler.execute(plugin, {
                        if (player.level != 100 || player.exp != 1.0f) {
                            player.level = 100
                            player.exp = 1.0f
                            if (player.foodLevel < 20) player.foodLevel = 20
                        }
                    }, null, 0L)
                    continue
                }

                
                if (player.gameMode != GameMode.SURVIVAL || plugin.isIgnored(player) || plugin.spectatorManager.isSpectator(player)) continue

                val uuid = player.uniqueId
                val user = plugin.playerDataManager.getUserData(uuid) ?: continue

                var currentStamina = user.stamina
                val isSprinting = player.isSprinting

                val isOneBounceSurvivor = session.currentMode == MistakenMode.ONE_BOUNCE && !session.isKiller(uuid)
                val maxStamina = if (isOneBounceSurvivor) 500.0 else 100.0
                val currentRecoveryRate = if (isOneBounceSurvivor) recoveryRate * 2 else recoveryRate

                
                if (isSprinting && currentStamina > 0.0) {
                    
                    var loss = if (session.isKiller(uuid)) lossKiller else lossSurvivor
                    if (session.currentMode == MistakenMode.ONE_BOUNCE && !session.isKiller(uuid)) {
                        loss /= 2.0
                    }
                    currentStamina = (currentStamina - loss).coerceAtLeast(0.0)
                } else if (currentStamina < maxStamina) {
                    
                    
                    val isExhausted = player.hasPotionEffect(PotionEffectType.SLOWNESS) &&
                        (player.getPotionEffect(PotionEffectType.SLOWNESS)?.amplifier ?: -1) >= 2
                    if (!isExhausted) {
                        currentStamina = (currentStamina + currentRecoveryRate).coerceAtMost(maxStamina)
                    }
                }

                user.stamina = currentStamina

                val justExhausted = currentStamina <= 0.0 && !player.hasPotionEffect(PotionEffectType.SLOWNESS)
                val newLevel = currentStamina.toInt()
                val newExpProgress = (currentStamina / maxStamina).toFloat().coerceIn(0.0f, 1.0f)

                player.scheduler.execute(plugin, {
                    if (player.isOnline) {
                        if (player.foodLevel < 20) player.foodLevel = 20

                        if (player.level != newLevel) player.level = newLevel
                        player.exp = newExpProgress

                        if (justExhausted) {
                            applyAgotamiento(player)
                        }

                        if (currentStamina in 1.0..25.0 && isSprinting) {
                            player.sendActionBar(MessageService.getComponent(player, "stamina.low_warning", Placeholder.parsed("level", newLevel.toString())))
                        }
                    }
                }, null, 0L)
            }
        }, 1L, 250L, TimeUnit.MILLISECONDS)
    }

    private fun applyAgotamiento(player: Player) {
        player.isSprinting = false
        player.addPotionEffect(exhaustionEffect)
        player.sendActionBar(exhaustedMsg)
        player.playSound(player.location, Sound.ENTITY_HORSE_BREATHE, 0.8f, 0.6f)
    }

    fun shutdown() {
        staminaTask?.cancel()
    }
}
