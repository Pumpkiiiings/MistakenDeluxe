package liric.mistaken.roles.killers.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.roles.killers.Killer
import liric.mistaken.roles.killers.CoreKiller
import liric.mistaken.utils.hooks.CraftEngine
import org.bukkit.*
import org.bukkit.attribute.Attribute
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

/**
 * [LIRIC-MISTAKEN 2.0]
 * NullAsesino: El Ente del Glitch.
 * MEJORAS: Escala 1.1, Fï¿½sicas mejoradas y Finishers del Abismo agregados.
 */
class NullAsesino : CoreKiller(
    "null",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "asesinos.null.nombre", "killers_info")
), Listener { // ?? Agregado Listener para Finishers

    private val pathBase = "asesinos.null"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTraps = ConcurrentHashMap.newKeySet<Entity>()

    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val orbitMaterials = listOf(Material.BEACON, Material.ENDER_EYE, Material.NETHER_STAR)

    // Anti-spam para los efectos de muerte
    private val lastKillEffect = ConcurrentHashMap<UUID, Long>()

    init {
        preLoadKit()
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getKillerConfig(this.id)
        val armorMap = mapOf(
            "helmet" to Material.NETHERITE_HELMET,
            "chestplate" to Material.NETHERITE_CHESTPLATE,
            "leggings" to Material.NETHERITE_LEGGINGS,
            "boots" to Material.NETHERITE_BOOTS
        )

        armorMap.forEach { (key, fallbackMat) ->
            val id = config.getString("armor.$key")
            if (id != null && id != "none") {
                val item = CraftEngine.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: fallbackMat)
            }
        }

        listOf("weapon", "skill1", "skill2", "skill3", "skill4").forEach { key ->
            val id = config.getString("items.$key")
            if (id != null && id != "none") {
                val item = CraftEngine.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
            }
        }
    }

    override fun useSkill(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadErrorRender(player); playSkillEffects(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadGeneradorBait(player); playSkillEffects(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadPrisionVacio(player); playSkillEffects(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadColmillosVacio(player); playSkillEffects(player, 4) }
        }
    }

    // --- ?? FINISHERS: EFECTOS DE ASESINATO ALEATORIOS DE NULL ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onNullKill(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val session = plugin.sessionManager.getSession(attacker) ?: return
        if (session.isKiller(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
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
                // EFECTO 1: DRENAJE DE ALMA
                world.playSound(loc, Sound.ENTITY_ENDERMAN_STARE, 1.5f, 0.1f)

                var ticks = 0
                plugin.server.regionScheduler.runAtFixedRate(plugin, loc, Consumer { task ->
                    if (ticks >= 40) { task.cancel(); return@Consumer }

                    val angle = ticks * 0.5
                    val radius = 2.0 - (ticks * 0.05)
                    val x = radius * cos(angle)
                    val z = radius * sin(angle)
                    val y = ticks * 0.1

                    world.spawnParticle(org.bukkit.Particle.SQUID_INK, loc.clone().add(x, y, z), 5, 0.1, 0.1, 0.1, 0.0)
                    world.spawnParticle(org.bukkit.Particle.SCULK_SOUL, loc.clone().add(-x, y, -z), 2, 0.1, 0.1, 0.1, 0.0)

                    ticks++
                }, 1L, 1L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.5f)
                    world.spawnParticle(org.bukkit.Particle.SONIC_BOOM, loc.clone().add(0.0, 1.0, 0.0), 1)
                }, 40L)
            }
            1 -> {
                // EFECTO 2: PRISIï¿½N DE OBSIDIANA LLOROSA
                world.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.5f, 0.5f)

                val prison = world.spawn(loc.clone().add(0.0, 2.0, 0.0), BlockDisplay::class.java) {
                    it.block = Material.CRYING_OBSIDIAN.createBlockData()
                    it.transformation = Transformation(JomlVector3f(-1.5f, -1.5f, -1.5f), Quaternionf(), JomlVector3f(3f, 3f, 3f), Quaternionf())
                    it.teleportDuration = 20 // Se hunde
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    prison.teleport(loc.clone().subtract(0.0, 2.0, 0.0))
                    world.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 1.5, 0.5, 1.5, 0.1)
                }, 5L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 2f, 0.1f)
                    prison.remove()
                }, 30L)
            }
            2 -> {
                // EFECTO 3: MIRADA DEL VACï¿½O
                world.playSound(loc, Sound.AMBIENT_CAVE, 2f, 0.5f)

                val displays = mutableListOf<BlockDisplay>()
                for (i in 0..2) {
                    val angle = (i * Math.PI * 2) / 3
                    val x = 2.0 * cos(angle)
                    val z = 2.0 * sin(angle)

                    displays.add(world.spawn(loc.clone().add(x, 2.0, z), BlockDisplay::class.java) {
                        it.block = Material.BEACON.createBlockData()
                        it.transformation = Transformation(JomlVector3f(-0.5f, -0.5f, -0.5f), Quaternionf(), JomlVector3f(1f, 1f, 1f), Quaternionf())
                    })
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 1f, 1f)
                    world.spawnParticle(org.bukkit.Particle.SMOKE, loc, 100, 2.0, 2.0, 2.0, 0.1)
                    displays.forEach { it.remove() }
                }, 30L)
            }
        }
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        // ?? ESCALA AUMENTADA
        player.getAttribute(Attribute.SCALE)?.baseValue = 1.1

        val langInfo = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "killers_info")
        val configMecanica = plugin.configManager.getKillerConfig(this.id)

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = configMecanica.getString("armor.$key") ?:
            configMecanica.getString("items.$key")

            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "weapon") "asesinos.${this.id}.skill_names.weapon"
            else "asesinos.${this.id}.skill_names.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
            }

            if (isArmor) {
                when(key) {
                    "helmet" -> inv.helmet = item
                    "chestplate" -> inv.chestplate = item
                    "leggings" -> inv.leggings = item
                    "boots" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        deliver("helmet", 0, true); deliver("chestplate", 0, true)
        deliver("leggings", 0, true); deliver("boots", 0, true)
        deliver("skill1", 1); deliver("skill2", 2)
        deliver("skill3", 3); deliver("skill4", 4); deliver("weapon", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    private fun habilidadErrorRender(player: Player) {
        player.world.playSound(player.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
        player.world.spawnParticle(org.bukkit.Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 3, 0.5, 0.5, 0.5, 0.0)

        player.world.getNearbyPlayers(player.location, 12.0).forEach { victim ->
            // ?? Uso de la funciï¿½n centralizada
            if (isValidTarget(player, victim)) {
                victim.apply {
                    addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 200, 0))
                    addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
                    sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(null, "roles.killer.abilities.null_asesino.sistema_corrupto"))
                }
            }
        }
    }

    private fun habilidadGeneradorBait(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.1, 0.5)
        val bait = loc.world.spawn(loc, ArmorStand::class.java) { asEntity ->
            asEntity.isInvisible = true
            asEntity.setGravity(false)
            asEntity.isMarker = true
            asEntity.equipment.helmet = ItemStack(Material.BEACON)
        }
        activeTraps.add(bait)

        var timer = 0
        bait.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (timer >= 400 || !bait.isValid) {
                cleanupTrap(bait)
                task.cancel()
                return@Consumer
            }

            val angle = timer * 0.4
            val x = cos(angle) * 0.7
            val z = sin(angle) * 0.7
            loc.world.spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(x, 1.0, z), 1, 0.0, 0.0, 0.0, 0.0)

            // ?? Uso de la funciï¿½n centralizada
            val victim = loc.world.getNearbyPlayers(loc, 3.5).firstOrNull { isValidTarget(player, it) }

            if (victim != null) {
                plugin.combatManager.takeDamage(victim)
                victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.1f)
                cleanupTrap(bait)
                task.cancel()
            }
            timer++
        }, null, 1L, 2L) // 100ms = 2 ticks
    }

    private fun habilidadPrisionVacio(player: Player) {
        // ?? Uso de la funciï¿½n centralizada
        val ray = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 15.0) {
            it is Player && isValidTarget(player, it)
        }
        val victim = ray?.hitEntity as? Player ?: return
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 10))
        player.playSound(victim.location, Sound.BLOCK_CHAIN_PLACE, 1f, 0.5f)
    }

    private fun habilidadColmillosVacio(player: Player) {
        val startLoc = player.location
        val direction = startLoc.direction.setY(0.0).normalize()

        val currentLoc = startLoc.clone()
        for (i in 0 until 15) {
            currentLoc.add(direction)
            val locToSpawn = currentLoc.clone()
            plugin.server.regionScheduler.runDelayed(plugin, locToSpawn, Consumer { _ ->
                if (!locToSpawn.block.type.isSolid) {
                    locToSpawn.world.spawn(locToSpawn, EvokerFangs::class.java)
                    locToSpawn.world.getNearbyPlayers(locToSpawn, 1.5).forEach { victim ->
                        // ?? Uso de la funciï¿½n centralizada
                        if (isValidTarget(player, victim)) {
                            plugin.combatManager.takeDamage(victim)
                            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                        }
                    }
                }
            }, (i + 1).toLong()) // 50ms = 1 tick de diferencia
        }
    }

    override fun showPhysicalTrail(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.isKiller(player)) { limpiarVisuales(uuid); return }

        val playerWorld = player.world
        if (orbitadores[uuid]?.firstOrNull()?.world != playerWorld) limpiarVisuales(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<ItemDisplay>().apply {
                orbitMaterials.forEach { mat ->
                    add(crearItemOrbitante(player.location, mat))
                }
            }
        }

        val anguloActual = angulos.getOrDefault(uuid, 0.0)

        val radio = 1.5
        val step = (2 * Math.PI) / entidades.size
        val playerLoc = player.location

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val currentAngle = anguloActual + (step * i)
                val x = radio * cos(currentAngle)
                val z = radio * sin(currentAngle)
                val y = 1.3 + (0.2 * sin(currentAngle * 1.5))

                val targetLoc = playerLoc.clone().add(x, y, z)
                targetLoc.yaw = (currentAngle * 120).toFloat() % 360
                targetLoc.pitch = (currentAngle * 60).toFloat() % 360

                display.teleport(targetLoc)
            }
        }
        angulos[uuid] = anguloActual + 0.12
    }

    private fun crearItemOrbitante(loc: Location, mat: Material): ItemDisplay {
        return liric.mistaken.packet.PacketFactory.displays.buildItemDisplay(org.bukkit.Bukkit.getOnlinePlayers().toList(), loc) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(
                JomlVector3f(0f, 0f, 0f),
                Quaternionf(),
                JomlVector3f(0.5f, 0.5f, 0.5f),
                Quaternionf()
            )
            id.teleportDuration = 3
            id.interpolationDuration = 3
        }
    }

    override fun showTrail(player: Player) {
        val loc = player.location.add(0.0, 1.2, 0.0)
        val pos = Vector3d(loc.x, loc.y, loc.z)
        val mgr = PacketEvents.getAPI().playerManager
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.WITCH), false, pos, Vector3f(0.2f, 0.2f, 0.2f), 0.02f, 1)
        loc.world.players.forEach { if (it != player && it.location.distanceSquared(loc) < 625.0) mgr.sendPacket(it, packet) }
    }

    private fun cleanupTrap(trap: Entity) { trap.remove(); activeTraps.remove(trap) }
    private fun limpiarVisuales(uuid: UUID) { orbitadores.remove(uuid)?.forEach { it.remove() }; angulos.remove(uuid) }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarVisuales(it.uniqueId)
            it.getAttribute(Attribute.SCALE)?.baseValue = 1.0
            lastKillEffect.remove(it.uniqueId)
        }
        activeTraps.forEach { it.remove() }
        activeTraps.clear()
    }
}








