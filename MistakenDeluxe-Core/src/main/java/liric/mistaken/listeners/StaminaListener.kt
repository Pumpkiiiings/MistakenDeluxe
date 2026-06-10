package liric.mistaken.listeners

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

        val rawExhausted = plugin.messageConfig.getRawString(null, "stamina.exhausted", "<red><bold>¡AGOTADO!</bold></red>")
        exhaustedMsg = mm.deserialize(rawExhausted)
    }

    private fun startStaminaTask() {
        staminaTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate

            for (player in plugin.server.onlinePlayers) {
                // 🔥 MULTIARENA: Buscamos la sesión del jugador
                val session = plugin.sessionManager.getSession(player)

                // Si no tiene sesión o no están en juego, resetear barra de exp
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

                // LÓGICA IN-GAME
                if (player.gameMode != GameMode.SURVIVAL || plugin.isIgnored(player) || plugin.spectatorManager.isSpectator(player)) continue

                val uuid = player.uniqueId
                val user = plugin.playerDataManager.getUserData(uuid) ?: continue

                var currentStamina = user.stamina
                val isSprinting = player.isSprinting

                // --- Cálculo de pérdida/recuperación ---
                if (isSprinting && currentStamina > 0.0) {
                    // 🔥 MULTIARENA: Verificamos si es asesino en SU sesión
                    var loss = if (session.esAsesino(uuid)) lossKiller else lossSurvivor
                    if (session.currentMode == liric.mistaken.game.enums.MistakenMode.ONE_BOUNCE && !session.esAsesino(uuid)) {
                        loss /= 2.0
                    }
                    currentStamina = (currentStamina - loss).coerceAtLeast(0.0)
                } else if (currentStamina < 100.0) {
                    // Recupera estamina siempre que no esté corriendo
                    // (SLOWNESS no debe bloquear la regeneración, solo el agotamiento activo lo hace)
                    val isExhausted = player.hasPotionEffect(PotionEffectType.SLOWNESS) &&
                        (player.getPotionEffect(PotionEffectType.SLOWNESS)?.amplifier ?: -1) >= 2
                    if (!isExhausted) {
                        currentStamina = (currentStamina + recoveryRate).coerceAtMost(100.0)
                    }
                }

                user.stamina = currentStamina

                val justExhausted = currentStamina <= 0.0 && !player.hasPotionEffect(PotionEffectType.SLOWNESS)
                val newLevel = currentStamina.toInt()
                val newExpProgress = (currentStamina / 100.0).toFloat().coerceIn(0.0f, 1.0f)

                player.scheduler.execute(plugin, {
                    if (player.isOnline) {
                        if (player.foodLevel < 20) player.foodLevel = 20

                        if (player.level != newLevel) player.level = newLevel
                        player.exp = newExpProgress

                        if (justExhausted) {
                            aplicarAgotamiento(player)
                        }

                        if (currentStamina in 1.0..25.0 && isSprinting) {
                            player.sendActionBar(mm.deserialize("<red>ESTAMINA BAJA: $newLevel%</red>"))
                        }
                    }
                }, null, 0L)
            }
        }, 1L, 250L, TimeUnit.MILLISECONDS)
    }

    private fun aplicarAgotamiento(player: Player) {
        player.isSprinting = false
        player.addPotionEffect(exhaustionEffect)
        player.sendActionBar(exhaustedMsg)
        player.playSound(player.location, Sound.ENTITY_HORSE_BREATHE, 0.8f, 0.6f)
    }

    fun shutdown() {
        staminaTask?.cancel()
    }
}
