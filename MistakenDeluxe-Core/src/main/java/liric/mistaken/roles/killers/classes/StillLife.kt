package liric.mistaken.roles.killers.classes

import liric.mistaken.roles.killers.CoreKiller
import org.bukkit.entity.Player
import pumpking.lib.service.PumpkingServiceManager
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import org.bukkit.Location
import org.bukkit.Bukkit
import org.bukkit.Material
import java.util.function.Consumer

class StillLife : CoreKiller(
    "still_life",
    PumpkingServiceManager.messages.getStrictString(null, "asesinos.still_life.nombre", "killers_info")
), Listener {

    override val defaultMusic = "mistaken:still_life"
    
    override fun useSkill(player: Player, slot: Int) {}
    
    private val lastKilled = ConcurrentHashMap<UUID, String>()
    private val disguisedAs = ConcurrentHashMap<UUID, String>()
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)

        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            for (uuid in disguisedAs.keys) {
                val player = Bukkit.getPlayer(uuid)
                if (player != null && player.isOnline) {
                    val session = plugin.sessionManager.getSession(player)
                    if (session != null && session.isKiller(player.uniqueId) && plugin.playerDataManager.getSelectedKiller(player.uniqueId) == this.id) {
                        val closeSurvivor = player.getNearbyEntities(5.0, 5.0, 5.0).filterIsInstance<Player>().any { p ->
                            !session.isKiller(p.uniqueId) && !plugin.spectatorManager.isSpectator(p)
                        }
                        if (closeSurvivor) {
                            removeDisguise(player)
                        }
                    } else {
                        disguisedAs.remove(uuid)
                    }
                } else {
                    disguisedAs.remove(uuid)
                }
            }
        }, 20L, 10L)
    }

    override fun onTrigger(player: Player, triggerId: String) {
        when (triggerId) {
            "skill1" -> {
                // Smoke bomb
                val loc = player.location
                loc.world.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, loc, 200, 4.0, 2.0, 4.0, 0.05)
                loc.world.getNearbyPlayers(loc, 6.0).forEach { p ->
                    if (plugin.sessionManager.getSession(p)?.isKiller(p.uniqueId) != true) {
                        p.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 100, 0))
                    }
                }
                playSkillEffects(player, 1)
            }
            "skill2" -> {
                // Spikes
                val dir = player.location.direction.setY(0.0).normalize()
                var current = player.location.clone()
                for (i in 1..8) {
                    current.add(dir)
                    val spawnLoc = current.clone()
                    plugin.server.regionScheduler.runDelayed(plugin, spawnLoc, Consumer {
                        spawnLoc.world.spawn(spawnLoc, org.bukkit.entity.EvokerFangs::class.java)
                    }, (i * 2).toLong())
                }
                playSkillEffects(player, 2)
            }
            "skill3" -> {
                // Fake Generator
                val targetLoc = player.location.add(player.location.direction.multiply(2.0)).block.location
                
                val trap = liric.mistaken.roles.killers.triggers.traps.TrapDefinition(
                    ownerUuid = player.uniqueId,
                    killerId = this.id,
                    location = targetLoc
                ) { survivor, trapLoc ->
                    handleFakeGeneratorTrap(survivor, trapLoc, player.uniqueId)
                }
                liric.mistaken.roles.killers.triggers.traps.WorldTrapRegistry.registerTrap(trap)
                
                val blockState = io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitBlockData(Material.RAW_IRON_BLOCK.createBlockData())
                val packet = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange(
                    com.github.retrooper.packetevents.util.Vector3i(targetLoc.blockX, targetLoc.blockY, targetLoc.blockZ),
                    blockState.globalId
                )
                Bukkit.getOnlinePlayers().forEach { p ->
                    com.github.retrooper.packetevents.PacketEvents.getAPI().playerManager.sendPacket(p, packet)
                }
                player.sendMessage(pumpking.lib.color.ColorTranslator.translate(
                    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(player, "asesinos.still_life.habilidades.generador_colocado", "killers_info")
                ))
                playSkillEffects(player, 3)
            }
            "skill4" -> {
                // Spawn clones (Null armor)
                val loc = player.location
                val session = plugin.sessionManager.getSession(player)
                for (i in 1..4) {
                    val cloneLoc = loc.clone().add((Math.random() - 0.5) * 4, 0.0, (Math.random() - 0.5) * 4)
                    val zombie = loc.world.spawn(cloneLoc, org.bukkit.entity.Zombie::class.java)
                    
                    zombie.setAdult()
                    
                    val equip = zombie.equipment
                    if (equip != null) {
                        val helmet = org.bukkit.inventory.ItemStack(Material.LEATHER_HELMET).also { val m = it.itemMeta as org.bukkit.inventory.meta.LeatherArmorMeta; m.setColor(org.bukkit.Color.BLACK); it.itemMeta = m }
                        val chest = org.bukkit.inventory.ItemStack(Material.LEATHER_CHESTPLATE).also { val m = it.itemMeta as org.bukkit.inventory.meta.LeatherArmorMeta; m.setColor(org.bukkit.Color.BLACK); it.itemMeta = m }
                        val legs = org.bukkit.inventory.ItemStack(Material.LEATHER_LEGGINGS).also { val m = it.itemMeta as org.bukkit.inventory.meta.LeatherArmorMeta; m.setColor(org.bukkit.Color.BLACK); it.itemMeta = m }
                        val boots = org.bukkit.inventory.ItemStack(Material.LEATHER_BOOTS).also { val m = it.itemMeta as org.bukkit.inventory.meta.LeatherArmorMeta; m.setColor(org.bukkit.Color.BLACK); it.itemMeta = m }
                        
                        equip.helmet = helmet
                        equip.chestplate = chest
                        equip.leggings = legs
                        equip.boots = boots
                    }
                    
                    zombie.isCustomNameVisible = true
                    zombie.customName(pumpking.lib.color.ColorTranslator.translate("<dark_gray>Null"))
                    
                    // Seek survivor
                    if (session != null) {
                        val closest = loc.world.getNearbyEntities(loc, 30.0, 10.0, 30.0).filterIsInstance<Player>().firstOrNull {
                            !session.isKiller(it.uniqueId) && !plugin.spectatorManager.isSpectator(it)
                        }
                        if (closest != null) {
                            zombie.target = closest
                        }
                    }
                    
                    // Kill after 15 seconds
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        if (!zombie.isDead) {
                            zombie.health = 0.0
                        }
                    }, 20L * 15L)
                }
                
                player.sendMessage(pumpking.lib.color.ColorTranslator.translate("<green>¡Clones invocados!"))
                playSkillEffects(player, 4)
            }
            "disguise_toggle" -> {
                val last = lastKilled[player.uniqueId]
                if (last != null) {
                    if (disguisedAs.containsKey(player.uniqueId)) {
                        removeDisguise(player)
                    } else {
                        disguisedAs[player.uniqueId] = last
                        player.customName = last
                        player.isCustomNameVisible = true
                        player.sendMessage(pumpking.lib.color.ColorTranslator.translate(
                            pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(player, "asesinos.still_life.habilidades.disfrazado", "killers_info")
                                .replace("%last%", last)
                        ))
                    }
                }
            }
        }
    }

    private fun handleFakeGeneratorTrap(survivor: Player, loc: Location, killerUuid: UUID) {
        liric.mistaken.roles.killers.triggers.traps.WorldTrapRegistry.unregisterTrap(loc)
        
        val airState = io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitBlockData(Material.AIR.createBlockData())
        val packet = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange(
            com.github.retrooper.packetevents.util.Vector3i(loc.blockX, loc.blockY, loc.blockZ),
            airState.globalId
        )
        Bukkit.getOnlinePlayers().forEach { p ->
            com.github.retrooper.packetevents.PacketEvents.getAPI().playerManager.sendPacket(p, packet)
        }

        loc.world.createExplosion(loc, 2.0f, false, false)
        survivor.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 100, 0))
        
        Bukkit.getPlayer(killerUuid)?.let {
            it.sendMessage(pumpking.lib.color.ColorTranslator.translate(
                pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(it, "asesinos.still_life.habilidades.generador_interactuado", "killers_info")
            ))
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return
        
        val session = plugin.sessionManager.getSession(killer) ?: return
        if (session.isKiller(killer.uniqueId) && plugin.playerDataManager.getSelectedKiller(killer.uniqueId) == this.id) {
            lastKilled[killer.uniqueId] = victim.name
            killer.sendMessage(pumpking.lib.color.ColorTranslator.translate(
                pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(killer, "asesinos.still_life.habilidades.asesinado", "killers_info")
                    .replace("%victim%", victim.name)
            ))
        }
    }

    private fun removeDisguise(player: Player) {
        disguisedAs.remove(player.uniqueId)
        player.customName = null
        player.isCustomNameVisible = false
        player.sendMessage(pumpking.lib.color.ColorTranslator.translate(
            pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(player, "asesinos.still_life.habilidades.disfraz_quitado", "killers_info")
        ))
    }

    override fun onInterceptChat(player: Player, message: String): String? {
        val disguiseName = disguisedAs[player.uniqueId]
        if (disguiseName != null) {
            return pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(player, "asesinos.still_life.habilidades.chat_disfraz", "killers_info")
                .replace("%disguisename%", disguiseName)
                .replace("%message%", message)
        }
        return null
    }

    override fun showTrail(player: Player) {}
    override fun showPhysicalTrail(player: Player) {}

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            disguisedAs.remove(it.uniqueId)
            lastKilled.remove(it.uniqueId)
            it.customName = null
            it.isCustomNameVisible = false
        }
    }
}
