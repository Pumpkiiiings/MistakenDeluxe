package liric.mistaken.scripting.services

import liric.mistaken.Mistaken
import liric.mistaken.utils.hooks.ObserverHook
import org.bukkit.Color
import org.bukkit.Location
import liric.mistaken.utils.worldViewers
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.function.Consumer

object SkillService {
    
    private val plugin = Mistaken.instance

    fun delay(ticks: Long, action: () -> Unit) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable { action() }, ticks)
    }

    fun drawStar(player: Player, color: Color, radius: Double, points: Int) {
        liric.mistaken.utils.visuals.ParticleShapesUtils.drawStar(player.location, Particle.DUST, radius, points)
    }

    fun playScreenTint(player: Player, r: Int, g: Int, b: Int, alpha: Float, durationTicks: Int) {
        ObserverHook.playScreenTint(player, r, g, b, alpha, durationTicks)
    }

    fun playScreenshake(player: Player, intensity: Float, durationTicks: Int) {
        ObserverHook.playScreenshake(player, intensity, durationTicks)
    }

    fun playSound(player: Player, sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
        player.playSound(player.location, sound, volume, pitch)
    }
    
    fun playSound(player: Player, customSoundId: String, volume: Float = 1.0f, pitch: Float = 1.0f) {
        ObserverHook.playSound(player, customSoundId, volume, pitch)
    }

    fun spawnFakeSwarm(loc: Location, count: Int, durationTicks: Long) {
        val world = loc.world ?: return
        val pm = com.github.retrooper.packetevents.PacketEvents.getAPI().playerManager
        val fakeIds = mutableListOf<Int>()
        for(i in 1..count) {
            val fakeId = java.util.concurrent.ThreadLocalRandom.current().nextInt(500000, 600000)
            fakeIds.add(fakeId)
            val spawnPacket = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity(
                fakeId, java.util.Optional.of(java.util.UUID.randomUUID()), com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.BAT,
                com.github.retrooper.packetevents.util.Vector3d(loc.x, loc.y + 2.0, loc.z), loc.pitch, loc.yaw, loc.yaw, 0, java.util.Optional.empty()
            )
            world.players.forEach { pm.sendPacket(it, spawnPacket) }
        }
        plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
            fakeIds.forEach { fakeId ->
                world.players.forEach { pm.sendPacket(it, com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities(fakeId)) }
            }
        }, durationTicks)
    }

    fun spawnVirtualTempBlock(loc: Location, material: org.bukkit.Material, tx: Float, ty: Float, tz: Float, sx: Float, sy: Float, sz: Float, durationTicks: Long) {
        val display = liric.mistaken.packet.PacketFactory.displays.buildBlockDisplay(loc.worldViewers(), loc) { bd ->
            bd.block = material.createBlockData()
            bd.transformation = org.bukkit.util.Transformation(
                org.joml.Vector3f(tx, ty, tz),
                org.joml.Quaternionf(),
                org.joml.Vector3f(sx, sy, sz),
                org.joml.Quaternionf()
            )
        }
        plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
            display.remove()
        }, durationTicks)
    }

    fun applyGlowingTeam(player: Player, targets: List<Player>, teamColorName: String, durationTicks: Long) {
        val teamName = "hb_glow_${player.entityId}"
        val color = net.kyori.adventure.text.format.NamedTextColor.NAMES.value(teamColorName.lowercase()) ?: net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE
        val teamInfo = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo(
            net.kyori.adventure.text.Component.text(teamName), net.kyori.adventure.text.Component.empty(), net.kyori.adventure.text.Component.empty(),
            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility.ALWAYS, com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule.NEVER,
            color, com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.OptionData.NONE
        )
        val targetNames = targets.map { it.name }
        val createTeam = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams(teamName, com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, targetNames)
        val pm = com.github.retrooper.packetevents.PacketEvents.getAPI().playerManager
        pm.sendPacket(player, createTeam)
        targets.forEach { online ->
            val metadata = listOf(com.github.retrooper.packetevents.protocol.entity.data.EntityData(0, com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes.BYTE, 0x40.toByte()))
            pm.sendPacket(player, com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata(online.entityId, metadata))
        }
        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline) {
                val removeTeam = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams(teamName, com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode.REMOVE, java.util.Optional.empty())
                pm.sendPacket(player, removeTeam)
            }
        }, null, durationTicks)
    }

    fun spawnTempItemDisplay(loc: Location, material: org.bukkit.Material, sx: Float, sy: Float, sz: Float, glowColorName: String, durationTicks: Long) {
        val world = loc.world ?: return
        val display = world.spawn(loc, org.bukkit.entity.ItemDisplay::class.java) { id ->
            id.setItemStack(org.bukkit.inventory.ItemStack(material))
            id.transformation = org.bukkit.util.Transformation(
                org.joml.Vector3f(),
                org.joml.Quaternionf(),
                org.joml.Vector3f(sx, sy, sz),
                org.joml.Quaternionf()
            )
            id.isGlowing = true
            val color = try {
                org.bukkit.Color::class.java.fields.find { it.name.equals(glowColorName, ignoreCase = true) }?.get(null) as? org.bukkit.Color
            } catch (e: Exception) { null } ?: org.bukkit.Color.WHITE
            id.glowColorOverride = color
        }
        plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
            display.remove()
        }, durationTicks)
    }

    fun spawnSpinningTnt(loc: Location, durationTicks: Long) {
        val world = loc.world ?: return
        val tnt = world.spawn(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.entity.BlockDisplay::class.java) { bd ->
            bd.block = org.bukkit.Material.TNT.createBlockData()
            bd.transformation = org.bukkit.util.Transformation(
                org.joml.Vector3f(-0.5f, -0.5f, -0.5f),
                org.joml.Quaternionf(),
                org.joml.Vector3f(1f, 1f, 1f),
                org.joml.Quaternionf()
            )
            bd.teleportDuration = 20
        }
        
        plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
            tnt.teleport(loc.clone().add(0.0, 3.0, 0.0))
            val t = tnt.transformation
            t?.leftRotation?.rotateY(5f)?.rotateX(2f)
            if (t != null) tnt.transformation = t
        }, 1L)

        plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
            tnt.remove()
        }, durationTicks)
    }

    fun spawnEvokerFang(loc: Location) {
        val world = loc.world ?: return
        world.spawn(loc, org.bukkit.entity.EvokerFangs::class.java)
    }

    fun spawnBlinkingRitual(loc: Location, material: org.bukkit.Material, count: Int, radius: Double, durationTicks: Long) {
        val world = loc.world ?: return
        val displays = mutableListOf<org.bukkit.entity.BlockDisplay>()
        
        for (i in 0 until count) {
            val angle = (i * Math.PI * 2) / count
            val x = radius * kotlin.math.cos(angle)
            val z = radius * kotlin.math.sin(angle)
            val bd = world.spawn(loc.clone().add(x, 0.5, z), org.bukkit.entity.BlockDisplay::class.java) {
                it.block = material.createBlockData()
                it.transformation = org.bukkit.util.Transformation(
                    org.joml.Vector3f(-0.4f, -0.4f, -0.4f),
                    org.joml.Quaternionf(),
                    org.joml.Vector3f(0.8f, 0.8f, 0.8f),
                    org.joml.Quaternionf()
                )
                it.teleportDuration = 2
            }
            displays.add(bd)
        }
        
        var blink = 0
        plugin.server.regionScheduler.runAtFixedRate(plugin, loc, Consumer { t ->
            if (blink * 5 >= durationTicks) { t.cancel(); return@Consumer }
            displays.forEach { bd ->
                if (bd.isValid) {
                    val offset = if (blink % 2 == 0) 0.5 else -0.5
                    bd.teleport(bd.location.add(0.0, offset, 0.0))
                }
            }
            world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, loc.clone().add(0.0, 1.5, 0.0), 5, 1.0, 0.5, 1.0, 0.05)
            blink++
        }, 5L, 5L)
        
        plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
            displays.forEach { if (it.isValid) it.remove() }
        }, durationTicks)
    }

    fun spawnTrackingHitbox(player: liric.mistaken.scripting.adapter.BukkitPlayerAdapter, sx: Double, sy: Double, sz: Double, material: org.bukkit.Material, durationTicks: Long) {
        val bukkitPlayer = player.getPlayer()
        val hitbox = liric.mistaken.utils.misc.HitboxVisualizer.createHitbox(bukkitPlayer.location, sx, sy, sz, material)
        var ticks = 0L
        plugin.server.regionScheduler.runAtFixedRate(plugin, bukkitPlayer.location, Consumer { task ->
            if (ticks >= durationTicks || !bukkitPlayer.isOnline) {
                hitbox?.remove()
                task.cancel()
                return@Consumer
            }
            hitbox?.teleport(bukkitPlayer.location)
            ticks++
        }, 1L, 1L)
    }

    fun drawInstantHitbox(loc: Location, sx: Double, sy: Double, sz: Double, durationTicks: Long, material: org.bukkit.Material) {
        liric.mistaken.utils.misc.HitboxVisualizer.drawInstantHitbox(plugin, loc, sx, sy, sz, durationTicks, material)
    }
}
