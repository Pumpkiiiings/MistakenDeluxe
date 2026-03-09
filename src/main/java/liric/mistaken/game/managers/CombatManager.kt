package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.api.HealthAPI
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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * [LIRIC-MISTAKEN 2.0]
 * CombatManager: Adaptación fiel a la lógica Java (KB Nativo).
 * FIX: Cooldown de 3 segundos por golpe con indicador visual nativo (Attack Speed).
 */
class CombatManager(private val plugin: Mistaken) : Listener, HealthAPI {

    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val killerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val survivorCooldowns = ConcurrentHashMap<UUID, Long>()

    // 🔥 TIEMPOS DE COOLDOWN (Ambos en 3 segundos exactos)
    private val KILLER_COOLDOWN = 3000L
    private val SURVIVOR_COOLDOWN = 3000L

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

                if (distSq <= 100.0) {
                    target.scheduler.run(plugin, { _ ->
                        if (target.isOnline) target.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 30, 0, false, false, false))
                    }, null)
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
        else plugin.server.globalRegionScheduler.run(plugin) { _ -> block() }
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

        // 🔥 FIX MAGICO VISUAL: Modificamos el Attack Speed nativo de Minecraft.
        // Fórmula: Tiempo en segundos = 1.0 / Attack Speed
        // Para 3 segundos de cooldown -> 1.0 / 3.0 = 0.333333
        // Esto hace que la espadita debajo de la mira tarde 3s en llenarse visualmente.
        player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 0.333333

        if (plugin.isReady) plugin.scoreboardManager.updatePlayer(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onArenaCombat(event: EntityDamageByEntityEvent) {
        if (event.entity.hasMetadata("mistaken_processing")) return

        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return

        if (plugin.gameManager.currentState != GameState.INGAME) return

        val isAttackerKiller = plugin.gameManager.esAsesino(attacker.uniqueId)
        val isVictimKiller = plugin.gameManager.esAsesino(victim.uniqueId)

        if (!isAttackerKiller && !isVictimKiller) return

        val now = System.currentTimeMillis()

        // 1. Fuego Amigo
        if (isAttackerKiller == isVictimKiller) {
            event.isCancelled = true
            return
        }

        // 2. Asesino vs Humano
        if (isAttackerKiller && !isVictimKiller) {
            val lastHit = killerCooldowns.getOrDefault(attacker.uniqueId, 0L)

            if (now - lastHit < KILLER_COOLDOWN) {
                val remaining = (KILLER_COOLDOWN - (now - lastHit)) / 1000.0
                attacker.sendActionBar(plugin.messageConfig.getMessage(attacker, "combat.cooldown", Placeholder.parsed("time", String.format(Locale.US, "%.1f", remaining))))
                event.isCancelled = true
                return
            }

            killerCooldowns[attacker.uniqueId] = now
            event.damage = 0.1 // KB Nativo
            processTrueDamage(victim, attacker, 3.0)
            return
        }

        // 3. Humano vs Asesino
        if (!isAttackerKiller && isVictimKiller) {
            val lastHit = survivorCooldowns.getOrDefault(attacker.uniqueId, 0L)

            if (now - lastHit < SURVIVOR_COOLDOWN) {
                val remaining = (SURVIVOR_COOLDOWN - (now - lastHit)) / 1000.0
                attacker.sendActionBar(plugin.messageConfig.getMessage(attacker, "combat.cooldown", Placeholder.parsed("time", String.format(Locale.US, "%.1f", remaining))))
                event.isCancelled = true
                return
            }

            survivorCooldowns[attacker.uniqueId] = now
            event.damage = 0.0 // KB Nativo
            processTrueDamage(victim, attacker, 4.0)
            victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)
            return
        }
    }

    private fun processTrueDamage(victim: Player, attacker: Player?, amount: Double) {
        if (isFrozen(victim)) return

        runOnMain {
            val nextHP = (victim.health - amount).coerceAtLeast(0.0)
            victim.health = nextHP

            val isSurvivor = !plugin.gameManager.esAsesino(victim.uniqueId)

            if (isSurvivor && nextHP <= 4.0 && nextHP > 0.0) {
                if (!victim.hasPotionEffect(PotionEffectType.DARKNESS)) {
                    val msg = plugin.messageConfig.getRawString(victim, "combat.critical-wound", "<red><bold>¡HERIDA CRÍTICA!</bold>")
                    victim.sendMessage(mm.deserialize(msg))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, Int.MAX_VALUE, 0, false, false, true))
                }
            }

            victim.world.spawnParticle(Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, Material.REDSTONE_BLOCK.createBlockData())

            if (plugin.isReady) plugin.scoreboardManager.updatePlayer(victim)

            if (nextHP <= 0.0) {
                victim.removePotionEffect(PotionEffectType.DARKNESS)
                victim.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
                frozenPlayers.remove(victim.uniqueId)

                plugin.gameManager.playerController.handlePlayerDeath(victim)
            }
        }
    }

    // --- Métodos de Gestión ---

    fun removePlayerData(uuid: UUID) {
        val p = Bukkit.getPlayer(uuid)
        p?.let {
            it.removePotionEffect(PotionEffectType.DARKNESS)
            it.isSwimming = false

            // Reset atributos de Freeze
            if (frozenPlayers.contains(uuid)) {
                it.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
                it.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.42
                it.clearTitle()
            }

            // 🔥 Restaurar Attack Speed a Default (4.0 es la velocidad normal de puños en Minecraft)
            it.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0
        }
        killerCooldowns.remove(uuid)
        survivorCooldowns.remove(uuid)
        frozenPlayers.remove(uuid)
    }

    fun clearAll() {
        frozenPlayers.clear()
        killerCooldowns.clear()
        survivorCooldowns.clear()
    }

    override fun unfreeze(victim: Player, rescuer: Player) {
        if (!frozenPlayers.remove(victim.uniqueId)) return
        runOnMain {
            victim.removePotionEffect(PotionEffectType.DARKNESS)
            victim.isSwimming = false

            victim.clearTitle()
            victim.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
            victim.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.42
            victim.inventory.helmet = null

            victim.health = 10.0
            victim.world.playSound(victim.location, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.5f)
        }
    }

    fun giveWinRewards(killerWon: Boolean) {
        val killers = plugin.gameManager.asesinosUUIDs
        val winners = if (killerWon) Bukkit.getOnlinePlayers().filter { killers.contains(it.uniqueId) }
        else Bukkit.getOnlinePlayers().filter { !killers.contains(it.uniqueId) && it.gameMode != GameMode.SPECTATOR }

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            winners.forEach { Mistaken.economy?.depositPlayer(it, if (killerWon) 500.0 else 200.0) }
        }
    }

    fun soltarPasajero(vehicle: Player) {
        vehicle.passengers.forEach {
            vehicle.removePassenger(it)
            if (it is Player && it.health <= 4.0) it.isSwimming = true
        }
    }

    fun freezePlayer(victim: Player) {
        if (!frozenPlayers.add(victim.uniqueId)) return
        runOnMain {
            victim.inventory.helmet = ItemStack(Material.BLUE_ICE)
            victim.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.0
            victim.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.0
            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false))
            victim.world.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)

            startFreezeTimer(victim)

            plugin.gameManager.broadcastLocalized("game.player-frozen", Placeholder.parsed("player", victim.name))
            plugin.gameManager.playerController.checkWinCondition()
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
                runOnMain {
                    plugin.gameManager.playerController.handlePlayerDeath(victim)
                }
            }
        }, null, 0L, 20L)
    }

    override fun takeDamage(victim: Player) { processTrueDamage(victim, null, 3.0) }
}
