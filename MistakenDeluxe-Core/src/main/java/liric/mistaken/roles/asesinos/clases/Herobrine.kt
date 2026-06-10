package liric.mistaken.roles.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import liric.mistaken.Mistaken
import liric.mistaken.roles.asesinos.Asesino
import liric.mistaken.roles.asesinos.CoreAsesino
import liric.mistaken.utils.hooks.CraftEngine
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class Herobrine : CoreAsesino(
    "herobrine",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.herobrine.nombre", "<white><b>HEROBRINE</b>", "asesinos_info")
), Listener { // 🔥 Listener añadido para los Finishers

    private val pathBase = "asesinos.herobrine"
    private val blockOrbiters = ConcurrentHashMap<UUID, BlockDisplay>()
    private val itemOrbiters = ConcurrentHashMap<UUID, MutableList<Entity>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Anti-spam para los efectos de muerte
    private val lastKillEffect = ConcurrentHashMap<UUID, Long>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$pathBase.armadura.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.LEATHER_HELMET)
                }
            }
        }

        items.forEach { k ->
            config.getString("$pathBase.items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        when (slot) {
            1 -> habilidadDashVacio(player)
            2 -> habilidadSaltoDimensional(player)
            3 -> habilidadEstrellaWither(player)
            4 -> habilidadErrorMundo(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 💀 FINISHERS: EFECTOS DE ASESINATO ALEATORIOS DE HEROBRINE ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHerobrineKill(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val session = plugin.sessionManager.getSession(attacker) ?: return
        if (session.esAsesino(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
            if (victim.gameMode == GameMode.SPECTATOR) {
                val now = System.currentTimeMillis()
                if (now - lastKillEffect.getOrDefault(victim.uniqueId, 0L) > 2000L) {
                    lastKillEffect[victim.uniqueId] = now
                    triggerFinisherAleatorio(victim.location)
                }
            }
        }
    }

    private fun triggerFinisherAleatorio(loc: Location) {
        val effectType = ThreadLocalRandom.current().nextInt(3)
        val world = loc.world ?: return

        when (effectType) {
            0 -> {
                // EFECTO 1: LA CRUZ DE OBSIDIANA
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.5f)

                val cruz = world.spawn(loc.clone().add(0.0, 1.0, 0.0), BlockDisplay::class.java) {
                    it.block = Material.OBSIDIAN.createBlockData()
                    it.transformation = Transformation(JomlVector3f(-1.5f, -2.5f, -0.5f), Quaternionf(), JomlVector3f(3f, 5f, 1f), Quaternionf())
                }
                val brazoCruz = world.spawn(loc.clone().add(0.0, 2.5, 0.0), BlockDisplay::class.java) {
                    it.block = Material.OBSIDIAN.createBlockData()
                    it.transformation = Transformation(JomlVector3f(-2.5f, -0.5f, -0.5f), Quaternionf(), JomlVector3f(5f, 1f, 1f), Quaternionf())
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 0.1f)
                    world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, loc.clone().add(0.0, 2.0, 0.0), 100, 1.0, 3.0, 1.0, 0.1)
                }, 10L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    cruz.remove()
                    brazoCruz.remove()
                    // 🔥 FIX: BLOCK_CRACK -> BLOCK
                    world.spawnParticle(org.bukkit.Particle.BLOCK, loc, 150, 1.0, 2.0, 1.0, Material.OBSIDIAN.createBlockData())
                }, 30L)
            }
            1 -> {
                // EFECTO 2: ASCENSIÓN FALSA (RAYO BEACON + MURCIÉLAGOS)
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1f)

                val beacon = world.spawn(loc, BlockDisplay::class.java) {
                    it.block = Material.BEACON.createBlockData()
                    it.transformation = Transformation(JomlVector3f(-0.5f, 0f, -0.5f), Quaternionf(), JomlVector3f(1f, 10f, 1f), Quaternionf())
                    it.isGlowing = true
                }

                val pm = PacketEvents.getAPI().playerManager
                for(i in 1..5) {
                    val fakeId = ThreadLocalRandom.current().nextInt(500000, 600000)
                    val spawnPacket = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity(
                        fakeId, Optional.of(UUID.randomUUID()), com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.BAT,
                        Vector3d(loc.x, loc.y + 2.0, loc.z), loc.pitch, loc.yaw, loc.yaw, 0, Optional.empty()
                    )
                    world.players.forEach { pm.sendPacket(it, spawnPacket) }

                    plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                        world.players.forEach { pm.sendPacket(it, com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities(fakeId)) }
                    }, 25L)
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.5f)
                    world.spawnParticle(org.bukkit.Particle.CLOUD, loc, 100, 1.0, 3.0, 1.0, 0.1)
                    beacon.remove()
                }, 25L)
            }
            2 -> {
                // EFECTO 3: TEMPLO DEL VACÍO (MARCO DE PIEDRA Y ANTORCHAS)
                world.playSound(loc, Sound.BLOCK_STONE_PLACE, 1f, 0.1f)
                val altar = world.spawn(loc, BlockDisplay::class.java) {
                    it.block = Material.MOSSY_COBBLESTONE.createBlockData()
                    it.transformation = Transformation(JomlVector3f(-1.5f, -0.5f, -1.5f), Quaternionf(), JomlVector3f(3f, 1f, 3f), Quaternionf())
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.spawnParticle(org.bukkit.Particle.FLAME, loc.clone().add(1.5, 0.5, 1.5), 10, 0.0, 0.0, 0.0, 0.0)
                    world.spawnParticle(org.bukkit.Particle.FLAME, loc.clone().add(-1.5, 0.5, -1.5), 10, 0.0, 0.0, 0.0, 0.0)
                    world.playSound(loc, Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1f)
                }, 10L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    // 🔥 FIX: SMOKE_LARGE -> LARGE_SMOKE
                    world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, loc, 100, 1.5, 0.5, 1.5, 0.1)
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 0.5f)
                    altar.remove()
                }, 20L)
            }
        }
    }

    // --- HABILIDADES ---

    private fun habilidadDashVacio(player: Player) {
        val dir = player.location.direction.normalize()
        player.velocity = dir.clone().multiply(2.5).setY(0.2)
        player.world.spawnParticle(org.bukkit.Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 5, 0.2, 0.2, 0.2, 0.0)

        var ticks = 0
        val hitted = mutableSetOf<UUID>()

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 12 || !player.isOnline) {
                task.cancel()
                return@Consumer
            }
            player.world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, player.location, 3, 0.1, 0.1, 0.1, 0.02)
            player.world.spawnParticle(org.bukkit.Particle.WHITE_SMOKE, player.location, 2, 0.05, 0.05, 0.05, 0.01)

            val eyeLoc = player.eyeLocation.add(dir.clone().multiply(0.8))
            if (eyeLoc.block.type.isSolid) {
                player.sendMessage(mm.deserialize("<red><b>[!]</b> ¡Te estampaste contra el muro!"))
                repeat(3) { plugin.combatManager.takeDamage(player) }
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                task.cancel()
                return@Consumer
            }

            player.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>().forEach { victim ->
                if (esObjetivoValido(player, victim) && victim.uniqueId !in hitted) {
                    hitted.add(victim.uniqueId)
                    repeat(3) { plugin.combatManager.takeDamage(player) }
                    victim.playSound(victim.location, Sound.ENTITY_WITHER_BREAK_BLOCK, 1f, 0.8f)
                    victim.sendMessage(mm.deserialize("<red><b>[!]</b> Herobrine te ha embestido con el poder del Vacío."))
                }
            }
            ticks++
        }, null, 1L, 1L)
    }

    private fun habilidadSaltoDimensional(player: Player) {
        val gens = plugin.generatorManager.getGeneratorLocations()
        if (gens.isEmpty()) return
        player.world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, player.location.add(0.0, 1.0, 0.0), 30, 0.5, 1.0, 0.5, 0.1)
        player.playSound(player.location, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 0.5f)
        val target = gens.random().clone().add(0.5, 1.1, 0.5)

        player.teleportAsync(target).thenAccept {
            player.world.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, player.location.add(0.0, 1.0, 0.0), 25, 0.4, 0.8, 0.4, 0.05)
            player.playSound(player.location, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.8f)
        }
    }

    private fun habilidadEstrellaWither(player: Player) {
        val skull = player.launchProjectile(WitherSkull::class.java)
        skull.yield = 0f

        var life = 0
        skull.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (life >= 60 || !skull.isValid) {
                task.cancel()
                return@Consumer
            }
            skull.world.spawnParticle(org.bukkit.Particle.WITCH, skull.location, 3, 0.05, 0.05, 0.05, 0.01)

            val hit = player.world.getNearbyPlayers(skull.location, 1.2).firstOrNull { esObjetivoValido(player, it) }

            hit?.let {
                plugin.combatManager.takeDamage(it)
                it.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                skull.remove()
                task.cancel()
            }
            life++
        }, null, 1L, 1L)
    }

    private fun habilidadErrorMundo(player: Player) {
        val teamName = "hb_glow"
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("HB_Team"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.DARK_PURPLE, WrapperPlayServerTeams.OptionData.NONE
        )

        plugin.server.onlinePlayers.forEach { online ->
            if (!esObjetivoValido(player, online)) return@forEach

            val createTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, listOf(online.name))
            PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)
            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
            PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(online.entityId, metadata))
            online.playSound(online.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f)
            online.world.spawnParticle(org.bukkit.Particle.ENCHANTED_HIT, online.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.1)
        }

        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline) {
                val removeTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, null, 200L)
    }

    // --- EQUIPAMIENTO Y CARGA ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        if (itemKitCache.isEmpty()) preLoadKit()

        val configMecanica = plugin.configManager.getAsesinos()
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
            else configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.herobrine.habilidades_nombres.arma"
            else "asesinos.herobrine.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
            }

            if (isArmor) {
                when(key) {
                    "casco" -> inv.helmet = item
                    "pechera" -> inv.chestplate = item
                    "pantalones" -> inv.leggings = item
                    "botas" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        deliver("casco", 0, true); deliver("pechera", 0, true)
        deliver("pantalones", 0, true); deliver("botas", 0, true)
        deliver("habilidad1", 1); deliver("habilidad2", 2)
        deliver("habilidad3", 3); deliver("habilidad4", 4)
        deliver("arma", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }

        if (blockOrbiters[uuid]?.world != player.world) limpiarVisuales(uuid)

        if (!blockOrbiters.containsKey(uuid)) {
            val bMain = player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                bd.block = Material.NETHERRACK.createBlockData()
                bd.transformation = Transformation(JomlVector3f(-0.15f, -0.15f, -0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                bd.teleportDuration = 3
                bd.interpolationDuration = 3
            }
            blockOrbiters[uuid] = bMain

            val extras = mutableListOf<Entity>().apply {
                add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                    id.setItemStack(ItemStack(Material.NETHER_STAR))
                    id.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
                    id.teleportDuration = 3
                    id.interpolationDuration = 3
                })
                add(player.world.spawn(player.location, BlockDisplay::class.java) { bd ->
                    bd.block = Material.GOLD_BLOCK.createBlockData()
                    bd.transformation = Transformation(JomlVector3f(-0.15f), Quaternionf(), JomlVector3f(0.3f, 0.3f, 0.3f), Quaternionf())
                    bd.teleportDuration = 3
                    bd.interpolationDuration = 3
                })
            }
            itemOrbiters[uuid] = extras
        }

        val anguloBase = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val radio = 1.4
        val pLoc = player.location

        val bMain = blockOrbiters[uuid]!!
        val extras = itemOrbiters[uuid]!!

        val loc1 = pLoc.clone().add(radio * cos(anguloBase), 1.2 + (0.2 * sin(anguloBase * 2)), radio * sin(anguloBase))
        loc1.yaw = (anguloBase * 100).toFloat() % 360
        bMain.teleport(loc1)

        val angle2 = anguloBase + 2.09
        val loc2 = pLoc.clone().add(radio * cos(angle2), 1.0 + (0.2 * cos(anguloBase * 2)), radio * sin(angle2))
        loc2.yaw = (angle2 * 80).toFloat() % 360
        extras[0].teleport(loc2)

        val angle3 = anguloBase + 4.18
        val loc3 = pLoc.clone().add(radio * cos(angle3), 0.8 + (0.2 * sin(anguloBase)), radio * sin(angle3))
        loc3.yaw = (angle3 * 100).toFloat() % 360
        extras[1].teleport(loc3)

        angulos[uuid] = anguloBase
    }

    override fun mostrarTrail(player: Player) {
        val l = player.location.add(0.0, 1.2, 0.0)
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.CLOUD), false,
            Vector3d(l.x, l.y, l.z),
            Vector3f(0.12f, 0.12f, 0.12f),
            0.01f,
            1
        )
        player.world.players.forEach {
            if (it.location.distanceSquared(l) < 400.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet)
        }
    }

    private fun limpiarVisuales(uuid: UUID) {
        blockOrbiters.remove(uuid)?.remove()
        itemOrbiters.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarVisuales(it.uniqueId)
            lastKillEffect.remove(it.uniqueId)
        }
    }
}
