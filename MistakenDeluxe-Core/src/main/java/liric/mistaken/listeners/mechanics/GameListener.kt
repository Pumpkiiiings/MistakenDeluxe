package liric.mistaken.listeners.mechanics

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.event.player.PlayerRespawnEvent
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService


class GameListener(private val plugin: Mistaken) : Listener {

    private val mm = plugin.mm
    private val plain = PlainTextComponentSerializer.plainText()
    private val stunSoundsQueue = ConcurrentHashMap<UUID, MutableList<Int>>()

    /**
     * 🧊 SISTEMA DE RESCATE (Freeze Tag)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRescue(event: PlayerInteractEntityEvent) {
        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return 

        if (!plugin.isReady || session.currentState != GameState.INGAME) return
        if (session.activeModeHandler !is liric.mistaken.game.modes.handlers.FreezeTagModeHandler) return

        val victim = event.rightClicked as? Player ?: return

        if (plugin.combatManager.isFrozen(victim)) {
            if (!session.isKiller(player.uniqueId)) {
                if (plugin.combatManager.getHealth(player) <= 1) {
                    player.sendActionBar(ColorTranslator.translate("<red>�Est�s muy herido para rescatar a nadie!"))
                    return
                }

                event.isCancelled = true
                plugin.combatManager.unfreeze(victim, player)
                victim.world.spawnParticle(Particle.SNOWFLAKE, victim.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
                victim.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 1.5f)
            }
        }
    }

    /**
     * ?? EFECTOS VISUALES Y STUN
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamageEffects(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val session = plugin.sessionManager.getSession(victim) ?: return 

        if (!plugin.isReady || session.currentState != GameState.INGAME) return

        val damager = when (val attacker = event.damager) {
            is Player -> attacker
            is Projectile -> attacker.shooter as? Player
            else -> null
        } ?: return

        val isDamagerKiller = session.isKiller(damager.uniqueId)
        val isVictimKiller = session.isKiller(victim.uniqueId)

        if (!isDamagerKiller && isVictimKiller) {
            
            if (plugin.spectatorManager.isSpectator(damager)) return

            val killerHealth = plugin.combatManager.getHealth(victim)
            damager.sendActionBar(MessageService.getComponent(damager, "game.killer-hit-actionbar",
                Placeholder.parsed("health", killerHealth.toString())))

            if (ThreadLocalRandom.current().nextInt(100) < 15) {
                applyStunToKiller(victim, damager)
            }
        }
    }

    /**
     * ?? MUERTE L�GICA POR ARENA
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val session = plugin.sessionManager.getSession(victim) ?: return 

        if (!plugin.isReady || session.currentState != GameState.INGAME) return

        val deathLoc = victim.location.clone()
        event.drops.clear()
        event.droppedExp = 0
        event.deathMessage(null)

        session.activeModeHandler.onPlayerDeathEvent(event)

        
        session.playerController.handlePlayerDeath(victim)

        victim.scheduler.runDelayed(plugin, Consumer { _ ->
            if (victim.isOnline && victim?.isValid == false) {
                victim.spigot().respawn()
                victim.scheduler.runDelayed(plugin, Consumer { _ ->
                    if (session.currentState == GameState.INGAME) {
                        
                        if (!session.isKiller(victim.uniqueId)) {
                            plugin.spectatorManager.setCustomSpectator(victim)
                        }
                    }
                }, null, 2L)
            }
        }, null, 1L)
    }

    
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return

        session.activeModeHandler.onPlayerRespawnEvent(event)
    }

    private fun applyStunToKiller(killer: Player, damager: Player) {
        killer.removePotionEffect(PotionEffectType.SPEED)
        killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
        killer.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
        killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 4)) 
        killer.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 100, 200)) 

        if (liric.mistaken.utils.hooks.ObserverHook.hasObserverPlugin) {
            val anim = liric.mistaken.utils.hooks.ObserverHook.getAnimation(killer, "stun", "")
            if (anim.isNotEmpty()) {
                com.observer.paper.api.PaperObserverAnimationAPI.playAnimation(killer, anim)
            }
            
            liric.mistaken.utils.hooks.ObserverHook.setTrueDarkness(killer, true)
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (killer.isOnline) {
                    liric.mistaken.utils.hooks.ObserverHook.setTrueDarkness(killer, false)
                }
            }, 100L)
        }

        killer.playSound(killer.location, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 0.5f)
        killer.world.spawnParticle(Particle.ENCHANTED_HIT, killer.location.add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)

        killer.sendMessage(MessageService.getComponent(killer, "game.killer-stunned-victim"))
        damager.sendMessage(MessageService.getComponent(damager, "game.killer-stunned-damager"))

        val killerClass = plugin.playerDataManager.getSelectedKiller(killer.uniqueId)
        if (killerClass == "slasher") {
            val uuid = killer.uniqueId
            val queue = stunSoundsQueue.getOrPut(uuid) { mutableListOf(1, 2).apply { shuffle() } }
            if (queue.isEmpty()) { queue.addAll(listOf(1, 2)); queue.shuffle() }

            val soundIndex = queue.removeAt(0)
            killer.world.playSound(killer.location, "mistaken:whitepumpkin_stun_$soundIndex", SoundCategory.PLAYERS, 3.0f, 1.0f)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEnvironmentalDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val session = plugin.sessionManager.getSession(player) ?: return

        if (!plugin.isReady || session.currentState != GameState.INGAME) return

        if (plugin.combatManager.isFrozen(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val session = plugin.sessionManager.getSession(player) ?: return

        if (!plugin.isReady || session.currentState != GameState.INGAME) return

        val type = event.inventory.type
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) return

        val title = plain.serialize(event.view.title())
        val allowed = listOf("Reparando", "Skill Check", "ENTES", "Tienda", "Selecciona", "Espectear", "Terminal", "Hackeo", "Código", "Panel")
        if (allowed.any { title.contains(it, ignoreCase = true) }) return

        event.isCancelled = true
    }

    @EventHandler
    fun onHungerChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        val session = plugin.sessionManager.getSession(player) ?: return

        if (plugin.isReady && session.currentState == GameState.INGAME) {
            event.isCancelled = true
            if (player.foodLevel < 20) player.foodLevel = 20
        }
    }

    private val healCooldowns = ConcurrentHashMap<UUID, Long>()
    private val isHealing = ConcurrentHashMap<UUID, Boolean>()

    
    @EventHandler fun onDrop(e: PlayerDropItemEvent) {
        val player = e.player
        val session = plugin.sessionManager.getSession(player)
        
        if (session?.currentState == GameState.INGAME || session?.currentState == GameState.STARTING) {
            e.isCancelled = true
            
            
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.updateInventory()
            })
            
            
            if (!session.isKiller(player.uniqueId) && !plugin.spectatorManager.isSpectator(player)) {
                val uuid = player.uniqueId
                val now = System.currentTimeMillis()
                
                if (isHealing[uuid] == true) return
                
                val lastHeal = healCooldowns[uuid] ?: 0L
                if (now - lastHeal < 30_000L) {
                    val remaining = (30_000L - (now - lastHeal)) / 1000L
                    player.sendActionBar(ColorTranslator.translate("<red>Debes esperar $remaining s para volver a curarte."))
                    return
                }

                
                val target = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 4.5) {
                    it is Player && it != player && !session.isKiller(it.uniqueId) && !plugin.spectatorManager.isSpectator(it)
                }?.hitEntity as? Player

                val targetToHeal = target ?: player
                val maxHealth = targetToHeal.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0

                if (plugin.combatManager.getHealth(targetToHeal) >= maxHealth) {
                    if (targetToHeal == player) {
                        player.sendActionBar(ColorTranslator.translate("<red>¡Ya tienes la vida al máximo!"))
                    } else {
                        player.sendActionBar(ColorTranslator.translate("<red>¡El jugador ${targetToHeal.name} ya tiene la vida al máximo!"))
                    }
                    return
                }
                
                isHealing[uuid] = true
                val totalTicks = 60 
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, totalTicks, 1, false, false))
                
                if (targetToHeal != player) {
                    targetToHeal.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, totalTicks, 1, false, false))
                }

                val initialPlayerLoc = player.location.clone()
                val initialTargetLoc = targetToHeal.location.clone()
                
                object : org.bukkit.scheduler.BukkitRunnable() {
                    var ticks = 0
                    override fun run() {
                        if (!player.isOnline || session.currentState != GameState.INGAME || plugin.spectatorManager.isSpectator(player) || !targetToHeal.isOnline) {
                            isHealing[uuid] = false
                            cancel()
                            return
                        }

                        
                        if (player.location.distanceSquared(initialPlayerLoc) > 1.0 || 
                            targetToHeal.location.distanceSquared(initialTargetLoc) > 1.0) {
                            
                            val cancelMsg = ColorTranslator.translate("<red>Curación cancelada por movimiento.")
                            player.sendMessage(cancelMsg)
                            if (targetToHeal != player) targetToHeal.sendMessage(cancelMsg)
                            
                            isHealing[uuid] = false
                            
                            healCooldowns[uuid] = System.currentTimeMillis() - 25_000L 
                            cancel()
                            return
                        }
                        
                        ticks += 5
                        val remainingSecs = (totalTicks - ticks) / 20.0
                        
                        if (ticks >= totalTicks) {
                            val heartsToHeal = java.util.concurrent.ThreadLocalRandom.current().nextInt(2, 8)
                            val healthToHeal = heartsToHeal * 2.0
                            
                            val currentHealth = plugin.combatManager.getHealth(targetToHeal).toDouble()
                            if (currentHealth < maxHealth) {
                                val newHealth = (currentHealth + healthToHeal).coerceAtMost(maxHealth)
                                plugin.combatManager.setHealth(targetToHeal, newHealth.toInt())
                            }
                            
                            val times = net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(1000), java.time.Duration.ofMillis(250))
                            
                            if (targetToHeal == player) {
                                player.showTitle(net.kyori.adventure.title.Title.title(
                                    ColorTranslator.translate("<green>¡Curado!"),
                                    ColorTranslator.translate("<gray>+$heartsToHeal corazones"),
                                    times
                                ))
                            } else {
                                player.showTitle(net.kyori.adventure.title.Title.title(
                                    ColorTranslator.translate("<green>¡Has curado a ${targetToHeal.name}!"),
                                    ColorTranslator.translate("<gray>+$heartsToHeal corazones"),
                                    times
                                ))
                                targetToHeal.showTitle(net.kyori.adventure.title.Title.title(
                                    ColorTranslator.translate("<green>¡Has sido curado!"),
                                    ColorTranslator.translate("<gray>Por ${player.name}"),
                                    times
                                ))
                                
                                
                                liric.mistaken.Mistaken.economy?.depositPlayer(player, 100.0)
                                player.sendMessage(ColorTranslator.translate("<green>+100 monedas por curar a un compañero."))
                            }
                            
                            targetToHeal.playSound(targetToHeal.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                            if (targetToHeal != player) {
                                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                            }
                            
                            isHealing[uuid] = false
                            healCooldowns[uuid] = System.currentTimeMillis()
                            
                            
                            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                if (player.isOnline && plugin.sessionManager.getSession(player)?.currentState == GameState.INGAME) {
                                    player.sendActionBar(ColorTranslator.translate("<green>¡Tu habilidad de curación está lista!"))
                                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                                }
                            }, 30 * 20L)
                            
                            cancel()
                        } else {
                            val times = net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(500), java.time.Duration.ZERO)
                            
                            if (targetToHeal == player) {
                                player.showTitle(net.kyori.adventure.title.Title.title(
                                    ColorTranslator.translate("<yellow>Curándose..."),
                                    ColorTranslator.translate("<gray>${String.format(java.util.Locale.US, "%.1f", remainingSecs)}s"),
                                    times
                                ))
                            } else {
                                player.showTitle(net.kyori.adventure.title.Title.title(
                                    ColorTranslator.translate("<yellow>Estás curando a ${targetToHeal.name}"),
                                    ColorTranslator.translate("<gray>Tiempo: ${String.format(java.util.Locale.US, "%.1f", remainingSecs)}s"),
                                    times
                                ))
                                targetToHeal.showTitle(net.kyori.adventure.title.Title.title(
                                    ColorTranslator.translate("<yellow>${player.name} te está curando..."),
                                    ColorTranslator.translate("<gray>No te muevas. Tiempo: ${String.format(java.util.Locale.US, "%.1f", remainingSecs)}s"),
                                    times
                                ))
                            }
                            
                            if (ticks % 10 == 0) {
                                targetToHeal.playSound(targetToHeal.location, Sound.ENTITY_GENERIC_DRINK, 0.5f, 1.0f)
                                if (targetToHeal != player) {
                                    player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 0.5f, 1.0f)
                                }
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 5L)
            }
        }
    }

    @EventHandler fun onCraft(e: CraftItemEvent) {
        val session = plugin.sessionManager.getSession(e.whoClicked as Player)
        if (session?.currentState == GameState.INGAME || session?.currentState == GameState.STARTING) e.isCancelled = true
    }

    @EventHandler fun onBreak(e: BlockBreakEvent) {
        val session = plugin.sessionManager.getSession(e.player)
        if ((session?.currentState == GameState.INGAME || session?.currentState == GameState.STARTING) && !e.player.hasPermission("mistaken.admin")) e.isCancelled = true
    }

    @EventHandler fun onPlace(e: BlockPlaceEvent) {
        val session = plugin.sessionManager.getSession(e.player)
        if ((session?.currentState == GameState.INGAME || session?.currentState == GameState.STARTING) && !e.player.hasPermission("mistaken.admin")) e.isCancelled = true
    }

    @EventHandler fun onInventoryClick(e: org.bukkit.event.inventory.InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        if (player.hasPermission("mistaken.admin") && player.gameMode == GameMode.CREATIVE) return
        val session = plugin.sessionManager.getSession(player) ?: return
        
        if (session.currentState == GameState.STARTING) {
            e.isCancelled = true
            return
        }
        
        if (session.currentState == GameState.INGAME) {
            if (e.slotType == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR || e.rawSlot == 45) {
                e.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onPlayerDismount(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (event.isSneaking && player.passengers.any { !liric.mistaken.utils.misc.EntityUtils.isHUDEntity(it) }) {
            plugin.combatManager.soltarPasajero(player)
        }
    }

    @EventHandler
    fun onRegen(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        val session = plugin.sessionManager.getSession(player)

        if (session?.currentState == GameState.INGAME) {
            val r = event.regainReason
            if (r == EntityRegainHealthEvent.RegainReason.SATIATED || r == EntityRegainHealthEvent.RegainReason.REGEN) {
                event.isCancelled = true
            }
        }
    }
}
