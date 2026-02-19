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

class StaminaListener(private val plugin: Mistaken) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
        slownessLevel = (config.getInt("stamina.exhaustion-slowness-level", 2).coerceAtLeast(1) - 1)

        val rawExhausted = plugin.messageConfig.getRawString(null, "stamina.exhausted", "<red><bold>¡AGOTADO!")
        exhaustedMsg = mm.deserialize(rawExhausted)
    }

    private fun startStaminaTask() {
        scope.launch {
            while (isActive) {
                if (!plugin.isReady) {
                    delay(1000L)
                    continue
                }

                if (plugin.gameManager.currentState != GameState.INGAME) {
                    withContext(plugin.mainThread) {
                        resetAllFood()
                    }
                    delay(2000L)
                    continue
                }

                val killers = plugin.gameManager.asesinosUUIDs
                val onlinePlayers = Bukkit.getOnlinePlayers()

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
                            withContext(plugin.mainThread) { aplicarAgotamiento(player) }
                        }
                    } else {
                        if (!player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                            currentStamina = (currentStamina + recoveryRate).coerceAtMost(100.0)
                        }
                    }

                    user.stamina = currentStamina

                    val newFoodLevel = (currentStamina / 5).toInt()
                    if (player.foodLevel != newFoodLevel || (currentStamina < 25 && isSprinting)) {
                        withContext(plugin.mainThread) {
                            if (player.isOnline) {
                                player.foodLevel = newFoodLevel
                                if (currentStamina < 25 && isSprinting) {
                                    player.sendActionBar(mm.deserialize("<red>ESTAMINA: ${currentStamina.toInt()}%"))
                                }
                            }
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    private fun aplicarAgotamiento(player: Player) {
        if (!player.isOnline) return
        player.isSprinting = false
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

    fun shutdown() {
        scope.cancel()
    }
}
