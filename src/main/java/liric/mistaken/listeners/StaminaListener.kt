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

/**
 *[LIRIC-MISTAKEN 2.0]
 * StaminaListener: Sistema de resistencia en la Barra de Experiencia.
 * FIX: Reparado el bucle infinito de agotamiento para que la estamina sí se regenere.
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
        // La poción dura 80 ticks (4 segundos)
        exhaustionEffect = PotionEffect(PotionEffectType.SLOWNESS, 80, slownessLevel, false, false, true)

        val rawExhausted = plugin.messageConfig.getRawString(null, "stamina.exhausted", "<red><bold>¡AGOTADO!</bold></red>")
        exhaustedMsg = mm.deserialize(rawExhausted)
    }

    private fun startStaminaTask() {
        staminaTask = plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate

            if (plugin.gameManager.currentState != GameState.INGAME) {
                plugin.server.globalRegionScheduler.execute(plugin, Runnable {
                    plugin.server.onlinePlayers.forEach { p ->
                        if (p.foodLevel < 20) p.foodLevel = 20
                        if (p.level != 100 || p.exp != 1.0f) {
                            p.level = 100
                            p.exp = 1.0f
                        }
                    }
                })
                return@runAtFixedRate
            }

            val killers = plugin.gameManager.asesinosUUIDs

            for (player in plugin.server.onlinePlayers) {
                if (player.gameMode != GameMode.SURVIVAL || plugin.isIgnored(player)) continue

                val uuid = player.uniqueId
                val user = plugin.playerDataManager.getUserData(uuid) ?: continue

                var currentStamina = user.stamina
                val isSprinting = player.isSprinting

                // --- Lógica matemática ---
                if (isSprinting && currentStamina > 0.0) {
                    val loss = if (killers.contains(uuid)) lossKiller else lossSurvivor
                    currentStamina = (currentStamina - loss).coerceAtLeast(0.0)
                } else {
                    // Solo recupera si NO tiene lentitud
                    if (!player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                        currentStamina = (currentStamina + recoveryRate).coerceAtMost(100.0)
                    }
                }

                user.stamina = currentStamina

                // 🔥 EL FIX MAESTRO:
                // Solo disparamos el agotamiento si la estamina es 0 Y el jugador AÚN NO tiene la poción.
                // Si ya tiene la poción, no hacemos nada y dejamos que el tiempo de la poción corra.
                val justExhausted = currentStamina <= 0.0 && !player.hasPotionEffect(PotionEffectType.SLOWNESS)

                val newLevel = currentStamina.toInt()
                val newExpProgress = (currentStamina / 100.0).toFloat().coerceIn(0.0f, 1.0f)

                player.scheduler.execute(plugin, Runnable {
                    if (player.isOnline) {
                        if (player.foodLevel < 20) player.foodLevel = 20

                        if (player.level != newLevel) player.level = newLevel
                        player.exp = newExpProgress

                        // Aplicar castigo solo 1 vez
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
