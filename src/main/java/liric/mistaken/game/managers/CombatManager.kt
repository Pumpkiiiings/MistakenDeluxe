package liric.mistaken.game.managers

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.api.HealthAPI
import liric.mistaken.api.events.MistakenDeathEvent
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.format.NamedTextColor
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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * [LIRIC-MISTAKEN 2.0]
 * CombatManager: Gestión de combate y salud con tecnología de Coroutines.
 */
class CombatManager(private val plugin: Mistaken) : Listener, HealthAPI {

    private val playerHealth = ConcurrentHashMap<UUID, Int>()
    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val originalHelmets = ConcurrentHashMap<UUID, ItemStack>()
    private val killerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val freezeDeathJobs = ConcurrentHashMap<UUID, Job>()

    private val combatScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cooldownMs = 3000L

    // --- IMPLEMENTACIÓN API ---

    override fun getHealth(p: Player): Int = playerHealth.getOrDefault(p.uniqueId, 6)

    override fun setHealth(p: Player, health: Int) {
        playerHealth[p.uniqueId] = health.coerceIn(0, 6)
    }

    override fun isFrozen(p: Player): Boolean = frozenPlayers.contains(p.uniqueId)

    override fun resetPlayer(p: Player) {
        removePlayerData(p.uniqueId)
        setHealth(p, 6)
    }

    override fun takeDamage(victim: Player) {
        if (isFrozen(victim)) return

        val killer = plugin.gameManager.currentAsesino
        if (killer != null && victim != killer) {
            aplicarHuntersMark(killer, victim)
            // Empuje suave direccional
            val velocity = victim.location.toVector()
                .subtract(killer.location.toVector())
                .normalize().multiply(0.3).setY(0.25)
            victim.velocity = velocity
        }

        if (plugin.gameManager.currentMode == MistakenMode.FREEZE_TAG) {
            handleFreeze(victim)
            return
        }

        val current = getHealth(victim) - 1
        setHealth(victim, current)
        enviarFeedbackSalud(victim, current)

        if (current <= 0) {
            handleDeath(victim, false)
            return
        }

        if (current == 1) {
            victim.sendMessage(plugin.messageConfig.getMessage(victim, "combat.critical-wound"))
            victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
        }

        limpiarEstadoTransporte(victim)
        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)

        // Partículas de sangre (Redstone block)
        victim.world.spawnParticle(
            Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10,
            0.2, 0.2, 0.2, Bukkit.createBlockData(Material.REDSTONE_BLOCK)
        )
    }

    override fun unfreeze(victim: Player, rescuer: Player) {
        if (!frozenPlayers.remove(victim.uniqueId)) return
        resetFreezeState(victim)
        setHealth(victim, 3)
        victim.world.playSound(victim.location, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.5f)

        victim.sendMessage(plugin.messageConfig.getMessage(victim, "game.unfrozen-victim", Placeholder.parsed("player", rescuer.name)))
        rescuer.sendMessage(plugin.messageConfig.getMessage(rescuer, "game.unfrozen-rescuer", Placeholder.parsed("player", victim.name)))
    }

    // --- EVENTOS DE COMBATE ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArenaCombat(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return

        // Solo procesar en mundos de arena (ASP carga mundos con "_" por convención)
        if (!victim.world.name.contains("_")) return

        val killer = plugin.gameManager.currentAsesino ?: return
        val isAttackerKiller = attacker.uniqueId == killer.uniqueId
        val isVictimKiller = victim.uniqueId == killer.uniqueId

        if (isAttackerKiller && !isVictimKiller) {
            val now = System.currentTimeMillis()
            val lastAttack = killerCooldowns.getOrDefault(attacker.uniqueId, 0L)

            if (now - lastAttack < cooldownMs) {
                val remaining = (cooldownMs - (now - lastAttack)) / 1000 + 1
                attacker.sendActionBar(plugin.messageConfig.getMessage(attacker, "combat.cooldown",
                    Placeholder.parsed("time", remaining.toString())))
                event.isCancelled = true
                return
            }

            killerCooldowns[attacker.uniqueId] = now
            event.damage = 0.01 // Daño mínimo para activar animaciones
            takeDamage(victim)
            return
        }

        if (!isAttackerKiller) {
            event.isCancelled = true
            if (!isVictimKiller) {
                attacker.sendMessage(plugin.messageConfig.getMessage(attacker, "game.no-friendly-fire"))
            }
        }
    }

    private fun handleDeath(victim: Player, isHypothermia: Boolean) {
        val killer = plugin.gameManager.currentAsesino

        // Si muere el asesino, ganan los supervivientes
        if (killer != null && victim.uniqueId == killer.uniqueId) {
            victim.world.spawnParticle(Particle.EXPLOSION, victim.location, 1)
            victim.world.playSound(victim.location, Sound.ENTITY_WITHER_DEATH, 1f, 0.5f)
            giveWinRewards(false)
            plugin.gameManager.endGame("game.killer-died-victory", false)
            return
        }

        if (killer != null) {
            Bukkit.getPluginManager().callEvent(MistakenDeathEvent(victim, killer))

            // Stats y Economía en hilo asíncrono
            combatScope.launch(Dispatchers.IO) {
                plugin.statsManager.incrementStat(killer.uniqueId, "kills")
                plugin.statsManager.incrementStat(victim.uniqueId, "deaths")

                val reward = plugin.config.getDouble("settings.kill-reward", 50.0)
                Mistaken.economy?.depositPlayer(killer, reward)
            }
            plugin.gameManager.addTime(15)
        }

        resetFreezeState(victim)
        frozenPlayers.remove(victim.uniqueId)
        victim.gameMode = GameMode.SPECTATOR

        val path = if (isHypothermia) "game.player-frozen-death" else "game.player-died"
        plugin.gameManager.broadcastLocalized(path, Placeholder.parsed("player", victim.name))
        victim.playSound(victim.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)

        // Verificar victoria en el siguiente tick de Bukkit
        Bukkit.getScheduler().runTask(plugin, Runnable { plugin.gameManager.checkWinCondition() })
    }

    private fun enviarFeedbackSalud(victim: Player, current: Int) {
        val heartOn = plugin.messageConfig.getRawString(victim, "combat.heart-icon-on", "❤")
        val heartOff = plugin.messageConfig.getRawString(victim, "combat.heart-icon-off", "🖤")

        val healthBar = heartOn.repeat(max(0, current)) + heartOff.repeat(max(0, 6 - current))

        victim.sendActionBar(plugin.messageConfig.getMessage(victim, "combat.health-bar",
            Placeholder.parsed("hearts_on", healthBar),
            Placeholder.parsed("hearts_off", "") // Ajustado para ser más limpio
        ))
    }

    private fun handleFreeze(victim: Player) {
        if (!frozenPlayers.add(victim.uniqueId)) return

        val helmet = victim.inventory.helmet
        if (helmet != null && helmet.type != Material.AIR) {
            originalHelmets[victim.uniqueId] = helmet
        }

        victim.inventory.helmet = ItemStack(Material.BLUE_ICE)
        setPlayerPhysics(victim, 0.0, 0.0)
        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false))
        victim.world.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)

        startFreezeTimer(victim)
        plugin.gameManager.broadcastLocalized("game.player-frozen", Placeholder.parsed("player", victim.name))
        plugin.gameManager.checkWinCondition()
    }

    private fun startFreezeTimer(victim: Player) {
        val job = combatScope.launch {
            var timeLeft = 120
            while (isActive && victim.isOnline && isFrozen(victim)) {
                if (timeLeft <= 0) {
                    withContext(Dispatchers.Main) { handleDeath(victim, true) }
                    break
                }

                val timeFormatted = String.format("%d:%02d", timeLeft / 60, timeLeft % 60)
                val color = when {
                    timeLeft <= 20 -> "<red>"
                    timeLeft <= 60 -> "<yellow>"
                    else -> "<aqua>"
                }

                withContext(Dispatchers.Main) {
                    victim.showTitle(Title.title(
                        plugin.messageConfig.getMessage(victim, "game.freeze-title"),
                        plugin.messageConfig.getMessage(victim, "game.freeze-subtitle",
                            Placeholder.parsed("color", color),
                            Placeholder.parsed("time", timeFormatted)),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ZERO)
                    ))
                    if (timeLeft <= 10) victim.playSound(victim.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 2f)
                }

                delay(1000L)
                timeLeft--
            }
        }
        freezeDeathJobs[victim.uniqueId] = job
    }

    private fun resetFreezeState(p: Player) {
        freezeDeathJobs.remove(p.uniqueId)?.cancel()
        p.clearTitle()
        setPlayerPhysics(p, 0.1, 0.42)

        originalHelmets.remove(p.uniqueId)?.let {
            p.inventory.helmet = it
        } ?: run {
            if (p.inventory.helmet?.type == Material.BLUE_ICE) p.inventory.helmet = null
        }
    }

    private fun setPlayerPhysics(p: Player, walkSpeed: Double, jumpStrength: Double) {
        p.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = walkSpeed
        p.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = jumpStrength
    }

    private fun aplicarHuntersMark(killer: Player, victim: Player) {
        enviarGlowPaquete(killer, victim, true)

        val team = killer.scoreboard.getTeam("glow_yellow") ?: killer.scoreboard.registerNewTeam("glow_yellow").apply {
            color(NamedTextColor.YELLOW)
        }
        team.addEntry(victim.name)

        killer.sendActionBar(plugin.messageConfig.getMessage(killer, "combat.hunters-mark", Placeholder.parsed("player", victim.name)))
        killer.playSound(killer.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f)

        combatScope.launch {
            delay(5000L) // 100 ticks = 5s
            if (killer.isOnline && victim.isOnline) {
                enviarGlowPaquete(killer, victim, false)
                withContext(Dispatchers.Main) {
                    killer.scoreboard.getTeam("glow_yellow")?.removeEntry(victim.name)
                }
            }
        }
    }

    fun enviarGlowPaquete(receiver: Player, victim: Player, active: Boolean) {
        val mask = if (active) 0x40.toByte() else 0x00.toByte()
        val data = listOf(EntityData(0, EntityDataTypes.BYTE, mask))
        val packet = WrapperPlayServerEntityMetadata(victim.entityId, data)
        PacketEvents.getAPI().playerManager.sendPacket(receiver, packet)
    }

    fun giveWinRewards(killerWon: Boolean) {
        val econ = Mistaken.economy ?: return
        val killer = plugin.gameManager.currentAsesino
        val winners = mutableListOf<Player>()

        if (killerWon) {
            killer?.let { winners.add(it) }
        } else {
            Bukkit.getOnlinePlayers().filter { it.uniqueId != killer?.uniqueId && it.gameMode != GameMode.SPECTATOR }.forEach { winners.add(it) }
        }

        val kAmount = plugin.config.getDouble("settings.win-reward-killer", 500.0)
        val sAmount = plugin.config.getDouble("settings.win-reward-survivor", 200.0)

        combatScope.launch(Dispatchers.IO) {
            winners.forEach { p ->
                if (p.isOnline) {
                    val amount = if (killerWon) kAmount else sAmount
                    econ.depositPlayer(p, amount)

                    val path = if (killerWon) "combat.win-reward-killer" else "combat.win-reward-survivor"
                    p.sendMessage(plugin.messageConfig.getMessage(p, path, Placeholder.parsed("amount", amount.toString())))
                }
            }
        }
    }

    private fun limpiarEstadoTransporte(victim: Player) {
        if (victim.passengers.isNotEmpty()) soltarPasajero(victim)
        (victim.vehicle as? Player)?.let { soltarPasajero(it) }
    }

    fun soltarPasajero(r: Player) {
        r.passengers.forEach { p ->
            r.removePassenger(p)
            if (p is Player && getHealth(p) == 1) p.isSwimming = true
        }
    }

    fun removePlayerData(uuid: UUID) {
        Bukkit.getPlayer(uuid)?.let { resetFreezeState(it) }
        playerHealth.remove(uuid)
        frozenPlayers.remove(uuid)
        originalHelmets.remove(uuid)
        killerCooldowns.remove(uuid)
    }

    fun clearAll() {
        frozenPlayers.forEach { uuid -> Bukkit.getPlayer(uuid)?.let { resetFreezeState(it) } }
        playerHealth.clear()
        frozenPlayers.clear()
        originalHelmets.clear()
        killerCooldowns.clear()
        freezeDeathJobs.values.forEach { it.cancel() }
        freezeDeathJobs.clear()
        combatScope.cancel()
    }
}
