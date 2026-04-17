package liric.mistaken.roles.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.roles.asesinos.Asesino
import liric.mistaken.utils.hooks.CraftEngine
import org.bukkit.*
import org.bukkit.Particle.DustOptions
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
 *[LIRIC-MISTAKEN 2.0]
 * Entity 303: El Hacker de la Realidad.
 * FIX: Animación Ultra-Fluida, Escala 1.1 y Finishers (Efectos de muerte) añadidos.
 */
class Entity303 : Asesino(
    "entity303",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.entity303.nombre", "<red><b>ENTITY 303</b>", "asesinos_info")
), Listener { // 🔥 Agregado Listener para Finishers

    private val path = "asesinos.entity303"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()

    // Anti-spam para los efectos de muerte
    private val lastKillEffect = ConcurrentHashMap<UUID, Long>()

    private val orbitMaterials = listOf(
        Material.REDSTONE_BLOCK, Material.MAGMA_BLOCK,
        Material.OBSERVER, Material.NETHER_WART_BLOCK,
        Material.CRIMSON_HYPHAE
    )

    init {
        preLoadKit()
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$path.armadura.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                    itemKitCache[k] = item
                }
            }
        }

        items.forEach { k ->
            config.getString("$path.items.$k")?.let { id ->
                if (id != "none") {
                    val item = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                    itemKitCache[k] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadDashCodigo(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadInfeccionSistema(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadProtocoloVuelo(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadCrashPantalla(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    // --- 💀 FINISHERS: EFECTOS DE ASESINATO ALEATORIOS DE 303 ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntity303Kill(event: EntityDamageByEntityEvent) {
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
                // EFECTO 1: DELETE(ENTITY)
                world.playSound(loc, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.5f, 0.5f)

                var ticks = 0
                plugin.server.regionScheduler.runAtFixedRate(plugin, loc, Consumer { task ->
                    if (ticks >= 30) { task.cancel(); return@Consumer }

                    world.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.0, 0.0), 10, 0.5, 1.0, 0.5, DustOptions(Color.RED, 1f))
                    world.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.0, 0.0), 10, 0.5, 1.0, 0.5, DustOptions(Color.AQUA, 1f))
                    ticks++
                }, 1L, 1L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 2f)
                    world.spawnParticle(org.bukkit.Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 5, 0.0, 0.0, 0.0, 0.0)
                    world.spawnParticle(org.bukkit.Particle.WHITE_ASH, loc.clone().add(0.0, 1.0, 0.0), 100, 1.0, 1.0, 1.0, 0.5)
                }, 30L)
            }
            1 -> {
                // EFECTO 2: CRASH.DUMP (BARRERA VISUAL)
                world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 2f, 0.1f)
                world.spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 1)

                val barrier = world.spawn(loc.clone().add(0.0, 1.0, 0.0), ItemDisplay::class.java) {
                    it.setItemStack(ItemStack(Material.BARRIER))
                    it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(2f, 2f, 2f), Quaternionf())
                    it.isGlowing = true
                    it.glowColorOverride = Color.RED
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
                    world.spawnParticle(org.bukkit.Particle.BLOCK, loc, 50, 0.5, 0.5, 0.5, Material.REDSTONE_BLOCK.createBlockData())
                    barrier.remove()
                }, 40L)
            }
            2 -> {
                // EFECTO 3: FORCE EXIT (TNT GIRATORIA)
                world.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1f, 1f)
                val tnt = world.spawn(loc.clone().add(0.0, 0.5, 0.0), BlockDisplay::class.java) {
                    it.block = Material.TNT.createBlockData()
                    it.transformation = Transformation(JomlVector3f(-0.5f, -0.5f, -0.5f), Quaternionf(), JomlVector3f(1f, 1f, 1f), Quaternionf())
                    it.teleportDuration = 20
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    tnt.teleport(loc.clone().add(0.0, 3.0, 0.0))
                    val t = tnt.transformation
                    t.leftRotation.rotateY(5f).rotateX(2f)
                    tnt.transformation = t
                }, 1L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f)
                    world.spawnParticle(org.bukkit.Particle.EXPLOSION, loc.clone().add(0.0, 3.0, 0.0), 2)
                    tnt.remove()
                }, 21L)
            }
        }
    }

    // --- HABILIDADES ---

    private fun habilidadDashCodigo(player: Player) {
        val dir = player.location.direction.normalize().multiply(2.0).setY(0.2)
        player.velocity = dir
        player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 1f, 1.5f)

        var count = 0
        val hitted = mutableSetOf<UUID>()

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (count >= 10 || !player.isOnline) {
                task.cancel()
                return@Consumer
            }
            player.world.getNearbyPlayers(player.location, 2.0).forEach { victim ->
                // 🔥 Uso de la función centralizada
                if (esObjetivoValido(player, victim) && victim.uniqueId !in hitted) {
                    hitted.add(victim.uniqueId)
                    plugin.combatManager.takeDamage(victim)
                    victim.velocity = player.location.direction.normalize().multiply(1.5).setY(0.4)
                    victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                }
            }
            count++
        }, null, 1L, 1L)
    }

    private fun habilidadInfeccionSistema(player: Player) {
        val star = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.NETHER_STAR))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
        }
        val direction = player.location.direction.multiply(1.5)

        var ticks = 0
        star.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 40 || !star.isValid) {
                if (star.isValid) star.remove()
                task.cancel()
                return@Consumer
            }

            star.teleport(star.location.add(direction))
            star.world.spawnParticle(org.bukkit.Particle.ENCHANT, star.location, 5, 0.1, 0.1, 0.1, 0.05)

            // 🔥 Uso de la función centralizada
            val hit = star.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull { esObjetivoValido(player, it) }

            if (hit != null || star.location.block.type.isSolid) {
                star.world.spawnParticle(org.bukkit.Particle.EXPLOSION, star.location, 1)
                hit?.let {
                    plugin.combatManager.takeDamage(it)
                    it.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 2))
                    it.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 100, 1))
                }
                star.remove()
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

    private fun habilidadProtocoloVuelo(player: Player) {
        player.allowFlight = true
        player.isFlying = true
        player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)

        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline && player.gameMode != GameMode.SPECTATOR) {
                player.isFlying = false
                player.allowFlight = false
                player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 0))
                player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f)
            }
        }, null, 100L)
    }

    private fun habilidadCrashPantalla(player: Player) {
        plugin.server.onlinePlayers.forEach { online ->
            // 🔥 Uso de la función centralizada
            if (esObjetivoValido(player, online) && online.location.distanceSquared(player.location) < 1600) {
                online.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                online.playSound(online.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.1f)
                online.sendMessage(mm.deserialize("<red><bold>SYSTEM ERROR: 303 FOUND"))
            }
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        player.getAttribute(Attribute.SCALE)?.baseValue = 1.1

        val configMecanica = plugin.configManager.getAsesinos()
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$path.armadura.$key")
            else configMecanica.getString("$path.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat)
                else if (isArmor) {
                    val fallback = when(key) {
                        "casco" -> Material.NETHERITE_HELMET
                        "pechera" -> Material.NETHERITE_CHESTPLATE
                        "pantalones" -> Material.NETHERITE_LEGGINGS
                        else -> Material.NETHERITE_BOOTS
                    }
                    ItemStack(fallback)
                } else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.entity303.habilidades_nombres.arma"
            else "asesinos.entity303.habilidades_nombres.$key"

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
            } else {
                inv.setItem(slot, item)
            }
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
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiar(uuid); return }

        val playerWorld = player.world
        if (orbitadores[uuid]?.firstOrNull()?.world != playerWorld) limpiar(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            orbitMaterials.map { mat -> crearBloqueOrbitante(player.location, mat) }.toMutableList()
        }

        val anguloActual = (angulos.getOrDefault(uuid, 0.0) + 0.15) % (Math.PI * 2)
        val radio = 1.6
        val step = (2 * Math.PI) / entidades.size
        val playerLoc = player.location

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val currentAngle = anguloActual + (step * i)
                val x = radio * cos(currentAngle)
                val z = radio * sin(currentAngle)
                val y = 1.3 + (0.2 * sin(currentAngle * 2))

                val targetLoc = playerLoc.clone().add(x, y, z)
                targetLoc.yaw = (currentAngle * 100).toFloat() % 360
                targetLoc.pitch = (currentAngle * 50).toFloat() % 360

                display.teleport(targetLoc)
            }
        }
        angulos[uuid] = anguloActual
    }

    private fun crearBloqueOrbitante(loc: Location, mat: Material): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                JomlVector3f(-0.125f, -0.125f, -0.125f),
                Quaternionf(),
                JomlVector3f(0.25f, 0.25f, 0.25f),
                Quaternionf()
            )
            bd.teleportDuration = 3
            bd.interpolationDuration = 3
            if (mat == Material.MAGMA_BLOCK || mat == Material.REDSTONE_BLOCK) {
                bd.brightness = org.bukkit.entity.Display.Brightness(15, 15)
            }
        }
    }

    override fun mostrarTrail(player: Player) {
        val loc = player.location.add(0.0, 1.2, 0.0)
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.DUST, ParticleDustData(1f, 0f, 0f, 0.8f)),
            false,
            Vector3d(loc.x, loc.y, loc.z),
            Vector3f(0.2f, 0.2f, 0.2f),
            0.01f,
            1
        )
        loc.world.players.forEach { if (it.location.distanceSquared(loc) < 400.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet) }
    }

    private fun limpiar(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
        lastKillEffect.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiar(it.uniqueId)
            it.getAttribute(Attribute.SCALE)?.baseValue = 1.0
            it.allowFlight = false
            it.isFlying = false
        }
    }
}
