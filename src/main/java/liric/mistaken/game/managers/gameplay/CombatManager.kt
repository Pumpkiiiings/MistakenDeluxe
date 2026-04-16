package liric.mistaken.game.managers.gameplay

import liric.mistaken.Mistaken
import liric.mistaken.api.HealthAPI
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.ChatColor
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
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CombatManager(private val plugin: Mistaken) : Listener, HealthAPI {

    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val killerCooldowns = ConcurrentHashMap<UUID, Long>()
    private val survivorCooldowns = ConcurrentHashMap<UUID, Long>()

    private val KILLER_COOLDOWN = 1000L
    private val SURVIVOR_COOLDOWN = 1000L

    private val mm = plugin.mm

    init {
        startRadarTask()
    }

    private fun startRadarTask() {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate
            val sessionManager = plugin.sessionManager ?: return@runAtFixedRate
            val sessions = sessionManager.activeSessions.values

            for (session in sessions) {
                if (session.currentState != GameState.INGAME) continue

                val killersOnline = session.asesinosUUIDs.mapNotNull { plugin.server.getPlayer(it) }.filter { it.isOnline }
                if (killersOnline.isEmpty()) continue

                for (killer in killersOnline) {
                    val killerLoc = killer.location
                    var minDistanceSq = Double.MAX_VALUE
                    var foundSomeone = false
                    var ghostName = ""

                    for (target in session.getPlayers()) {
                        if (target == killer || target.world != killerLoc.world) continue

                        if (session.esAsesino(target.uniqueId)) {
                            val mode = session.currentMode
                            target.scheduler.run(plugin, { _ ->
                                if (target.isOnline && killer.isOnline) {
                                    if (mode == MistakenMode.DOUBLE_KILLER || mode == MistakenMode.ONE_BOUNCE) {
                                        if (session.currentState == GameState.INGAME) {
                                            try { plugin.glowingAPI?.setGlowing(target, killer, ChatColor.YELLOW) } catch (_: Exception) {}
                                        } else {
                                            try { plugin.glowingAPI?.unsetGlowing(target, killer) } catch (_: Exception) {}
                                        }
                                    } else {
                                        try { plugin.glowingAPI?.unsetGlowing(target, killer) } catch (_: Exception) {}
                                    }
                                }
                            }, null)
                            continue
                        }

                        val tabName = PlainTextComponentSerializer.plainText().serialize(target.playerListName())
                        val isNPC = target.hasMetadata("NPC") || target.name.isEmpty() || tabName.isBlank()

                        val isValidSurvivor = target.gameMode == GameMode.SURVIVAL &&
                                !isNPC && !target.isInvisible && killer.canSee(target) &&
                                !plugin.isIgnored(target) && plugin.spectatorManager?.isSpectator(target) == false

                        if (isValidSurvivor) {
                            val distSq = killerLoc.distanceSquared(target.location)

                            if (distSq <= 225.0) {
                                target.scheduler.run(plugin, { _ ->
                                    if (target.isOnline && killer.isOnline) {
                                        if (session.currentState == GameState.INGAME) {
                                            try { plugin.glowingAPI?.setGlowing(target, killer, ChatColor.RED) } catch (_: Exception) {}
                                        } else {
                                            try { plugin.glowingAPI?.unsetGlowing(target, killer) } catch (_: Exception) {}
                                        }
                                    }
                                }, null)
                            } else {
                                target.scheduler.run(plugin, { _ ->
                                    if (target.isOnline && killer.isOnline) {
                                        try { plugin.glowingAPI?.unsetGlowing(target, killer) } catch (_: Exception) {}
                                    }
                                }, null)
                            }

                            if (distSq <= 900.0) {
                                if (distSq < minDistanceSq) {
                                    minDistanceSq = distSq
                                    foundSomeone = true
                                    ghostName = target.name
                                }
                            }
                        } else {
                            target.scheduler.run(plugin, { _ ->
                                if (target.isOnline && killer.isOnline) {
                                    try { plugin.glowingAPI?.unsetGlowing(target, killer) } catch (_: Exception) {}
                                }
                            }, null)
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
                }
            }
        }, 0L, 500L, TimeUnit.MILLISECONDS)
    }

    override fun getHealth(player: Player): Int = player.health.toInt()

    override fun setHealth(player: Player, health: Int) {
        player.scheduler.run(plugin, { _ ->
            val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            player.health = health.toDouble().coerceIn(0.0, max)
            plugin.scoreboardManager.updatePlayer(player)
        }, null)
    }

    override fun isFrozen(player: Player): Boolean = frozenPlayers.contains(player.uniqueId)

    override fun resetPlayer(player: Player) {
        removePlayerData(player.uniqueId)
        resetHealth(player)
    }

    fun resetHealth(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val session = plugin.sessionManager?.getSession(player)
            val isKiller = session?.esAsesino(player.uniqueId) ?: false
            val maxHP = if (isKiller) 160.0 else 20.0

            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHP
            player.health = maxHP
            player.removePotionEffect(PotionEffectType.DARKNESS)
            player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 1.0

            plugin.scoreboardManager.updatePlayer(player)
        }, null)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onArenaCombat(event: EntityDamageByEntityEvent) {
        if (event.entity.hasMetadata("mistaken_processing")) return
        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return

        if (victim.isInvisible || attacker.isInvisible || victim.gameMode == GameMode.ADVENTURE) {
            event.isCancelled = true
            return
        }

        val session = plugin.sessionManager?.getSession(victim) ?: return
        if (session.currentState != GameState.INGAME) return

        val isAttackerKiller = session.esAsesino(attacker.uniqueId)
        val isVictimKiller = session.esAsesino(victim.uniqueId)
        val isAssassinPvpMode = session.currentMode == MistakenMode.DOUBLE_KILLER

        if (isAttackerKiller == isVictimKiller && !isAssassinPvpMode) {
            event.isCancelled = true
            return
        }

        if (!isAttackerKiller && !isVictimKiller) {
            event.isCancelled = true
            return
        }

        val now = System.currentTimeMillis()

        if (isAttackerKiller) {
            val lastHit = killerCooldowns.getOrDefault(attacker.uniqueId, 0L)
            if (now - lastHit < KILLER_COOLDOWN) {
                val remaining = (KILLER_COOLDOWN - (now - lastHit)) / 1000.0
                attacker.sendActionBar(plugin.messageConfig.getMessage(attacker, "combat.cooldown", Placeholder.parsed("time", String.Companion.format(
                    Locale.US, "%.1f", remaining))))
                event.isCancelled = true
                return
            }
            killerCooldowns[attacker.uniqueId] = now
            event.damage = 0.1
            processTrueDamage(victim, attacker, if (isAssassinPvpMode) 4.0 else 3.0, session)
            return
        }

        if (!isAttackerKiller && isVictimKiller) {
            val lastHit = survivorCooldowns.getOrDefault(attacker.uniqueId, 0L)
            if (now - lastHit < SURVIVOR_COOLDOWN) {
                val remaining = (SURVIVOR_COOLDOWN - (now - lastHit)) / 1000.0
                attacker.sendActionBar(plugin.messageConfig.getMessage(attacker, "combat.cooldown", Placeholder.parsed("time", String.Companion.format(
                    Locale.US, "%.1f", remaining))))
                event.isCancelled = true
                return
            }
            survivorCooldowns[attacker.uniqueId] = now
            event.damage = 0.0
            processTrueDamage(victim, attacker, 4.0, session)
            victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)
        }
    }

    private fun processTrueDamage(victim: Player, attacker: Player?, amount: Double, session: GameSession? = null) {
        if (isFrozen(victim)) return
        val currentSession = session ?: plugin.sessionManager?.getSession(victim) ?: return

        victim.scheduler.run(plugin, { _ ->
            if (victim.gameMode == GameMode.SPECTATOR || victim.isInvisible || victim.gameMode == GameMode.ADVENTURE) return@run

            val nextHP = (victim.health - amount).coerceAtLeast(0.0)
            victim.health = nextHP

            val isSurvivor = !currentSession.esAsesino(victim.uniqueId)
            if (isSurvivor && nextHP <= 4.0 && nextHP > 0.0) {
                if (!victim.hasPotionEffect(PotionEffectType.DARKNESS)) {
                    val msg = plugin.messageConfig.getRawString(victim, "combat.critical-wound", "<red><bold>¡HERIDA CRÍTICA!</bold>")
                    victim.sendMessage(mm.deserialize(msg))
                    victim.addPotionEffect(
                        PotionEffect(
                            PotionEffectType.DARKNESS,
                            Int.MAX_VALUE,
                            0,
                            false,
                            false,
                            true
                        )
                    )
                }
            }

            victim.world.spawnParticle(Particle.BLOCK, victim.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, Material.REDSTONE_BLOCK.createBlockData())
            plugin.scoreboardManager.updatePlayer(victim)

            if (nextHP <= 0.0) {
                victim.health = 20.0
                victim.removePotionEffect(PotionEffectType.DARKNESS)
                victim.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
                frozenPlayers.remove(victim.uniqueId)

                currentSession.getCurrentAsesino()?.let { killer ->
                    try { plugin.glowingAPI?.unsetGlowing(victim, killer) } catch (_: Exception) {}
                }
                currentSession.playerController.handlePlayerDeath(victim)
            }
        }, null)
    }

    override fun takeDamage(victim: Player) { processTrueDamage(victim, null, 3.0) }

    override fun unfreeze(victim: Player, rescuer: Player) {
        if (!frozenPlayers.remove(victim.uniqueId)) return
        victim.scheduler.run(plugin, { _ ->
            victim.removePotionEffect(PotionEffectType.DARKNESS)
            victim.clearTitle()
            victim.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
            victim.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.42
            victim.inventory.helmet = null
            victim.health = 10.0
            victim.world.playSound(victim.location, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.5f)
        }, null)
    }

    fun giveWinRewards(killerWon: Boolean, session: GameSession) {
        val killers = session.asesinosUUIDs
        val winners = if (killerWon) {
            session.getPlayers().filter { killers.contains(it.uniqueId) }
        } else {
            session.getPlayers().filter { !killers.contains(it.uniqueId) && it.gameMode != GameMode.SPECTATOR }
        }
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            winners.forEach { Mistaken.Companion.economy?.depositPlayer(it, if (killerWon) 500.0 else 200.0) }
        }
    }

    fun soltarPasajero(vehicle: Player) {
        vehicle.scheduler.run(plugin, { _ ->
            vehicle.passengers.forEach { vehicle.removePassenger(it) }
        }, null)
    }

    fun freezePlayer(victim: Player, session: GameSession) {
        if (!frozenPlayers.add(victim.uniqueId)) return
        victim.scheduler.run(plugin, { _ ->
            victim.inventory.helmet = ItemStack(Material.BLUE_ICE)
            victim.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.0
            victim.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.0
            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false))
            victim.world.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)

            startFreezeTimer(victim, session)

            session.broadcastLocalized("game.player-frozen", Placeholder.parsed("player", victim.name))
            session.playerController.checkWinCondition()
        }, null)
    }

    private fun startFreezeTimer(victim: Player, session: GameSession) {
        var timeLeft = 120
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isFrozen(victim) || !victim.isOnline) {
                task.cancel()
                return@runAtFixedRate
            }
            victim.scheduler.run(plugin, { _ ->
                if (!isFrozen(victim)) {
                    task.cancel()
                    return@run
                }
                val timeFormatted = String.Companion.format(Locale.US, "%d:%02d", timeLeft / 60, timeLeft % 60)
                victim.showTitle(
                    Title.title(
                    plugin.messageConfig.getMessage(victim, "game.freeze-title"),
                    plugin.messageConfig.getMessage(victim, "game.freeze-subtitle", Placeholder.parsed("time", timeFormatted))
                ))
                timeLeft--
                if (timeLeft <= 0) {
                    task.cancel()
                    session.playerController.handlePlayerDeath(victim)
                }
            }, null)
        }, 0L, 1L, TimeUnit.SECONDS)
    }

    fun removePlayerData(uuid: UUID) {
        val target = plugin.server.getPlayer(uuid)
        target?.scheduler?.run(plugin, { _ ->
            target.removePotionEffect(PotionEffectType.DARKNESS)
            if (frozenPlayers.contains(uuid)) {
                target.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
                target.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.42
                target.clearTitle()
            }
            target.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0

            plugin.server.onlinePlayers.forEach { viewer ->
                if (target.isOnline && viewer.isOnline) {
                    try { plugin.glowingAPI?.unsetGlowing(target, viewer) } catch (_: Exception) {}
                    try { plugin.glowingAPI?.unsetGlowing(viewer, target) } catch (_: Exception) {}
                }
            }
        }, null)

        killerCooldowns.remove(uuid)
        survivorCooldowns.remove(uuid)
        frozenPlayers.remove(uuid)
    }

    fun clearAll() {
        frozenPlayers.clear()
        killerCooldowns.clear()
        survivorCooldowns.clear()

        for (p1 in plugin.server.onlinePlayers) {
            p1.scheduler.run(plugin, { _ ->
                for (p2 in plugin.server.onlinePlayers) {
                    try { plugin.glowingAPI?.unsetGlowing(p1, p2) } catch (_: Exception) {}
                    try { plugin.glowingAPI?.unsetGlowing(p2, p1) } catch (_: Exception) {}
                }
            }, null)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onKillerDarkness(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        val effect = event.newEffect ?: return

        if (effect.type == PotionEffectType.DARKNESS || effect.type == PotionEffectType.BLINDNESS) {
            val session = plugin.sessionManager?.getSession(player) ?: return
            if (session.esAsesino(player.uniqueId) && session.currentMode != MistakenMode.ASSASSIN_PVP) {
                event.isCancelled = true
            }
        }
    }
}