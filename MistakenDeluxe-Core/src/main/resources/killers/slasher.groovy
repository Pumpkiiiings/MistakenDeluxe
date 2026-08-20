import com.github.retrooper.packetevents.PacketEvents
import liric.mistaken.roles.killers.CoreKiller
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualItemDisplay
import liric.mistaken.packet.fake.VirtualDisplay
import liric.mistaken.utils.misc.HitboxVisualizer
import liric.mistaken.utils.ViewerScopeKt
import pumpking.lib.service.PumpkingServiceManager

class SlasherScript extends CoreKiller {

    private final Map attackSoundsQueue = new ConcurrentHashMap()

    SlasherScript() {
        super("slasher", PumpkingServiceManager.messages.getStrictString(null, "asesinos.slasher.nombre", "killers_info"))
        
        onEvent(EntityDamageByEntityEvent.class, org.bukkit.event.EventPriority.MONITOR, true, { event ->
            onSlasherAttack(event)
        })
    }

    @Override
    void useSkill(Player player, int slot) {
        if (checkCooldown(player, slot)) return
        switch (slot) {
            case 1: habilidadSedDeSangre(player); break
            case 2: habilidadMacheteLanzable(player); break
            case 3: habilidadPresencia(player); break
            case 4: habilidadEjecucion(player); break
        }
        playSkillEffects(player, slot)
    }

    void onSlasherAttack(EntityDamageByEntityEvent event) {
        if (!(event.damager instanceof Player)) return
        if (!(event.entity instanceof Player)) return
        Player attacker = (Player) event.damager
        Player victim = (Player) event.entity

        def session = plugin.sessionManager.getSession(attacker)
        if (session == null) return
        
        if (session.isKiller(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
            if (isValidTarget(attacker, victim)) {
                def uuid = attacker.uniqueId
                def queue = attackSoundsQueue.get(uuid)
                if (queue == null || queue.isEmpty()) {
                    queue = [1, 2, 3, 4]
                    Collections.shuffle(queue)
                    attackSoundsQueue.put(uuid, queue)
                }
                int soundIndex = queue.remove(0)
                String soundName = "mistaken:whitepumpkin_ataque_" + soundIndex
                attacker.world.playSound(attacker.location, soundName, SoundCategory.PLAYERS, 3.0f, 1.0f)
            }
        }
    }

    private void habilidadSedDeSangre(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2))
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 1))
        dibujarEstrella(player, Color.RED, 1.5, 5)
        
        // Híbrido
        player.playSound(player.location, Sound.ENTITY_WOLF_GROWL, 1.5f, 0.5f)
        liric.mistaken.utils.hooks.ObserverHook.playScreenTint(player, 255, 0, 0, 0.3f, 160) // Borde rojo frenesí

        runDelayed(player, 160L, { _ ->
            if (player.isOnline && plugin.asesinoManager.isKiller(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
            }
        })
    }

    private void habilidadMacheteLanzable(Player player) {
        def config = plugin.configManager.getKillerConfig(this.id)
        String weaponId = config.getString("items.weapon")
        ItemStack macheteItem = null
        if (weaponId != null && weaponId != "none") {
            macheteItem = liric.mistaken.utils.hooks.CraftEngine.getCustomItem(weaponId)
            if (macheteItem == null) macheteItem = new ItemStack(Material.matchMaterial(weaponId) ?: Material.IRON_SWORD)
        }
        if (macheteItem == null) macheteItem = new ItemStack(Material.IRON_SWORD)
        macheteItem = macheteItem.clone()
        
        def spawnLoc = player.eyeLocation.clone()

        def machete = PacketFactory.displays.buildItemDisplay(ViewerScopeKt.sessionViewers(player), spawnLoc, { id ->
            id.setItemStack(macheteItem)
            id.transformation = new Transformation(new org.joml.Vector3f(), new Quaternionf().rotateX((float)Math.toRadians(90.0)), new org.joml.Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf())
            id.interpolationDuration = 1
            id.teleportDuration = 1
        })

        trackResource({ 
            if (machete.isValid()) machete.remove() 
        })
        def direction = player.location.direction.multiply(1.4)

        // HITBOX: Proyectil
        def hitbox = HitboxVisualizer.createHitbox(spawnLoc, 1.2, 1.2, 1.2, Material.ORANGE_STAINED_GLASS)

        int ticks = 0
        runTimer(player, 1L, 1L, { task ->
            if (ticks >= 30 || !machete.isValid()) {
                if (machete.isValid()) machete.remove()
                if (hitbox != null) hitbox.remove()
                task.cancel()
                return
            }

            machete.teleport(machete.location.add(direction))
            if (hitbox != null) hitbox.teleport(machete.location)
            
            // Rastro de sangre
            machete.world.spawnParticle(org.bukkit.Particle.DUST, machete.location, 3, new org.bukkit.Particle.DustOptions(Color.RED, 1.0f))

            def hit = machete.getNearbyEntities(1.2, 1.2, 1.2).findAll { it instanceof Player }.find { isValidTarget(player, (Player) it) }

            if (hit != null || machete.location.block.type.isSolid) {
                machete.world.spawnParticle(org.bukkit.Particle.BLOCK, machete.location, 50, 0.5, 0.5, 0.5, Material.REDSTONE_BLOCK.createBlockData())
                
                if (hit != null) {
                    plugin.combatManager.takeDamage((Player)hit)
                    hit.playSound(hit.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.8f)
                    if (hitbox != null) hitbox.block = Material.RED_STAINED_GLASS.createBlockData()
                    
                    liric.mistaken.utils.hooks.ObserverHook.playScreenTint((Player)hit, 255, 0, 0, 0.6f, 20)
                    liric.mistaken.utils.hooks.ObserverHook.playScreenshake((Player)hit, 1.5f, 15)
                }
                machete.remove()

                runDelayed(player, 2L, { _ -> if (hitbox != null) hitbox.remove() })
                task.cancel()
            }
            ticks++
        })
    }

    private void habilidadPresencia(Player player) {
        player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.8f)
        player.world.spawnParticle(org.bukkit.Particle.SCULK_SOUL, player.location, 50, 3.0, 1.0, 3.0, 0.05)

        // HITBOX: Grito en área
        HitboxVisualizer.drawInstantHitbox(plugin, player.location, 8.0, 8.0, 8.0, 20L, Material.PURPLE_STAINED_GLASS)

        player.getNearbyEntities(8.0, 8.0, 8.0).findAll { it instanceof Player }.forEach { victim ->
            if (isValidTarget(player, (Player)victim)) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                victim.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1))
                
                liric.mistaken.utils.hooks.ObserverHook.playScreenTint((Player)victim, 0, 0, 0, 0.7f, 15)
            }
        }
    }

    private void habilidadEjecucion(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 3))
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 2))
        dibujarEstrella(player, Color.MAROON, 2.5, 5)
        
        player.world.spawnParticle(org.bukkit.Particle.ASH, player.location, 300, 3.0, 3.0, 3.0, 0.05)
        player.world.spawnParticle(org.bukkit.Particle.FALLING_LAVA, player.location, 50, 3.0, 3.0, 3.0, 0.05)
        liric.mistaken.utils.hooks.ObserverHook.playScreenshake(player, 0.8f, 300)

        runDelayed(player, 300L, { _ ->
            if (player.isOnline && plugin.asesinoManager.isKiller(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
            }
        })
    }

    @Override
    void showTrail(Player player) {
        if (player.velocity.lengthSquared() < 0.001) return
        def viewers = player.world.players.findAll { it.location.distanceSquared(player.location) < 625.0 }
        PacketFactory.particles.sendDustParticle(viewers, player.location.clone().add(0.0, 1.2, 0.0), Color.RED, 0.8f, 1, 0.1f, 0.2f, 0.1f)
    }

    @Override
    void showPhysicalTrail(Player player) {}

    private void dibujarEstrella(Player player, Color color, double radio, int puntas) {
        def loc = player.location.add(0.0, 0.1, 0.0)
        def dust = new org.bukkit.Particle.DustOptions(color, 1.0f)
        for (int i = 0; i < puntas; i++) {
            double a = i * Math.PI * 2 / puntas
            double na = (i + 2) * Math.PI * 2 / puntas
            def p1 = loc.clone().add(Math.cos(a) * radio, 0.0, Math.sin(a) * radio)
            def p2 = loc.clone().add(Math.cos(na) * radio, 0.0, Math.sin(na) * radio)
            def dir = p2.toVector().subtract(p1.toVector())
            double len = dir.length()
            dir.normalize()
            
            runRegionDelayed(loc, 1L, { _ ->
                double d = 0.0
                while (d < len) {
                    player.world.spawnParticle(org.bukkit.Particle.DUST, p1.clone().add(dir.clone().multiply(d)), 1, dust)
                    d += 0.3
                }
            })
        }
    }

    @Override
    void cleanup(Player player) {
        super.cleanup(player)
        if (player != null) attackSoundsQueue.remove(player.uniqueId)
    }
}

new SlasherScript()
