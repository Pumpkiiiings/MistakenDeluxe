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
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * CombatManager: Motor de Daño y Estado del Jugador.
 * FIX: Candado Anti-Bucle en eventos de daño, Dispatcher de Bukkit y True Damage.
 */
class CombatManager(private val plugin: Mistaken) : Listener, HealthAPI {

    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val originalHelmets = ConcurrentHashMap<UUID, ItemStack>()
    private val killerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val freezeDeathJobs = ConcurrentHashMap<UUID, Job>()

    private val combatScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cooldownMs = 2000L
    private val mm = plugin.mm

    /**
     * Helper para ejecutar código en el Hilo Principal de forma segura.
     */
    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) block()
        else combatScope.launch(plugin.bukkitDispatcher) { block() }
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
            val maxHP = if (isKiller) 160.0 else 20.0 // 80 Corazones para Asesino, 10 para Superviviente
            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHP
            player.health = maxHP
            player.removePotionEffect(PotionEffectType.DARKNESS)
            player.isSwimming = false
            if (plugin.isReady) plugin.scoreboardManager.updatePlayer(player)
        }
    }

    /**
     * Motor de daño real (True Damage).
     * Modifica directamente la salud saltándose la armadura vanilla.
     */
    fun processTrueDamage(victim: Player, attacker: Player?, amount: Double) {
        if (isFrozen(victim)) return

        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        runOnMain {
            val nextHP = (victim.health - amount).coerceAtLeast(0.0)
            victim.health = nextHP

            if (!isVictimKiller) {
                if (attacker != null) aplicarHuntersMark(attacker, victim)

                // Check Herida Crítica (4 HP o menos para Survivors)
                if (nextHP <= 4.0 && nextHP > 0.0) {
                    if (!victim.hasPotionEffect(PotionEffectType.DARKNESS)) {
                        val rawMsg = plugin.messageConfig.getRawString(victim, "combat.critical-wound",
                            "<red><bold>¡TUS PIERNAS FALLAN!</bold> <gray>Busca ayuda o arrástrate.")

                        victim.sendMessage(mm.deserialize(rawMsg))

                        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, Int.MAX_VALUE, 0, false, false, true))
                        victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
                    }
                }
            }

            // Efectos visuales de sangre (Bloque de redstone)
            victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)
            victim.world.spawnParticle(Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, Material.REDSTONE_BLOCK.createBlockData())

            if (plugin.isReady) plugin.scoreboardManager.updatePlayer(victim)
            if (nextHP <= 0.0) handleDeath(victim, false)
        }
    }

    /**
     * 🔥 GESTIÓN DE DAÑO FÍSICO Y COMBATE CUERPO A CUERPO
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArenaCombat(event: EntityDamageByEntityEvent) {
        // 🛡️ CANDADO ANTI-BUCLES: Si trae la marca de "True Damage", lo ignoramos
        if (event.entity.hasMetadata("mistaken_processing")) return

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

            // Aplicamos Candado y True Damage
            event.isCancelled = true
            victim.setMetadata("mistaken_processing", FixedMetadataValue(plugin, true))

            processTrueDamage(victim, attacker, 3.0) // Asesino quita 1.5 corazones

            victim.removeMetadata("mistaken_processing", plugin)
            return
        }

        // CASO B: Sobreviviente ataca a Asesino
        if (!isAttackerKiller && isVictimKiller) {
            // Aplicamos Candado y True Damage
            event.isCancelled = true
            victim.setMetadata("mistaken_processing", FixedMetadataValue(plugin, true))

            processTrueDamage(victim, attacker, 4.0) // Survivor quita 2 corazones

            victim.removeMetadata("mistaken_processing", plugin)
            return
        }

        // Fuego amigo
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

    // --- SOPORTE DE MUERTE Y CONGELAMIENTO ---

    private fun handleDeath(victim: Player, isHypothermia: Boolean) {
        runOnMain {
            victim.removePotionEffect(PotionEffectType.DARKNESS)

            // Usamos tu utilidad para hacer el espectador limpio
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

            // Stats Asíncronas
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
            victim.health = 10.0 // Al ser rescatado revive con 5 corazones
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
        runOnMain {
            // 1. Usar el Scoreboard de Bukkit es mucho más seguro en 1.21.4
            val board = Bukkit.getScoreboardManager().mainScoreboard
            val teamName = "ms_glow"

            val team = board.getTeam(teamName) ?: board.registerNewTeam(teamName)
            team.color(NamedTextColor.YELLOW)

            // Si el jugador no está en el equipo, lo metemos
            if (!team.hasEntry(victim.name)) {
                team.addEntry(victim.name)
            }

            // 2. Usar la metadata de Entity para encender el Glow mediante paquetes
            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
            val packet = WrapperPlayServerEntityMetadata(victim.entityId, metadata)
            PacketEvents.getAPI().playerManager.sendPacket(killer, packet)

            // 3. Temporizador para quitar el Glow
            combatScope.launch {
                delay(5000) // Dura 5 segundos la marca
                runOnMain {
                    if (killer.isOnline && victim.isOnline) {
                        // Apagamos el Glow enviando el byte en 0
                        val resetPacket = WrapperPlayServerEntityMetadata(victim.entityId, listOf(EntityData(0, EntityDataTypes.BYTE, 0.toByte())))
                        PacketEvents.getAPI().playerManager.sendPacket(killer, resetPacket)

                        // Sacamos a la víctima del equipo amarillo
                        board.getTeam(teamName)?.removeEntry(victim.name)
                    }
                }
            }
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
                        val color = when {
                            timeLeft <= 20 -> "<red>"
                            timeLeft <= 60 -> "<yellow>"
                            else -> "<aqua>"
                        }

                        victim.showTitle(Title.title(
                            plugin.messageConfig.getMessage(victim, "game.freeze-title"),
                            plugin.messageConfig.getMessage(victim, "game.freeze-subtitle",
                                Placeholder.parsed("color", color),
                                Placeholder.parsed("time", timeFormatted))
                        ))
                    }
                }
                delay(1000L); timeLeft--
            }
            if (timeLeft <= 0 && isFrozen(victim)) runOnMain { handleDeath(victim, true) }
        }
        freezeDeathJobs[victim.uniqueId] = job
    }

    fun shutdown() { clearAll(); combatScope.cancel() }

    override fun takeDamage(victim: Player) {
        processTrueDamage(victim, null, 3.0)
    }
}
