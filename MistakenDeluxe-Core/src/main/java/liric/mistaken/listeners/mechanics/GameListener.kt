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
        if (!session.activeModeHandler.enableRescueInteraction) return

        val victim = event.rightClicked as? Player ?: return

        if (plugin.combatManager.isFrozen(victim)) {
            if (!session.isKiller(player.uniqueId)) {
                if (plugin.combatManager.getHealth(player) <= 1) {
                    val msg = MessageService.getRawString(player, "game.too-injured-to-rescue", "<red>¡Estás muy herido para rescatar a nadie!")
                    player.sendActionBar(ColorTranslator.translate(msg))
                    return
                }

                event.isCancelled = true
                plugin.combatManager.unfreeze(victim, player)
                victim.world.spawnParticle(Particle.SNOWFLAKE, victim.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
                victim.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 1.5f)
            }
        }
    }

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
            killer.scheduler.runDelayed(plugin, java.util.function.Consumer {
                if (killer.isOnline) {
                    liric.mistaken.utils.hooks.ObserverHook.setTrueDarkness(killer, false)
                }
            }, null, 100L)
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

        val holder = event.inventory.holder
        if (holder is liric.mistaken.listeners.interactables.GeneratorListener.GeneratorHolder ||
            holder is liric.mistaken.listeners.interactables.HackTerminalListener.HackTerminalHolder ||
            holder is liric.mistaken.game.managers.gameplay.SpectatorManager.SpectatorHolder) {
            return
        }

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
            
            
            player.scheduler.run(plugin, java.util.function.Consumer {
                player.updateInventory()
            }, null)
            
            
            if (!session.isKiller(player.uniqueId) && !plugin.spectatorManager.isSpectator(player)) {
                val uuid = player.uniqueId
                val now = System.currentTimeMillis()
                
                if (isHealing[uuid] == true) return
                
                val lastHeal = healCooldowns[uuid] ?: 0L
                if (now - lastHeal < 30_000L) {
                    val remaining = (30_000L - (now - lastHeal)) / 1000L
                    val msg = MessageService.getRawString(player, "game.heal-cooldown", "<red>Debes esperar %time% s para volver a curarte.")
                        .replace("%time%", remaining.toString())
                    player.sendActionBar(ColorTranslator.translate(msg))
                    return
                }

                
                val target = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 4.5) {
                    it is Player && it != player && !session.isKiller(it.uniqueId) && !plugin.spectatorManager.isSpectator(it)
                }?.hitEntity as? Player

                val targetToHeal = target ?: player
                val maxHealth = targetToHeal.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0

                if (plugin.combatManager.getHealth(targetToHeal) >= maxHealth) {
                    if (targetToHeal == player) {
                        val msg = MessageService.getRawString(player, "game.already-max-health-self", "<red>¡Ya tienes la vida al máximo!")
                        player.sendActionBar(ColorTranslator.translate(msg))
                    } else {
                        val msg = MessageService.getRawString(player, "game.already-max-health-other", "<red>¡El jugador %target% ya tiene la vida al máximo!")
                            .replace("%target%", targetToHeal.name)
                        player.sendActionBar(ColorTranslator.translate(msg))
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
                
                val ticksBox = intArrayOf(0)
                player.scheduler.runAtFixedRate(plugin, java.util.function.Consumer { task ->
                    if (!player.isOnline || session.currentState != GameState.INGAME || plugin.spectatorManager.isSpectator(player) || !targetToHeal.isOnline) {
                        isHealing[uuid] = false
                        task.cancel()
                        return@Consumer
                    }

                    
                    if (player.location.distanceSquared(initialPlayerLoc) > 1.0 || 
                        targetToHeal.location.distanceSquared(initialTargetLoc) > 1.0) {
                        val cancelMsgKey = MessageService.getRawString(player, "game.heal-cancelled-movement", "<red>Curación cancelada por movimiento.")
                        val cancelMsg = ColorTranslator.translate(cancelMsgKey)
                        player.sendMessage(cancelMsg)
                        if (targetToHeal != player) targetToHeal.sendMessage(cancelMsg)
                        
                        isHealing[uuid] = false
                        
                        healCooldowns[uuid] = System.currentTimeMillis() - 25_000L 
                        task.cancel()
                        return@Consumer
                    }
                    
                    ticksBox[0] += 5
                    val ticks = ticksBox[0]
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
                            val tTitle = MessageService.getRawString(player, "game.heal-success-self.title", "<green>¡Curado!")
                            val tSub = MessageService.getRawString(player, "game.heal-success-self.subtitle", "<gray>+%hearts% corazones")
                                .replace("%hearts%", heartsToHeal.toString())
                            player.showTitle(net.kyori.adventure.title.Title.title(
                                ColorTranslator.translate(tTitle),
                                ColorTranslator.translate(tSub),
                                times
                            ))
                        } else {
                            val hTitle = MessageService.getRawString(player, "game.heal-success-healer.title", "<green>¡Has curado a %target%!")
                                .replace("%target%", targetToHeal.name)
                            val hSub = MessageService.getRawString(player, "game.heal-success-healer.subtitle", "<gray>+%hearts% corazones")
                                .replace("%hearts%", heartsToHeal.toString())
                            player.showTitle(net.kyori.adventure.title.Title.title(
                                ColorTranslator.translate(hTitle),
                                ColorTranslator.translate(hSub),
                                times
                            ))
                            
                            val htTitle = MessageService.getRawString(targetToHeal, "game.heal-success-healed.title", "<green>¡Has sido curado!")
                            val htSub = MessageService.getRawString(targetToHeal, "game.heal-success-healed.subtitle", "<gray>Por %player%")
                                .replace("%player%", player.name)
                            targetToHeal.showTitle(net.kyori.adventure.title.Title.title(
                                ColorTranslator.translate(htTitle),
                                ColorTranslator.translate(htSub),
                                times
                            ))
                            
                            liric.mistaken.Mistaken.economy?.deposit(player, 100.0)
                            val rewardMsg = MessageService.getRawString(player, "game.heal-reward-coins", "<green>+100 monedas por curar a un compañero.")
                            player.sendMessage(ColorTranslator.translate(rewardMsg))
                        }
                        
                        targetToHeal.playSound(targetToHeal.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                        if (targetToHeal != player) {
                            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                        }
                        
                        isHealing[uuid] = false
                        healCooldowns[uuid] = System.currentTimeMillis()
                        
                        
                        player.scheduler.runDelayed(plugin, java.util.function.Consumer {
                            if (player.isOnline && plugin.sessionManager.getSession(player)?.currentState == GameState.INGAME) {
                                val readyMsg = MessageService.getRawString(player, "game.heal-skill-ready", "<green>¡Tu habilidad de curación está lista!")
                                player.sendActionBar(ColorTranslator.translate(readyMsg))
                                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                            }
                        }, null, 30 * 20L)
                        
                        task.cancel()
                    } else {
                        val times = net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(500), java.time.Duration.ZERO)
                        
                        val formatTime = String.format(java.util.Locale.US, "%.1f", remainingSecs)
                        if (targetToHeal == player) {
                            val tTitle = MessageService.getRawString(player, "game.healing-self.title", "<yellow>Curándose...")
                            val tSub = MessageService.getRawString(player, "game.healing-self.subtitle", "<gray>%time%s")
                                .replace("%time%", formatTime)
                            player.showTitle(net.kyori.adventure.title.Title.title(
                                ColorTranslator.translate(tTitle),
                                ColorTranslator.translate(tSub),
                                times
                            ))
                        } else {
                            val hTitle = MessageService.getRawString(player, "game.healing-healer.title", "<yellow>Estás curando a %target%")
                                .replace("%target%", targetToHeal.name)
                            val hSub = MessageService.getRawString(player, "game.healing-healer.subtitle", "<gray>Tiempo: %time%s")
                                .replace("%time%", formatTime)
                            player.showTitle(net.kyori.adventure.title.Title.title(
                                ColorTranslator.translate(hTitle),
                                ColorTranslator.translate(hSub),
                                times
                            ))
                            
                            val htTitle = MessageService.getRawString(targetToHeal, "game.healing-healed.title", "<yellow>%player% te está curando...")
                                .replace("%player%", player.name)
                            val htSub = MessageService.getRawString(targetToHeal, "game.healing-healed.subtitle", "<gray>No te muevas. Tiempo: %time%s")
                                .replace("%time%", formatTime)
                            targetToHeal.showTitle(net.kyori.adventure.title.Title.title(
                                ColorTranslator.translate(htTitle),
                                ColorTranslator.translate(htSub),
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
                }, null, 0L, 5L)
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
