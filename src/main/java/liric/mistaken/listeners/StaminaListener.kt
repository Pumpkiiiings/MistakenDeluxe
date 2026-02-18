package liric.mistaken.listeners

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

/**
 * [LIRIC-MISTAKEN 2.0]
 * StaminaListener: Gestión de energía física ultra-optimizada.
 * Usa Coroutines para procesar la lógica fuera del hilo principal.
 */
class StaminaListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Cache de Configuración
    private var lossSurvivor = 2.0
    private var lossKiller = 1.0
    private var recoveryRate = 1.5
    private var slownessLevel = 1
    private lateinit var exhaustedMsg: Component

    init {
        loadConfigValues()
        startStaminaTask()
    }

    private fun loadConfigValues() {
        val config = plugin.config
        lossSurvivor = config.getDouble("stamina.loss-survivor", 2.0)
        lossKiller = config.getDouble("stamina.loss-killer", 1.0)
        recoveryRate = config.getDouble("stamina.recovery-rate", 1.5)
        slownessLevel = config.getInt("stamina.exhaustion-slowness-level", 2).coerceAtLeast(1) - 1

        val rawExhausted = plugin.messageConfig.getRawString(null, "stamina.exhausted", "<red><bold>¡AGOTADO!")
        exhaustedMsg = mm.deserialize(rawExhausted)
    }

    private fun startStaminaTask() {
        scope.launch {
            while (isActive) {
                // 1. Verificación de estado rápida
                if (plugin.gameManager.currentState != GameState.INGAME) {
                    // Si no hay juego, resetear comida en el hilo principal
                    withContext(Dispatchers.Main) {
                        resetAllFood()
                    }
                    delay(2000L) // Esperar 2 segundos antes de checar otra vez
                    continue
                }

                val killers = plugin.gameManager.asesinosUUIDs
                val onlinePlayers = Bukkit.getOnlinePlayers()

                // 2. Procesar lógica en paralelo (Hilo secundario)
                onlinePlayers.forEach { player ->
                    if (player.gameMode != GameMode.SURVIVAL || plugin.isIgnored(player)) return@forEach

                    val uuid = player.uniqueId
                    val user = plugin.playerDataManager.getUserData(uuid) ?: return@forEach

                    var currentStamina = user.stamina
                    val isSprinting = player.isSprinting

                    if (isSprinting) {
                        val loss = if (killers.contains(uuid)) lossKiller else lossSurvivor
                        currentStamina = (currentStamina - loss).coerceAtLeast(0.0)

                        if (currentStamina <= 0.0) {
                            // Aplicar agotamiento requiere hilo principal
                            withContext(Dispatchers.Main) { aplicarAgotamiento(player) }
                        }
                    } else {
                        // Regeneración: No regenera si tiene lentitud (debuff de agotamiento)
                        if (!player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                            currentStamina = (currentStamina + recoveryRate).coerceAtMost(100.0)
                        }
                    }

                    // Guardar valor calculado
                    user.stamina = currentStamina

                    // 3. Actualización Visual (Main Thread Sync)
                    // Solo llamamos a Bukkit si los valores realmente cambiaron
                    val newFoodLevel = (currentStamina / 5).toInt()
                    if (player.foodLevel != newFoodLevel || (currentStamina < 25 && isSprinting)) {
                        withContext(Dispatchers.Main) {
                            if (player.isOnline) {
                                player.foodLevel = newFoodLevel
                                if (currentStamina < 25 && isSprinting) {
                                    player.sendActionBar(mm.deserialize("<red>ESTAMINA: ${currentStamina.toInt()}%"))
                                }
                            }
                        }
                    }
                }

                delay(250L) // Equivalente a 5 ticks (1/4 de segundo)
            }
        }
    }

    private fun aplicarAgotamiento(player: Player) {
        if (!player.isOnline) return

        player.isSprinting = false
        // Paper 1.21.4 Potion API
        player.addPotionEffect(
            PotionEffect(PotionEffectType.SLOWNESS, 80, slownessLevel, false, false, true)
        )

        player.sendActionBar(exhaustedMsg)
        player.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 0.8f, 0.8f)
    }

    private fun resetAllFood() {
        Bukkit.getOnlinePlayers().forEach { p ->
            if (p.foodLevel < 20) p.foodLevel = 20
        }
    }

    /**
     * Cancela la corrutina al apagar el plugin
     */
    fun cancelTask() {
        scope.cancel()
    }
}
