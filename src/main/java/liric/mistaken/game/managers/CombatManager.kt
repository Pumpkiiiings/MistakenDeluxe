package liric.mistaken.game.managers

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.api.HealthAPI
import liric.mistaken.api.events.MistakenDeathEvent
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.utils.mainThread
import net.kyori.adventure.text.Component
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
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CombatManager(private val plugin: Mistaken) : Listener, HealthAPI {

    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val originalHelmets = ConcurrentHashMap<UUID, ItemStack>()
    private val killerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val freezeDeathJobs = ConcurrentHashMap<UUID, Job>()

    private val combatScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cooldownMs = 2000L
    private val mm = plugin.mm

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) block()
        else combatScope.launch(plugin.mainThread) { block() }
    }

    // --- IMPLEMENTACIÓN DE LA API ---

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
            val maxHP = if (isKiller) 200.0 else 20.0
            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHP
            player.health = maxHP
            player.removePotionEffect(PotionEffectType.DARKNESS)
            player.isSwimming = false
            if (plugin.isReady) plugin.scoreboardManager.updatePlayer(player)
        }
    }

    /**
     * Motor de daño real (True Damage)
     */
    fun processTrueDamage(victim: Player, attacker: Player?, amount: Double) {
        if (isFrozen(victim)) return

        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)
        val nextHP = (victim.health - amount).coerceAtLeast(0.0)
        victim.health = nextHP

        if (!isVictimKiller) {
            if (attacker != null) aplicarHuntersMark(attacker, victim)

            // Check Herida Crítica (4 HP o menos para Survivors)
            if (nextHP <= 4.0 && nextHP > 0.0) {
                if (!victim.hasPotionEffect(PotionEffectType.DARKNESS)) {
                    // 🔥 MODIFICADO: Quitada la lógica manual del prefijo
                    val rawMsg = plugin.messageConfig.getRawString(victim, "combat.critical-wound",
                        "<red><bold>¡TUS PIERNAS FALLAN!</bold> <gray>Busca ayuda o arrástrate.")

                    victim.sendMessage(mm.deserialize(rawMsg))

                    runOnMain {
                        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, Int.MAX_VALUE, 0, false, false, true))
                        victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
                    }
                }
            }
        }

        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)
        victim.world.spawnParticle(Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, Material.REDSTONE_BLOCK.createBlockData())

        if (plugin.isReady) plugin.scoreboardManager.updatePlayer(victim)
        if (nextHP <= 0.0) handleDeath(victim, false)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArenaCombat(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return

        val isAttackerKiller = plugin.gameManager.esAsesino(attacker.uniqueId)
        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        // CASO A: Asesino ataca a Sobreviviente
        if (isAttackerKiller && !isVictimKiller) {
            val now = System.currentTimeMillis()
            if (now - killerCooldowns.getOrDefault(attacker.uniqueId, 0L) < cooldownMs) {
                event.isCancelled = true
                return
            }
            killerCooldowns[attacker.uniqueId] = now

            event.damage = 0.0
            // 🔥 MODIFICADO: El asesino quita 3.0 HP (1.5 corazones)
            processTrueDamage(victim, attacker, 3.0)
            return
        }

        // CASO B: Sobreviviente ataca a Asesino
        if (!isAttackerKiller && isVictimKiller) {
            event.damage = 0.0
            processTrueDamage(victim, attacker, 4.0)
            return
        }

        if (!isAttackerKiller && !isVictimKiller) event.isCancelled = true
    }

    // --- GESTIÓN DE SALIDA Y LIMPIEZA ---

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
        frozenPlayers.toList().forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { resetFreezeState(it) }
        }
        frozenPlayers.clear()
        originalHelmets.clear()
        killerCooldowns.clear()
        freezeDeathJobs.values.forEach { it.cancel() }
        freezeDeathJobs.clear()
    }

    fun soltarPasajero(vehicle: Player) {
        vehicle.passengers.forEach {
            vehicle.removePassenger(it)
            if (it is Player && it.health <= 4.0) it.isSwimming = true
        }
    }

    // --- SOPORTE ---

    private fun handleDeath(victim: Player, isHypothermia: Boolean) {
        runOnMain {
            victim.removePotionEffect(PotionEffectType.DARKNESS)
            victim.isSwimming = false
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
            frozenPlayers.remove(victim.uniqueId)
            victim.gameMode = GameMode.SPECTATOR
            victim.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            plugin.gameManager.broadcastLocalized("game.player-died", Placeholder.parsed("player", victim.name))
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

    private fun aplicarHuntersMark(killer: Player, victim: Player) {
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("GlowTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.YELLOW, WrapperPlayServerTeams.OptionData.NONE
        )
        val createTeam = WrapperPlayServerTeams("ms_glow", WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, mutableListOf(victim.name))
        PacketEvents.getAPI().playerManager.sendPacket(killer, createTeam)
        val packet = WrapperPlayServerEntityMetadata(victim.entityId, listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte())))
        PacketEvents.getAPI().playerManager.sendPacket(killer, packet)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (killer.isOnline && victim.isOnline) {
                PacketEvents.getAPI().playerManager.sendPacket(killer, WrapperPlayServerEntityMetadata(victim.entityId, listOf(EntityData(0, EntityDataTypes.BYTE, 0.toByte()))))
                PacketEvents.getAPI().playerManager.sendPacket(killer, WrapperPlayServerTeams("ms_glow", WrapperPlayServerTeams.TeamMode.REMOVE, null as ScoreBoardTeamInfo?, mutableListOf()))
            }
        }, 100L)
    }

    private fun startFreezeTimer(victim: Player) {
        val job = combatScope.launch {
            var timeLeft = 120
            while (isActive && timeLeft > 0 && isFrozen(victim)) {
                withContext(plugin.mainThread) {
                    val timeFormatted = String.format("%d:%02d", timeLeft / 60, timeLeft % 60)
                    victim.showTitle(Title.title(plugin.messageConfig.getMessage(victim, "game.freeze-title"),
                        plugin.messageConfig.getMessage(victim, "game.freeze-subtitle", Placeholder.parsed("time", timeFormatted))))
                }
                delay(1000L); timeLeft--
            }
            if (timeLeft <= 0 && isFrozen(victim)) withContext(plugin.mainThread) { handleDeath(victim, true) }
        }
        freezeDeathJobs[victim.uniqueId] = job
    }

    fun shutdown() { clearAll(); combatScope.cancel() }
    override fun takeDamage(victim: Player) {}
}
