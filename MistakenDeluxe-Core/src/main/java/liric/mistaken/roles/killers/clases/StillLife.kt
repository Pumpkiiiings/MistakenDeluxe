package liric.mistaken.roles.killers.clases

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
    
    private val lastKilled = ConcurrentHashMap<UUID, String>()
    private val disguisedAs = ConcurrentHashMap<UUID, String>()
    private val fakeGenerators = ConcurrentHashMap<Location, UUID>()
    private var packetListener: com.github.retrooper.packetevents.event.PacketListenerAbstract? = null

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        
        packetListener = object : com.github.retrooper.packetevents.event.PacketListenerAbstract() {
            override fun onPacketReceive(event: com.github.retrooper.packetevents.event.PacketReceiveEvent) {
                if (event.packetType == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                    val packet = com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement(event)
                    val pos = packet.blockPosition
                    val p = event.getPlayer<Player>()
                    val loc = Location(p.world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                    
                    if (fakeGenerators.containsKey(loc)) {
                        event.isCancelled = true
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            handleFakeGeneratorTrap(p, loc)
                        })
                    }
                }
            }
        }
        com.github.retrooper.packetevents.PacketEvents.getAPI().eventManager.registerListener(packetListener!!)

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

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        // Items por defecto para skills (pueden ser overrideados por config si se arma el equip logic estandar)
        inv.setItem(1, org.bukkit.inventory.ItemStack(Material.FIRE_CHARGE))
        inv.setItem(2, org.bukkit.inventory.ItemStack(Material.IRON_SWORD))
        inv.setItem(3, org.bukkit.inventory.ItemStack(Material.RAW_IRON_BLOCK))
    }

    override fun useSkill(player: Player, slot: Int) {
        when (slot) {
            1 -> {
                if (!checkCooldown(player, 1)) {
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
            }
            2 -> {
                if (!checkCooldown(player, 2)) {
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
            }
            3 -> {
                if (!checkCooldown(player, 3)) {
                    // Fake Generator
                    val targetLoc = player.location.add(player.location.direction.multiply(2.0)).block.location
                    fakeGenerators[targetLoc] = player.uniqueId
                    
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
            }
        }
    }

    private fun handleFakeGeneratorTrap(survivor: Player, loc: Location) {
        val killerId = fakeGenerators.remove(loc)
        
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
        
        killerId?.let {
            Bukkit.getPlayer(it)?.sendMessage(pumpking.lib.color.ColorTranslator.translate(
                pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(Bukkit.getPlayer(it), "asesinos.still_life.habilidades.generador_interactuado", "killers_info")
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

    @EventHandler
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return
        if (session.isKiller(player.uniqueId) && plugin.playerDataManager.getSelectedKiller(player.uniqueId) == this.id) {
            event.isCancelled = true
            
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

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return
        if (session.isKiller(player.uniqueId) && plugin.playerDataManager.getSelectedKiller(player.uniqueId) == this.id) {
            val disguiseName = disguisedAs[player.uniqueId]
            if (disguiseName != null) {
                event.isCancelled = true
                Bukkit.broadcast(pumpking.lib.color.ColorTranslator.translate(
                    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(player, "asesinos.still_life.habilidades.chat_disfraz", "killers_info")
                        .replace("%disguisename%", disguiseName)
                        .replace("%message%", event.message)
                ))
            }
        }
    }

    override fun showTrail(player: Player) {}
    override fun showPhysicalTrail(player: Player) {}

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            disguisedAs.remove(it.uniqueId)
            it.customName = null
            it.isCustomNameVisible = false
        }
    }
}
