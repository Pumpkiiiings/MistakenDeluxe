package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.api.HealthAPI
import liric.mistaken.api.events.MistakenDeathEvent
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * [LIRIC-MISTAKEN 2.0]
 * CombatManager: Motor de Daño y Radar de Latidos (NERFED).
 */
class CombatManager(private val plugin: Mistaken) : Listener, HealthAPI {

    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val originalHelmets = ConcurrentHashMap<UUID, ItemStack>()
    private val killerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val freezeDeathJobs = ConcurrentHashMap<UUID, Job>()

    private val combatScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cooldownMs = 2000L
    private val mm = plugin.mm

    init {
        startRadarTask()
    }

    /**
     * 🛰️ MOTOR DE RASTREO (VERSIÓN NERFEADA):
     * - Glow: 10 bloques.
     * - ActionBar: Amarillo, sin distancia.
     * - Sonido: Intensidad escalada por proximidad.
     */
    private fun startRadarTask() {
        combatScope.launch {
            while (isActive && !plugin.isReady) delay(1000)

            while (isActive) {
                if (plugin.gameManager.currentState == GameState.INGAME) {
                    val killer = plugin.gameManager.getCurrentAsesino()

                    if (killer != null && killer.isOnline) {
                        val killerLoc = killer.location
                        var minDistance = Double.MAX_VALUE
                        var foundSomeone = false

                        withContext(plugin.bukkitDispatcher) {
                            Bukkit.getOnlinePlayers().forEach { target ->
                                if (target.uniqueId != killer.uniqueId &&
                                    target.gameMode == GameMode.SURVIVAL &&
                                    !plugin.isIgnored(target)) {

                                    val dist = killerLoc.distance(target.location)

                                    // 1. 🔥 NERF: GLOW SOLO A 10 BLOQUES
                                    if (dist <= 10.0) {
                                        target.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 30, 0, false, false, false))
                                    }

                                    // 2. DETECCIÓN PARA LATIDOS (Máximo 30m)
                                    if (dist <= 30.0) {
                                        if (dist < minDistance) {
                                            minDistance = dist
                                            foundSomeone = true
                                        }
                                    }
                                }
                            }

                            // 3. FEEDBACK SENSORIAL ESCALADO
                            if (foundSomeone) {
                                // 🔥 NERF: ActionBar Amarilla y sin mostrar los metros
                                killer.sendActionBar(mm.deserialize(
                                    "<yellow>Escuchas el latido de alguien.."
                                ))

                                // 🔥 MEJORA: Latido Progresivo (Más intenso entre más cerca)
                                // Volumen: 0.4 a 1.2 | Pitch: 0.5 a 1.5
                                val volume = when {
                                    minDistance < 5.0 -> 1.2f
                                    minDistance < 15.0 -> 0.8f
                                    else -> 0.4f
                                }
                                val pitch = when {
                                    minDistance < 5.0 -> 1.5f
                                    minDistance < 15.0 -> 1.0f
                                    else -> 0.6f
                                }

                                killer.playSound(killer.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, volume, pitch)
                            }
                        }
                    }
                }
                delay(500) // 10 Ticks
            }
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) block()
        else combatScope.launch(plugin.bukkitDispatcher) { block() }
    }

    // --- MÉTODOS DE LA API (SE MANTIENEN IGUAL) ---

    override fun getHealth(player: Player): Int = player.health.toInt()

    override fun setHealth(player: Player, health: Int) {
        runOnMain {
            val maxHP = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            player.health = health.toDouble().coerceIn(0.0, maxHP)
            if (plugin.isReady) plugin.scoreboardManager.updatePlayer(player)
        }
    }

    override fun isFrozen(player: Player): Boolean = frozenPlayers.contains(player.uniqueId)

    override fun resetPlayer(player: Player) {
        removePlayerData(player.uniqueId)
        resetHealth(player)
    }

    fun resetHealth(player: Player) {
        val isKiller = plugin.gameManager.esAsesino(player.uniqueId)
        runOnMain {
            val maxHP = if (isKiller) 160.0 else 20.0
            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHP
            player.health = maxHP
            player.removePotionEffect(PotionEffectType.DARKNESS)
            player.isSwimming = false
            if (plugin.isReady) plugin.scoreboardManager.updatePlayer(player)
        }
    }

    fun processTrueDamage(victim: Player, attacker: Player?, amount: Double) {
        if (isFrozen(victim)) return
        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)
        runOnMain {
            val nextHP = (victim.health - amount).coerceAtLeast(0.0)
            victim.health = nextHP
            if (!isVictimKiller && nextHP <= 4.0 && nextHP > 0.0) {
                if (!victim.hasPotionEffect(PotionEffectType.DARKNESS)) {
                    val rawMsg = plugin.messageConfig.getRawString(victim, "combat.critical-wound", "<red><bold>¡TUS PIERNAS FALLAN!</bold>")
                    victim.sendMessage(mm.deserialize(rawMsg))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, Int.MAX_VALUE, 0, false, false, true))
                }
            }
            victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)
            victim.world.spawnParticle(Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, Material.REDSTONE_BLOCK.createBlockData())
            if (plugin.isReady) plugin.scoreboardManager.updatePlayer(victim)
            if (nextHP <= 0.0) handleDeath(victim, false)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArenaCombat(event: EntityDamageByEntityEvent) {
        if (event.entity.hasMetadata("mistaken_processing")) return
        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return
        val isAttackerKiller = plugin.gameManager.esAsesino(attacker.uniqueId)
        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        if (isAttackerKiller && !isVictimKiller) {
            val now = System.currentTimeMillis()
            if (now - killerCooldowns.getOrDefault(attacker.uniqueId, 0L) < cooldownMs) {
                event.isCancelled = true
                return
            }
            killerCooldowns[attacker.uniqueId] = now
            event.isCancelled = true
            processTrueDamage(victim, attacker, 3.0)
            return
        }
        if (!isAttackerKiller && isVictimKiller) {
            event.isCancelled = true
            victim.setMetadata("mistaken_processing", FixedMetadataValue(plugin, true))
            processTrueDamage(victim, attacker, 4.0)
            victim.removeMetadata("mistaken_processing", plugin)
            return
        }
    }

    fun removePlayerData(uuid: UUID) {
        val p = Bukkit.getPlayer(uuid)
        p?.let {
            resetFreezeState(it)
            it.removePotionEffect(PotionEffectType.DARKNESS)
            it.isSwimming = false
        }
        killerCooldowns.remove(uuid)
        frozenPlayers.remove(uuid)
        originalHelmets.remove(uuid)
        freezeDeathJobs.remove(uuid)?.cancel()
    }

    fun clearAll() {
        frozenPlayers.toList().forEach { uuid -> Bukkit.getPlayer(uuid)?.let { resetFreezeState(it) } }
        frozenPlayers.clear()
        originalHelmets.clear()
        killerCooldowns.clear()
        freezeDeathJobs.values.forEach { it.cancel() }
        freezeDeathJobs.clear()
    }

    private fun handleDeath(victim: Player, isHypothermia: Boolean) {
        runOnMain {
            victim.removePotionEffect(PotionEffectType.DARKNESS)
            liric.mistaken.utils.SpectatorUtils.setSafeSpectator(victim)
            victim.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            frozenPlayers.remove(victim.uniqueId)
        }
        if (plugin.gameManager.esAsesino(victim.uniqueId)) {
            giveWinRewards(false)
            plugin.gameManager.endGame("game.killer-died-victory", false)
            return
        }
        val killer = plugin.gameManager.getCurrentAsesino()
        if (killer != null) {
            Bukkit.getPluginManager().callEvent(MistakenDeathEvent(victim, killer))
            plugin.pluginScope.launch(Dispatchers.IO) {
                plugin.playerStatsManager.addStat(killer.uniqueId, killer.name, "kills")
                plugin.playerStatsManager.addStat(victim.uniqueId, victim.name, "deaths")
            }
            plugin.gameManager.addTime(15)
        }
        runOnMain {
            val deathPath = if (isHypothermia) "game.player-frozen-death" else "game.player-died"
            plugin.gameManager.broadcastLocalized(deathPath, Placeholder.parsed("player", victim.name))
            victim.playSound(victim.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)
            plugin.gameManager.checkWinCondition()
        }
    }

    override fun unfreeze(victim: Player, rescuer: Player) {
        if (!frozenPlayers.remove(victim.uniqueId)) return
        runOnMain {
            victim.removePotionEffect(PotionEffectType.DARKNESS)
            victim.isSwimming = false
            resetFreezeState(victim)
            victim.health = 10.0
            victim.world.playSound(victim.location, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.5f)
        }
    }

    private fun resetFreezeState(player: Player) {
        freezeDeathJobs.remove(player.uniqueId)?.cancel()
        player.clearTitle()
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.42
        originalHelmets.remove(player.uniqueId)?.let { player.inventory.helmet = it } ?: run { player.inventory.helmet = null }
    }

    fun giveWinRewards(killerWon: Boolean) {
        val killers = plugin.gameManager.asesinosUUIDs
        val winners = if (killerWon) Bukkit.getOnlinePlayers().filter { killers.contains(it.uniqueId) }
        else Bukkit.getOnlinePlayers().filter { !killers.contains(it.uniqueId) && it.gameMode != GameMode.SPECTATOR }
        combatScope.launch(Dispatchers.IO) {
            winners.forEach { Mistaken.economy?.depositPlayer(it, if (killerWon) 500.0 else 200.0) }
        }
    }

    fun soltarPasajero(vehicle: Player) {
        vehicle.passengers.forEach {
            vehicle.removePassenger(it)
            if (it is Player && it.health <= 4.0) it.isSwimming = true
        }
    }

    private fun handleFreeze(victim: Player) {
        if (!frozenPlayers.add(victim.uniqueId)) return
        runOnMain {
            val helmet = victim.inventory.helmet
            if (helmet != null && helmet.type != Material.AIR) {
                originalHelmets[victim.uniqueId] = helmet
            }
            victim.inventory.helmet = ItemStack(Material.BLUE_ICE)
            victim.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.0
            victim.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.0
            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false))
            victim.world.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
            startFreezeTimer(victim)
            plugin.gameManager.broadcastLocalized("game.player-frozen", Placeholder.parsed("player", victim.name))
            plugin.gameManager.checkWinCondition()
        }
    }

    private fun startFreezeTimer(victim: Player) {
        val job = combatScope.launch {
            var timeLeft = 120
            while (isActive && timeLeft > 0 && isFrozen(victim)) {
                withContext(plugin.bukkitDispatcher) {
                    if (victim.isOnline) {
                        val timeFormatted = String.format("%d:%02d", timeLeft / 60, timeLeft % 60)
                        victim.showTitle(Title.title(plugin.messageConfig.getMessage(victim, "game.freeze-title"),
                            plugin.messageConfig.getMessage(victim, "game.freeze-subtitle", Placeholder.parsed("time", timeFormatted))))
                    }
                }
                delay(1000L); timeLeft--
            }
            if (timeLeft <= 0 && isFrozen(victim)) runOnMain { handleDeath(victim, true) }
        }
        freezeDeathJobs[victim.uniqueId] = job
    }

    fun shutdown() { clearAll(); combatScope.cancel() }
    override fun takeDamage(victim: Player) { processTrueDamage(victim, null, 3.0) }
}
