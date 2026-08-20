import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import liric.mistaken.roles.killers.CoreKiller
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import liric.mistaken.utils.ViewerScopeKt
import org.bukkit.entity.*
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualBlockDisplay
import liric.mistaken.packet.fake.VirtualDisplay
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager
import liric.mistaken.utils.visuals.ParticleShapesUtils
import org.bukkit.inventory.ItemStack

class HerobrineScript extends CoreKiller {

    private final Map blockOrbiters = new ConcurrentHashMap()
    private final Map itemOrbiters = new ConcurrentHashMap()
    private final Map angulos = new ConcurrentHashMap()
    private final Map lastKillEffect = new ConcurrentHashMap()

    HerobrineScript() {
        super("herobrine", PumpkingServiceManager.messages.getStrictString(null, "asesinos.herobrine.nombre", "killers_info"))
        
        onEvent(EntityDamageByEntityEvent.class, org.bukkit.event.EventPriority.MONITOR, true, { event ->
            onHerobrineKill(event)
        })
    }

    @Override
    void useSkill(Player player, int slot) {
        if (checkCooldown(player, slot)) return
        switch (slot) {
            case 1: habilidadDashVacio(player); break
            case 2: habilidadSaltoDimensional(player); break
            case 3: habilidadEstrellaWither(player); break
            case 4: habilidadErrorMundo(player); break
        }
        playSkillEffects(player, slot)
    }

    void onHerobrineKill(EntityDamageByEntityEvent event) {
        if (!(event.damager instanceof Player)) return
        if (!(event.entity instanceof Player)) return
        Player attacker = (Player) event.damager
        Player victim = (Player) event.entity

        def session = plugin.sessionManager.getSession(attacker)
        if (session == null) return
        
        if (session.isKiller(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
            if (victim.gameMode == GameMode.SPECTATOR) {
                long now = System.currentTimeMillis()
                long last = lastKillEffect.getOrDefault(victim.uniqueId, 0L) as Long
                if (now - last > 2000L) {
                    lastKillEffect.put(victim.uniqueId, now)
                    triggerFinisherAleatorio(victim.location)
                }
            }
        }
    }

    private void triggerFinisherAleatorio(Location loc) {
        int effectType = ThreadLocalRandom.current().nextInt(3)
        World world = loc.world
        if (world == null) return

        if (effectType == 0) {
            world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.5f)
            def cruz = world.spawn(loc.clone().add(0.0, 1.0, 0.0), BlockDisplay.class, { it ->
                it.block = Material.OBSIDIAN.createBlockData()
                it.transformation = new Transformation(new org.joml.Vector3f(-1.5f, -2.5f, -0.5f), new Quaternionf(), new org.joml.Vector3f(3f, 5f, 1f), new Quaternionf())
            })
            def brazoCruz = world.spawn(loc.clone().add(0.0, 2.5, 0.0), BlockDisplay.class, { it ->
                it.block = Material.OBSIDIAN.createBlockData()
                it.transformation = new Transformation(new org.joml.Vector3f(-2.5f, -0.5f, -0.5f), new Quaternionf(), new org.joml.Vector3f(5f, 1f, 1f), new Quaternionf())
            })
            runRegionDelayed(loc, 10L, { _ ->
                world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 0.1f)
                ParticleShapesUtils.drawDnaHelix(loc, org.bukkit.Particle.SOUL_FIRE_FLAME)
            })
            runRegionDelayed(loc, 30L, { _ ->
                cruz.remove()
                brazoCruz.remove()
                world.spawnParticle(org.bukkit.Particle.BLOCK, loc, 150, 1.0, 2.0, 1.0, Material.OBSIDIAN.createBlockData())
            })
        } else if (effectType == 1) {
            world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1f)
            def beacon = PacketFactory.displays.buildBlockDisplay(ViewerScopeKt.worldViewers(loc), loc, { it ->
                it.block = Material.BEACON.createBlockData()
                it.transformation = new Transformation(new org.joml.Vector3f(-0.5f, 0f, -0.5f), new Quaternionf(), new org.joml.Vector3f(1f, 10f, 1f), new Quaternionf())
                it.isGlowing = true
            })
            def pm = PacketEvents.getAPI().playerManager
            for(int i = 1; i <= 5; i++) {
                int fakeId = ThreadLocalRandom.current().nextInt(500000, 600000)
                def spawnPacket = new WrapperPlayServerSpawnEntity(
                    fakeId, Optional.of(UUID.randomUUID()), EntityTypes.BAT,
                    new Vector3d(loc.x, loc.y + 2.0, loc.z), loc.pitch, loc.yaw, loc.yaw, 0, Optional.empty()
                )
                world.players.forEach({ pm.sendPacket(it, spawnPacket) })
                runRegionDelayed(loc, 25L, { _ ->
                    world.players.forEach({ pm.sendPacket(it, new WrapperPlayServerDestroyEntities(fakeId)) })
                })
            }
            runRegionDelayed(loc, 25L, { _ ->
                world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.5f)
                ParticleShapesUtils.drawTornado(loc, org.bukkit.Particle.CLOUD)
                beacon.remove()
            })
        } else {
            world.playSound(loc, Sound.BLOCK_STONE_PLACE, 1f, 0.1f)
            def altar = PacketFactory.displays.buildBlockDisplay(ViewerScopeKt.worldViewers(loc), loc, { it ->
                it.block = Material.MOSSY_COBBLESTONE.createBlockData()
                it.transformation = new Transformation(new org.joml.Vector3f(-1.5f, -0.5f, -1.5f), new Quaternionf(), new org.joml.Vector3f(3f, 1f, 3f), new Quaternionf())
            })
            runRegionDelayed(loc, 10L, { _ ->
                world.spawnParticle(org.bukkit.Particle.FLAME, loc.clone().add(1.5, 0.5, 1.5), 10, 0.0d, 0.0d, 0.0d, 0.0d)
                world.spawnParticle(org.bukkit.Particle.FLAME, loc.clone().add(-1.5, 0.5, -1.5), 10, 0.0d, 0.0d, 0.0d, 0.0d)
                world.playSound(loc, Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1f)
            })
            runRegionDelayed(loc, 20L, { _ ->
                ParticleShapesUtils.drawShockwave(loc, org.bukkit.Particle.LARGE_SMOKE)
                world.playSound(loc, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 0.5f)
                altar.remove()
            })
        }
    }

    private void habilidadDashVacio(Player player) {
        def dir = player.location.direction.normalize()
        player.velocity = dir.clone().multiply(2.5).setY(0.2)
        player.world.spawnParticle(org.bukkit.Particle.FIREWORK, player.location.add(0.0, 1.0, 0.0), 5, 0.2d, 0.2d, 0.2d, 0.0d)
        
        int ticks = 0
        def hitted = new HashSet()
        runTimer(player, 1L, 1L, { task ->
            if (ticks >= 12 || !player.isOnline) {
                task.cancel()
                return
            }
            player.world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, player.location, 3, 0.1d, 0.1d, 0.1d, 0.02d)
            player.world.spawnParticle(org.bukkit.Particle.WHITE_SMOKE, player.location, 2, 0.05d, 0.05d, 0.05d, 0.01d)

            def eyeLoc = player.eyeLocation.add(dir.clone().multiply(0.8))
            if (eyeLoc.block.type.isSolid) {
                player.sendMessage(ColorTranslator.translate(
                    PumpkingServiceManager.messages.getStrictString(player, "asesinos.herobrine.habilidades.estampaste", "killers_info")
                ))
                for(int i=0; i<3; i++) plugin.combatManager.takeDamage(player)
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                task.cancel()
                return
            }

            player.getNearbyEntities(1.5, 1.5, 1.5).findAll { it instanceof Player }.forEach { victim ->
                if (isValidTarget(player, (Player) victim) && !hitted.contains(victim.uniqueId)) {
                    hitted.add(victim.uniqueId)
                    for(int i=0; i<3; i++) plugin.combatManager.takeDamage(player)
                    victim.playSound(victim.location, Sound.ENTITY_WITHER_BREAK_BLOCK, 1f, 0.8f)
                    victim.sendMessage(ColorTranslator.translate(
                        PumpkingServiceManager.messages.getStrictString(victim, "asesinos.herobrine.habilidades.embestido", "killers_info")
                    ))
                }
            }
            ticks++
        })
    }

    private void habilidadSaltoDimensional(Player player) {
        def gens = plugin.generatorManager.getGeneratorLocations().findAll { it.world == player.world }
        if (gens.isEmpty()) return
        ParticleShapesUtils.drawVortex(player.location, org.bukkit.Particle.REVERSE_PORTAL)
        player.playSound(player.location, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 0.5f)
        def target = gens[ThreadLocalRandom.current().nextInt(gens.size())].clone().add(0.5, 1.1, 0.5)

        player.teleportAsync(target).thenAccept { _ ->
            ParticleShapesUtils.drawWings(player, org.bukkit.Particle.DRAGON_BREATH)
            player.playSound(player.location, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.8f)
        }
    }

    private void habilidadEstrellaWither(Player player) {
        def skull = player.launchProjectile(WitherSkull.class)
        skull.yield = 0f
        int life = 0
        runTimer(skull, 1L, 1L, { task ->
            if (life >= 60 || !skull.isValid()) {
                if (skull.isValid()) skull.remove()
                task.cancel()
                return
            }
            skull.world.spawnParticle(org.bukkit.Particle.WITCH, skull.location, 3, 0.05d, 0.05d, 0.05d, 0.01d)

            def hit = player.world.getNearbyPlayers(skull.location, 1.2).find { isValidTarget(player, it) }
            if (hit != null) {
                plugin.combatManager.takeDamage(hit)
                hit.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                skull.remove()
                task.cancel()
            }
            life++
        })
    }

    private void habilidadErrorMundo(Player player) {
        def teamName = "hb_glow"
        def teamInfo = new ScoreBoardTeamInfo(
            Component.text("HB_Team"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.DARK_PURPLE, WrapperPlayServerTeams.OptionData.NONE
        )

        def targets = plugin.server.onlinePlayers.findAll { isValidTarget(player, it) && it.world == player.world }
        if (targets.isEmpty()) return

        def targetNames = targets.collect { it.name }
        def createTeam = new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, targetNames)
        PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)

        targets.forEach { online ->
            def metadata = Arrays.asList(new EntityData(0, EntityDataTypes.BYTE, (byte)0x40))
            PacketEvents.getAPI().playerManager.sendPacket(player, new WrapperPlayServerEntityMetadata(online.entityId, metadata))
            online.playSound(online.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f)
            online.world.spawnParticle(org.bukkit.Particle.ENCHANTED_HIT, online.location.add(0.0, 1.0, 0.0), 20, 0.5d, 0.5d, 0.5d, 0.1d)
        }

        runDelayed(player, 200L, { _ ->
            if (player.isOnline) {
                def removeTeam = new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        })
    }

    @Override
    void showPhysicalTrail(Player player) {
        def uuid = player.uniqueId
        if (!plugin.asesinoManager.isKiller(player)) { limpiarVisuales(uuid); return }
        
        VirtualBlockDisplay existing = blockOrbiters.get(uuid)
        if (existing != null && existing.world != player.world) limpiarVisuales(uuid)

        if (!blockOrbiters.containsKey(uuid)) {
            def bMain = PacketFactory.displays.buildBlockDisplay(ViewerScopeKt.sessionViewers(player), player.location, { bd ->
                bd.block = Material.NETHERRACK.createBlockData()
                bd.transformation = new Transformation(new org.joml.Vector3f(-0.15f, -0.15f, -0.15f), new Quaternionf(), new org.joml.Vector3f(0.3f, 0.3f, 0.3f), new Quaternionf())
                bd.teleportDuration = 3
                bd.interpolationDuration = 3
            })
            blockOrbiters.put(uuid, bMain)

            def extras = new ArrayList<VirtualDisplay>()
            extras.add(PacketFactory.displays.buildItemDisplay(ViewerScopeKt.sessionViewers(player), player.location, { id ->
                id.setItemStack(new ItemStack(Material.NETHER_STAR))
                id.transformation = new Transformation(new org.joml.Vector3f(), new Quaternionf(), new org.joml.Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf())
                id.teleportDuration = 3
                id.interpolationDuration = 3
            }))
            extras.add(PacketFactory.displays.buildBlockDisplay(ViewerScopeKt.sessionViewers(player), player.location, { bd ->
                bd.block = Material.GOLD_BLOCK.createBlockData()
                bd.transformation = new Transformation(new org.joml.Vector3f(-0.15f, -0.15f, -0.15f), new Quaternionf(), new org.joml.Vector3f(0.3f, 0.3f, 0.3f), new Quaternionf())
                bd.teleportDuration = 3
                bd.interpolationDuration = 3
            }))
            itemOrbiters.put(uuid, extras)
        }

        double anguloBase = ((angulos.getOrDefault(uuid, 0.0) as Double) + 0.12) % (Math.PI * 2)
        double radio = 1.4
        def pLoc = player.location
        def bMain = blockOrbiters.get(uuid)
        def extras = itemOrbiters.get(uuid)

        def loc1 = pLoc.clone().add(radio * Math.cos(anguloBase), 1.2 + (0.2 * Math.sin(anguloBase * 2)), radio * Math.sin(anguloBase))
        loc1.yaw = (anguloBase * 100).toFloat() % 360
        bMain.teleport(loc1)

        double angle2 = anguloBase + 2.09
        def loc2 = pLoc.clone().add(radio * Math.cos(angle2), 1.0 + (0.2 * Math.cos(anguloBase * 2)), radio * Math.sin(angle2))
        loc2.yaw = (angle2 * 80).toFloat() % 360
        extras[0].teleport(loc2)

        double angle3 = anguloBase + 4.18
        def loc3 = pLoc.clone().add(radio * Math.cos(angle3), 0.8 + (0.2 * Math.sin(anguloBase)), radio * Math.sin(angle3))
        loc3.yaw = (angle3 * 100).toFloat() % 360
        extras[1].teleport(loc3)

        angulos.put(uuid, anguloBase)
        ParticleShapesUtils.drawWings(player, org.bukkit.Particle.SOUL_FIRE_FLAME)
    }

    @Override
    void showTrail(Player player) {
        def l = player.location.add(0.0, 1.2, 0.0)
        def viewers = player.world.players.findAll { it.location.distanceSquared(l) < 400.0 }
        PacketFactory.particles.sendParticle(viewers, l, ParticleTypes.CLOUD, 1, 0.12f, 0.12f, 0.12f, 0.01f)
    }

    private void limpiarVisuales(UUID uuid) {
        def b = blockOrbiters.remove(uuid)
        if (b != null) b.remove()
        
        def items = itemOrbiters.remove(uuid)
        if (items != null) items.forEach { it.remove() }
        
        angulos.remove(uuid)
    }

    @Override
    void clearGlobalData() {
        super.clearGlobalData()
        blockOrbiters.values().forEach { it.remove() }
        itemOrbiters.values().forEach { items -> items.forEach { it.remove() } }
        blockOrbiters.clear()
        itemOrbiters.clear()
        angulos.clear()
    }

    @Override
    void cleanup(Player player) {
        super.cleanup(player)
        if (player != null) {
            limpiarVisuales(player.uniqueId)
            lastKillEffect.remove(player.uniqueId)
        }
    }
}

new HerobrineScript()
