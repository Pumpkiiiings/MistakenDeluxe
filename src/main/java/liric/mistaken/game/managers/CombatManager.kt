package liric.mistaken.game.managers

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
import java.util.concurrent.TimeUnit

/**
 * [LIRIC-MISTAKEN 2.0]
 * CombatManager: Adaptación fiel a la lógica Java (KB Nativo).
 */
class CombatManager(private val plugin: Mistaken) : Listener, HealthAPI {

    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val originalHelmets = ConcurrentHashMap<UUID, ItemStack>()
    private val killerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val survivorCooldowns = ConcurrentHashMap<UUID, Long>()

    // Tiempos de Cooldown
    private val KILLER_COOLDOWN = 2000L
    private val SURVIVOR_COOLDOWN = 3000L // 3 Segundos exactos

    private val mm = plugin.mm

    init {
        startRadarTask()
    }

    private fun startRadarTask() {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady || plugin.gameManager.currentState != GameState.INGAME) return@runAtFixedRate
            val killer = plugin.gameManager.getCurrentAsesino() ?: return@runAtFixedRate
            if (!killer.isOnline) return@runAtFixedRate

            val killerLoc = killer.location
            var minDistanceSq = Double.MAX_VALUE
            var foundSomeone = false

            for (target in plugin.server.onlinePlayers) {
                if (target == killer || target.world != killerLoc.world ||
                    target.gameMode != GameMode.SURVIVAL || plugin.isIgnored(target)) continue

                val distSq = killerLoc.distanceSquared(target.location)

                if (distSq <= 100.0) { // 10 bloques
                    target.scheduler.execute(plugin, {
                        if (target.isOnline) target.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 30, 0, false, false, false))
                    }, null, 0L)
                }
                if (distSq <= 900.0) {
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq
                        foundSomeone = true
                    }
                }
            }

            if (foundSomeone) {
                val realDist = Math.sqrt(minDistanceSq)
                killer.sendActionBar(mm.deserialize("<yellow>Escuchas el latido de alguien.."))
                val (vol, pitch) = when {
                    realDist < 5.0 -> 1.2f to 1.5f
                    realDist < 15.0 -> 0.8f to 1.0f
                    else -> 0.4f to 0.6f
                }
                killer.playSound(killer.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, vol, pitch)
            }
        }, 0L, 500L, TimeUnit.MILLISECONDS)
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) block()
        else plugin.server.scheduler.runTask(plugin, Runnable { block() })
    }

    // --- API Methods ---
    override fun getHealth(player: Player): Int = player.health.toInt()

    override fun setHealth(player: Player, health: Int) = runOnMain {
        val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = health.toDouble().coerceIn(0.0, max)
        if (plugin.isReady) plugin.scoreboardManager.updatePlayer(player)
    }

    override fun isFrozen(player: Player): Boolean = frozenPlayers.contains(player.uniqueId)

    override fun resetPlayer(player: Player) {
        removePlayerData(player.uniqueId)
        resetHealth(player)
    }

    fun resetHealth(player: Player) = runOnMain {
        val isKiller = plugin.gameManager.esAsesino(player.uniqueId)
        val maxHP = if (isKiller) 160.0 else 20.0
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHP
        player.health = maxHP
        player.removePotionEffect(PotionEffectType.DARKNESS)
        player.isSwimming = false
        if (plugin.isReady) plugin.scoreboardManager.updatePlayer(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onArenaCombat(event: EntityDamageByEntityEvent) {
        // Evitar bucles infinitos si nosotros mismos causamos el daño
        if (event.entity.hasMetadata("mistaken_processing")) return

        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return

        // Verificar mundo de juego (simple check por nombre o lógica del GM)
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val isAttackerKiller = plugin.gameManager.esAsesino(attacker.uniqueId)
        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        // Si no hay asesino involucrado, salimos (o manejamos lógica de PvP normal)
        if (!isAttackerKiller && !isVictimKiller) return

        val now = System.currentTimeMillis()

        // 1. Fuego Amigo (Bloqueado totalmente)
        if (isAttackerKiller == isVictimKiller) {
            event.isCancelled = true
            return
        }

        // 2. Asesino vs Humano
        if (isAttackerKiller && !isVictimKiller) {
            val lastHit = killerCooldowns.getOrDefault(attacker.uniqueId, 0L)

            // Check Cooldown (2s)
            if (now - lastHit < KILLER_COOLDOWN) {
                val remaining = (KILLER_COOLDOWN - (now - lastHit)) / 1000.0
                attacker.sendActionBar(plugin.messageConfig.getMessage(attacker, "combat.cooldown", Placeholder.parsed("time", String.format(Locale.US, "%.1f", remaining))))
                event.isCancelled = true
                return
            }

            killerCooldowns[attacker.uniqueId] = now

            // 🔥 TRUCO DEL JAVA: No cancelamos, ponemos daño 0.0 para que haya KB natural
            event.damage = 0.1

            // Procesamos nuestro daño lógico
            processTrueDamage(victim, attacker, 3.0)
            return
        }

        // 3. Humano vs Asesino
        if (!isAttackerKiller && isVictimKiller) {
            val lastHit = survivorCooldowns.getOrDefault(attacker.uniqueId, 0L)

            // Check Cooldown (3s)
            if (now - lastHit < SURVIVOR_COOLDOWN) {
                val remaining = (SURVIVOR_COOLDOWN - (now - lastHit)) / 1000.0
                attacker.sendActionBar(plugin.messageConfig.getMessage(attacker, "combat.cooldown", Placeholder.parsed("time", String.format(Locale.US, "%.1f", remaining))))
                event.isCancelled = true
                return
            }

            survivorCooldowns[attacker.uniqueId] = now

            // 🔥 TRUCO DEL JAVA: No cancelamos, ponemos daño 0.0
            // Esto permite que el asesino reciba el empuje (Knockback) nativo de Minecraft.
            event.damage = 0.0

            // Procesamos nuestro daño lógico (4 golpes para matar)
            processTrueDamage(victim, attacker, 4.0)

            // Sonido extra de golpe
            victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)
            return
        }
    }

    fun processTrueDamage(victim: Player, attacker: Player?, amount: Double) {
        if (isFrozen(victim)) return

        runOnMain {
            // Bajamos la vida "lógica" (corazones visuales o custom health)
            val nextHP = (victim.health - amount).coerceAtLeast(0.0)
            victim.health = nextHP

            // Efectos de sangrado para supervivientes
            val isSurvivor = !plugin.gameManager.esAsesino(victim.uniqueId)

            if (isSurvivor && nextHP <= 4.0 && nextHP > 0.0) {
                if (!victim.hasPotionEffect(PotionEffectType.DARKNESS)) {
                    val msg = plugin.messageConfig.getRawString(victim, "combat.critical-wound", "<red><bold>¡HERIDA CRÍTICA!</bold>")
                    victim.sendMessage(mm.deserialize(msg))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, Int.MAX_VALUE, 0, false, false, true))
                }
            }

            // Efectos visuales de sangre (Redstone Block)
            victim.world.spawnParticle(Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, Material.REDSTONE_BLOCK.createBlockData())

            if (plugin.isReady) plugin.scoreboardManager.updatePlayer(victim)
            if (nextHP <= 0.0) handleDeath(victim, false)
        }
    }

    // --- Métodos de Gestión ---

    fun removePlayerData(uuid: UUID) {
        val p = Bukkit.getPlayer(uuid)
        p?.let {
            resetFreezeState(it)
            it.removePotionEffect(PotionEffectType.DARKNESS)
            it.isSwimming = false
        }
        killerCooldowns.remove(uuid)
        survivorCooldowns.remove(uuid)
        frozenPlayers.remove(uuid)
        originalHelmets.remove(uuid)
    }

    fun clearAll() {
        frozenPlayers.toList().forEach { uuid -> Bukkit.getPlayer(uuid)?.let { resetFreezeState(it) } }
        frozenPlayers.clear()
        originalHelmets.clear()
        killerCooldowns.clear()
        survivorCooldowns.clear()
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
            plugin.server.asyncScheduler.runNow(plugin) {
                plugin.statsManager.incrementStat(killer.uniqueId, "kills")
                plugin.statsManager.incrementStat(victim.uniqueId, "deaths")
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
        player.clearTitle()
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.42
        originalHelmets.remove(player.uniqueId)?.let { player.inventory.helmet = it } ?: run { player.inventory.helmet = null }
    }

    fun giveWinRewards(killerWon: Boolean) {
        val killers = plugin.gameManager.asesinosUUIDs
        val winners = if (killerWon) Bukkit.getOnlinePlayers().filter { killers.contains(it.uniqueId) }
        else Bukkit.getOnlinePlayers().filter { !killers.contains(it.uniqueId) && it.gameMode != GameMode.SPECTATOR }

        plugin.server.asyncScheduler.runNow(plugin) {
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
        var timeLeft = 120
        victim.scheduler.runAtFixedRate(plugin, { task ->
            if (!isFrozen(victim) || !victim.isOnline) {
                task.cancel()
                return@runAtFixedRate
            }
            val timeFormatted = String.format(Locale.US, "%d:%02d", timeLeft / 60, timeLeft % 60)
            victim.showTitle(Title.title(
                plugin.messageConfig.getMessage(victim, "game.freeze-title"),
                plugin.messageConfig.getMessage(victim, "game.freeze-subtitle", Placeholder.parsed("time", timeFormatted))
            ))
            timeLeft--
            if (timeLeft <= 0) {
                task.cancel()
                runOnMain { handleDeath(victim, true) }
            }
        }, null, 0L, 20L)
    }

    fun shutdown() {
        clearAll()
    }

    override fun takeDamage(victim: Player) { processTrueDamage(victim, null, 3.0) }
}
