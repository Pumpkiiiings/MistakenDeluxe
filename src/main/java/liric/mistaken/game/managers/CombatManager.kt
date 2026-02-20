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
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
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

class CombatManager(private val plugin: Mistaken) : Listener, HealthAPI {

    private val playerHealth = ConcurrentHashMap<UUID, Int>()
    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val originalHelmets = ConcurrentHashMap<UUID, ItemStack>()
    private val killerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val freezeDeathJobs = ConcurrentHashMap<UUID, Job>()

    private val combatScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cooldownMs = 3000L

    // Helper para ejecutar en Main Thread solo si es necesario (Optimización Senior)
    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) block()
        else combatScope.launch(plugin.mainThread) { block() }
    }

    // --- IMPLEMENTACIÓN API ---

    override fun getHealth(player: Player): Int = playerHealth.getOrDefault(player.uniqueId, 6)

    override fun setHealth(player: Player, health: Int) {
        playerHealth[player.uniqueId] = health.coerceIn(0, 6)
    }

    fun resetHealth(player: Player) {
        setHealth(player, 6)
    }

    override fun isFrozen(player: Player): Boolean = frozenPlayers.contains(player.uniqueId)

    override fun resetPlayer(player: Player) {
        removePlayerData(player.uniqueId)
        setHealth(player, 6)
    }

    override fun takeDamage(victim: Player) {
        if (isFrozen(victim)) return

        val killer = plugin.gameManager.getCurrentAsesino()
        if (killer != null && victim != killer) {
            aplicarHuntersMark(killer, victim)

            // Empuje físico: Sincronizado para evitar stuttering visual
            val velocity = victim.location.toVector()
                .subtract(killer.location.toVector())
                .normalize().multiply(0.3).setY(0.25)

            runOnMain { victim.velocity = velocity }
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

        // Limpiar estado de transporte debe ser en Main Thread
        runOnMain { limpiarEstadoTransporte(victim) }

        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)

        // Spawn de partículas (En 1.21.4, createBlockData debe estar en el main si el material es complejo)
        runOnMain {
            victim.world.spawnParticle(
                Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10,
                0.2, 0.2, 0.2, Material.REDSTONE_BLOCK.createBlockData()
            )
        }
    }

    override fun unfreeze(victim: Player, rescuer: Player) {
        if (!frozenPlayers.remove(victim.uniqueId)) return

        runOnMain {
            resetFreezeState(victim)
            setHealth(victim, 3)
            victim.world.playSound(victim.location, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.5f)

            victim.sendMessage(plugin.messageConfig.getMessage(victim, "game.unfrozen-victim", Placeholder.parsed("player", rescuer.name)))
            rescuer.sendMessage(plugin.messageConfig.getMessage(rescuer, "game.unfrozen-rescuer", Placeholder.parsed("player", victim.name)))
        }
    }

    // --- LÓGICA DE COMBATE ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArenaCombat(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return

        if (!victim.world.name.contains("_")) return

        val killer = plugin.gameManager.getCurrentAsesino() ?: return

        val isAttackerKiller = attacker.uniqueId == killer.uniqueId
        val isVictimKiller = victim.uniqueId == killer.uniqueId

        if (isAttackerKiller && !isVictimKiller) {
            val now = System.currentTimeMillis()
            val lastAttack = killerCooldowns.getOrDefault(attacker.uniqueId, 0L)

            if (now - lastAttack < cooldownMs) {
                val remaining = (cooldownMs - (now - lastAttack)) / 1000
                attacker.sendActionBar(plugin.messageConfig.getMessage(attacker, "combat.cooldown",
                    Placeholder.parsed("time", (remaining + 1).toString())))
                event.isCancelled = true
                return
            }

            killerCooldowns[attacker.uniqueId] = now
            event.damage = 0.01 // Solo para trigger de animación
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
        val killer = plugin.gameManager.getCurrentAsesino()

        if (killer != null && victim.uniqueId == killer.uniqueId) {
            runOnMain {
                victim.world.spawnParticle(Particle.EXPLOSION, victim.location, 1)
                victim.world.playSound(victim.location, Sound.ENTITY_WITHER_DEATH, 1f, 0.5f)
                giveWinRewards(false)
                plugin.gameManager.endGame("game.killer-died-victory", false)
            }
            return
        }

        if (killer != null) {
            // Llamar evento síncrono
            runOnMain { Bukkit.getPluginManager().callEvent(MistakenDeathEvent(victim, killer)) }

            combatScope.launch(Dispatchers.IO) {
                plugin.statsManager.incrementStat(killer.uniqueId, "kills")
                plugin.statsManager.incrementStat(victim.uniqueId, "deaths")

                val reward = plugin.config.getDouble("settings.kill-reward", 50.0)
                Mistaken.economy?.depositPlayer(killer, reward)
                plugin.gameManager.addTime(15)
            }
        }

        runOnMain {
            resetFreezeState(victim)
            frozenPlayers.remove(victim.uniqueId)
            victim.gameMode = GameMode.SPECTATOR

            val deathPath = if (isHypothermia) "game.player-frozen-death" else "game.player-died"
            plugin.gameManager.broadcastLocalized(deathPath, Placeholder.parsed("player", victim.name))
            victim.playSound(victim.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)

            plugin.gameManager.checkWinCondition()
        }
    }

    private fun enviarFeedbackSalud(victim: Player, current: Int) {
        val heartOn = plugin.config.getString("messages.combat.heart-icon-on") ?: "❤"
        val heartOff = plugin.config.getString("messages.combat.heart-icon-off") ?: "🖤"

        val onBuilder = heartOn.repeat(max(0, current))
        val offBuilder = heartOff.repeat(max(0, 6 - current))

        victim.sendActionBar(plugin.messageConfig.getMessage(victim, "combat.health-bar",
            Placeholder.parsed("hearts_on", onBuilder),
            Placeholder.parsed("hearts_off", offBuilder)
        ))
    }

    private fun limpiarEstadoTransporte(victim: Player) {
        if (victim.passengers.isNotEmpty()) soltarPasajero(victim)
        val vehicle = victim.vehicle
        if (vehicle is Player) soltarPasajero(vehicle)
    }

    private fun aplicarHuntersMark(killer: Player, victim: Player) {
        enviarGlowPaquete(killer, victim, true)

        runOnMain {
            val sb = killer.scoreboard
            val team = sb.getTeam("glow_yellow") ?: sb.registerNewTeam("glow_yellow").apply {
                color(NamedTextColor.YELLOW)
            }
            if (!team.hasEntry(victim.name)) team.addEntry(victim.name)
        }

        killer.sendActionBar(plugin.messageConfig.getMessage(killer, "combat.hunters-mark", Placeholder.parsed("player", victim.name)))
        killer.playSound(killer.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f)

        combatScope.launch {
            delay(5000)
            if (killer.isOnline && victim.isOnline) {
                enviarGlowPaquete(killer, victim, false)
                withContext(plugin.mainThread) {
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

    private fun handleFreeze(victim: Player) {
        if (!frozenPlayers.add(victim.uniqueId)) return

        runOnMain {
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
    }

    private fun startFreezeTimer(victim: Player) {
        val job = combatScope.launch {
            var timeLeft = 120
            while (isActive && timeLeft > 0) {
                if (!victim.isOnline || !isFrozen(victim)) break

                // UI Processing
                val timeFormatted = String.format("%d:%02d", timeLeft / 60, timeLeft % 60)
                val color = when {
                    timeLeft <= 20 -> "<red>"
                    timeLeft <= 60 -> "<yellow>"
                    else -> "<aqua>"
                }

                // Obtener mensajes fuera del main para ahorrar tiempo
                val titleMsg = plugin.messageConfig.getMessage(victim, "game.freeze-title")
                val subtitleMsg = plugin.messageConfig.getMessage(victim, "game.freeze-subtitle",
                    Placeholder.parsed("color", color),
                    Placeholder.parsed("time", timeFormatted))

                withContext(plugin.mainThread) {
                    victim.showTitle(Title.title(titleMsg, subtitleMsg,
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ZERO)))

                    if (timeLeft <= 10) victim.playSound(victim.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 2f)
                }

                delay(1000)
                timeLeft--
            }

            if (timeLeft <= 0 && victim.isOnline && isFrozen(victim)) {
                withContext(plugin.mainThread) { handleDeath(victim, true) }
            }
        }
        freezeDeathJobs[victim.uniqueId] = job
    }

    private fun resetFreezeState(player: Player) {
        freezeDeathJobs.remove(player.uniqueId)?.cancel()
        player.clearTitle()
        setPlayerPhysics(player, 0.1, 0.42)

        originalHelmets.remove(player.uniqueId)?.let {
            player.inventory.helmet = it
        } ?: run {
            if (player.inventory.helmet?.type == Material.BLUE_ICE) player.inventory.helmet = null
        }
    }

    private fun setPlayerPhysics(player: Player, walkSpeed: Double, jumpStrength: Double) {
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = walkSpeed
        player.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = jumpStrength
    }

    fun giveWinRewards(killerWon: Boolean) {
        val econ = Mistaken.economy ?: return
        val killer = plugin.gameManager.getCurrentAsesino()

        val winners = if (killerWon) {
            killer?.let { listOf(it) } ?: emptyList()
        } else {
            Bukkit.getOnlinePlayers().filter {
                it.uniqueId != killer?.uniqueId && it.gameMode != GameMode.SPECTATOR
            }
        }

        val kAmount = plugin.config.getDouble("settings.win-reward-killer", 500.0)
        val sAmount = plugin.config.getDouble("settings.win-reward-survivor", 200.0)

        combatScope.launch(Dispatchers.IO) {
            winners.forEach { p ->
                val amount = if (killerWon) kAmount else sAmount
                econ.depositPlayer(p, amount)

                val path = if (killerWon) "combat.win-reward-killer" else "combat.win-reward-survivor"
                p.sendMessage(plugin.messageConfig.getMessage(p, path, Placeholder.parsed("amount", amount.toString())))
            }
        }
    }

    fun removePlayerData(uuid: UUID) {
        Bukkit.getPlayer(uuid)?.let { resetFreezeState(it) }
        playerHealth.remove(uuid)
        frozenPlayers.remove(uuid)
        originalHelmets.remove(uuid)
        killerCooldowns.remove(uuid)
        freezeDeathJobs.remove(uuid)?.cancel()
    }

    fun clearAll() {
        frozenPlayers.toList().forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { resetFreezeState(it) }
        }
        playerHealth.clear()
        frozenPlayers.clear()
        originalHelmets.clear()
        killerCooldowns.clear()

        freezeDeathJobs.values.forEach { it.cancel() }
        freezeDeathJobs.clear()
    }

    fun soltarPasajero(vehicle: Player) {
        vehicle.passengers.forEach { p ->
            vehicle.removePassenger(p)
            if (p is Player && getHealth(p) == 1) {
                p.isSwimming = true
            }
        }
    }

    fun shutdown() {
        combatScope.cancel()
        clearAll()
    }
}
