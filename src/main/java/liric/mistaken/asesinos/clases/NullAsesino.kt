package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class NullAsesino : Asesino(
    "null",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.null.nombre", "<dark_gray><b>NULL</b>", "asesinos_info")
) {

    private val pathBase = "asesinos.null"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTraps = ConcurrentHashMap.newKeySet<Entity>()

    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val orbitMaterials = listOf(Material.BEACON, Material.ENDER_EYE, Material.NETHER_STAR)

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorMap = mapOf(
            "casco" to Material.NETHERITE_HELMET,
            "pechera" to Material.NETHERITE_CHESTPLATE,
            "pantalones" to Material.NETHERITE_LEGGINGS,
            "botas" to Material.NETHERITE_BOOTS
        )

        armorMap.forEach { (key, fallbackMat) ->
            val id = config.getString("$pathBase.armadura.$key")
            if (id != null && id != "none") {
                val item = CraftEngineUtils.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: fallbackMat)
            }
        }

        listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4").forEach { key ->
            val id = config.getString("$pathBase.items.$key")
            if (id != null && id != "none") {
                val item = CraftEngineUtils.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadErrorRender(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadGeneradorBait(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadPrisionVacio(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadColmillosVacio(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        // 🔥 ESCALA AUMENTADA
        player.getAttribute(Attribute.SCALE)?.baseValue = 1.1

        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")
        val configMecanica = plugin.configManager.getAsesinos()

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = configMecanica.getString("$pathBase.armadura.$key") ?:
            configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val mat = Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase())
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.${this.id}.habilidades_nombres.arma"
            else "asesinos.${this.id}.habilidades_nombres.$key"

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
        deliver("habilidad3", 3); deliver("habilidad4", 4); deliver("arma", 8)
    }

    private fun habilidadErrorRender(player: Player) {
        player.world.playSound(player.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
        player.world.spawnParticle(org.bukkit.Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 3, 0.5, 0.5, 0.5, 0.0)

        player.world.getNearbyPlayers(player.location, 12.0).forEach { victim ->
            // 🔥 Uso de la función centralizada
            if (esObjetivoValido(player, victim)) {
                victim.apply {
                    addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 200, 0))
                    addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
                    sendMessage(mm.deserialize("<dark_gray><obfuscated>ERR</obfuscated> <white><b>SISTEMA CORRUPTO</b> <dark_gray><obfuscated>ERR</obfuscated>"))
                }
            }
        }
    }

    private fun habilidadGeneradorBait(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.1, 0.5)
        val bait = loc.world?.spawn(loc, ArmorStand::class.java) { asEntity ->
            asEntity.isVisible = false
            asEntity.setGravity(false)
            asEntity.isMarker = true
            asEntity.equipment.helmet = ItemStack(Material.BEACON)
        } ?: return
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

            // 🔥 Uso de la función centralizada
            val victim = loc.world.getNearbyPlayers(loc, 3.5).firstOrNull { esObjetivoValido(player, it) }

            if (victim != null) {
                plugin.gameManager.combatManager.takeDamage(victim)
                victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.1f)
                cleanupTrap(bait)
                task.cancel()
            }
            timer++
        }, null, 1L, 2L)
    }

    private fun habilidadPrisionVacio(player: Player) {
        // 🔥 Uso de la función centralizada
        val ray = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 15.0) {
            it is Player && esObjetivoValido(player, it)
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
                        // 🔥 Uso de la función centralizada
                        if (esObjetivoValido(player, victim)) {
                            plugin.gameManager.combatManager.takeDamage(victim)
                            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                        }
                    }
                }
            }, (i + 1).toLong())
        }
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }

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
        return loc.world.spawn(loc, ItemDisplay::class.java) { id ->
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

    override fun mostrarTrail(player: Player) {
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
        }
        activeTraps.forEach { it.remove() }
        activeTraps.clear()
    }
}
