package liric.mistaken.listeners

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.mainThread
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

class StaminaListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Cache de configuraciones
    private var lossSurvivor = 2.0
    private var lossKiller = 1.0
    private var recoveryRate = 1.5
    private var slownessLevel = 0
    private lateinit var exhaustedMsg: Component

    // PotionEffect cacheado (evita crear miles de objetos por segundo)
    private lateinit var exhaustionEffect: PotionEffect

    init {
        loadConfigValues()
        startStaminaTask()
    }

    private fun loadConfigValues() {
        val config = plugin.config
        lossSurvivor = config.getDouble("stamina.loss-survivor", 2.0)
        lossKiller = config.getDouble("stamina.loss-killer", 1.0)
        recoveryRate = config.getDouble("stamina.recovery-rate", 1.5)
        slownessLevel = (config.getInt("stamina.exhaustion-slowness-level", 2).coerceAtLeast(1) - 1)

        val rawExhausted = plugin.messageConfig.getRawString(null, "stamina.exhausted", "<red><bold>¡AGOTADO!")
        exhaustedMsg = mm.deserialize(rawExhausted)

        // Inicializar el efecto de poción una sola vez
        exhaustionEffect = PotionEffect(PotionEffectType.SLOWNESS, 80, slownessLevel, false, false, true)
    }

    private fun startStaminaTask() {
        scope.launch {
            while (isActive) {
                // 1. Espera de seguridad
                if (!plugin.isReady) { delay(1000L); continue }

                // 2. Si no estamos en partida, reseteamos comida de forma eficiente (cada 2 seg)
                if (plugin.gameManager.currentState != GameState.INGAME) {
                    withContext(plugin.mainThread) {
                        Bukkit.getOnlinePlayers().forEach { if (it.foodLevel < 20) it.foodLevel = 20 }
                    }
                    delay(2000L)
                    continue
                }

                // --- PROCESAMIENTO BATCH (OPTIMIZADO) ---
                // Entramos al hilo principal UNA SOLA VEZ para leer y escribir todo
                withContext(plugin.mainThread) {
                    val killers = plugin.gameManager.asesinosUUIDs
                    val onlinePlayers = Bukkit.getOnlinePlayers()

                    for (player in onlinePlayers) {
                        // Filtros rápidos
                        if (player.gameMode != GameMode.SURVIVAL || plugin.isIgnored(player)) continue

                        val uuid = player.uniqueId
                        val user = plugin.playerDataManager.getUserData(uuid) ?: continue

                        var currentStamina = user.stamina
                        val isSprinting = player.isSprinting

                        // Lógica de estamina
                        if (isSprinting) {
                            val loss = if (killers.contains(uuid)) lossKiller else lossSurvivor
                            currentStamina = (currentStamina - loss).coerceAtLeast(0.0)

                            if (currentStamina <= 0.0) {
                                aplicarAgotamiento(player)
                            }
                        } else {
                            // Solo recupera si no tiene el efecto de lentitud (agotamiento)
                            if (!player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                                currentStamina = (currentStamina + recoveryRate).coerceAtMost(100.0)
                            }
                        }

                        // Guardar en data
                        user.stamina = currentStamina

                        // Actualizar Food Level (La barra de estamina visual)
                        val newFoodLevel = (currentStamina / 5).toInt()
                        if (player.foodLevel != newFoodLevel) {
                            player.foodLevel = newFoodLevel
                        }

                        // ActionBar (Solo si es necesario para evitar spam de paquetes)
                        if (currentStamina < 25 && isSprinting) {
                            player.sendActionBar(mm.deserialize("<red>ESTAMINA: ${currentStamina.toInt()}%"))
                        }
                    }
                }

                // Pausa de 5 ticks (250ms es perfecto para estamina)
                delay(250L)
            }
        }
    }

    /**
     * Ya no necesita withContext porque se llama desde el bucle que ya está en el Main Thread.
     */
    private fun aplicarAgotamiento(player: Player) {
        player.isSprinting = false
        player.addPotionEffect(exhaustionEffect)
        player.sendActionBar(exhaustedMsg)
        player.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 0.8f, 0.8f)
    }

    fun shutdown() {
        scope.cancel()
    }
}
