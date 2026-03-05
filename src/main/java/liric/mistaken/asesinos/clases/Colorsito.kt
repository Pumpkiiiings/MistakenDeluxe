package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.attribute.Attribute
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

class Colorsito : Asesino(
    "colorsito",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.colorsito.nombre", "<gradient:#ff0080:#00ffff:#ffff00><b>COLORSITO</b></gradient>", "asesinos_info")
) {

    private val path = "asesinos.colorsito"
    private val sonidoId = "mistaken:colorsito"

    private val orbitadores = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()
    // Ya no necesitamos 'angulos' porque usaremos tiempo real para suavidad extrema

    // Renombrado para evitar conflicto con la clase padre
    private val colorsitoTasks: MutableSet<ScheduledTask> = ConcurrentHashMap.newKeySet()
    private val musicTasks: MutableMap<UUID, ScheduledTask> = ConcurrentHashMap()

    private val orbitMaterials = listOf(
        Material.PURPLE_WOOL, Material.BLUE_WOOL, Material.LIGHT_BLUE_WOOL,
        Material.LIME_WOOL, Material.YELLOW_WOOL, Material.ORANGE_WOOL, Material.RED_WOOL
    )

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        reproducirEfectosHabilidad(player, slot)

        when (slot) {
            1 -> habilidadVividTrace(player)
            2 -> habilidadColorDrain(player)
            3 -> habilidadPulseStatic(player)
            4 -> habilidadShikisaiEnd(player)
        }
    }

    override fun equipar(player: Player) {
        // --- 📏 EFECTO MINI ---
        player.getAttribute(Attribute.SCALE)?.baseValue = 0.5

        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val configMecanica = plugin.configManager.getAsesinos()
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            // Lectura directa para asegurar que CraftEngine funcione
            val id = if (isArmor) configMecanica.getString("$path.armadura.$key")
            else configMecanica.getString("$path.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.colorsito.habilidades_nombres.arma"
            else "asesinos.colorsito.habilidades_nombres.$key"

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

        iniciarMusicaColorsito(player)
    }

    // --- 🎵 MÚSICA EN BUCLE ---
    private fun iniciarMusicaColorsito(player: Player) {
        val uuid = player.uniqueId
        detenerMusica(uuid)

        val task = player.scheduler.runAtFixedRate(plugin, { scheduledTask ->
            if (!player.isOnline || !plugin.asesinoManager.esElAsesino(player)) {
                detenerMusica(uuid)
                scheduledTask.cancel()
                return@runAtFixedRate
            }
            // Sonido solo para los demás o global
            player.world.playSound(player, sonidoId, SoundCategory.RECORDS, 2.0f, 1.0f)
        }, null, 1L, 2280L)

        task?.let { musicTasks[uuid] = it }
    }

    private fun detenerMusica(uuid: UUID) {
        musicTasks.remove(uuid)?.cancel()
        Bukkit.getOnlinePlayers().forEach { it.stopSound(sonidoId, SoundCategory.RECORDS) }
    }

    // --- ⚔️ HABILIDADES ---

    private fun habilidadVividTrace(player: Player) {
        val dir = player.location.direction.normalize().multiply(1.8).setY(0.2)
        player.velocity = dir
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f)

        var count = 0
        val hitted = mutableSetOf<UUID>()

        val task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, player.location, { t ->
            if (count >= 10 || !player.isOnline) {
                t.cancel()
                return@runAtFixedRate
            }
            player.getNearbyEntities(2.5, 2.5, 2.5).filterIsInstance<Player>().forEach { victim ->
                if (!plugin.asesinoManager.esElAsesino(victim) && hitted.add(victim.uniqueId)) {
                    plugin.gameManager.combatManager.takeDamage(victim)
                    victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(1.5).setY(0.4)
                    victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 1.5f)
                }
            }
            count++
        }, 1L, 1L)

        task?.let { colorsitoTasks.add(it) }
    }

    private fun habilidadColorDrain(player: Player) {
        player.playSound(player.location, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1f, 1.8f)
        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (!plugin.asesinoManager.esElAsesino(victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
                victim.sendMessage(mm.deserialize("<gradient:#ff0080:#00ffff><i>\"Dame tus colores...\"</i></gradient>"))
            }
        }
    }

    private fun habilidadPulseStatic(player: Player) {
        var ticks = 0
        val task = player.scheduler.runAtFixedRate(plugin, { t ->
            if (ticks > 3 || !player.isOnline) {
                t.cancel()
                return@runAtFixedRate
            }
            player.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, player.location, 20, 2.0, 2.0, 2.0, 0.05)
            player.getNearbyEntities(6.0, 6.0, 6.0).filterIsInstance<Player>().forEach { victim ->
                if (!plugin.asesinoManager.esElAsesino(victim)) {
                    plugin.gameManager.combatManager.takeDamage(victim)
                    victim.velocity = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(0.8).setY(0.3)
                }
            }
            ticks++
        }, null, 1L, 5L)

        task?.let { colorsitoTasks.add(it) }
    }

    private fun habilidadShikisaiEnd(player: Player) {
        val target = player.getNearbyEntities(15.0, 15.0, 15.0).filterIsInstance<Player>().find { !plugin.asesinoManager.esElAsesino(it) } ?: return

        player.teleportAsync(target.location).thenAccept {
            player.scheduler.run(plugin, { _ ->
                player.world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.location, 30, 0.5, 1.0, 0.5, 0.5)
                target.sendMessage(mm.deserialize("<red><b>[!] SOBRECARGA CROMÁTICA</b></red>"))
                target.velocity = Vector(0.0, 1.2, 0.0)
            }, null)
        }
    }

    // --- 🌀 RENDERIZADO VISUAL ULTRA-FLUIDO ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarEntidades(uuid); return }

        // Verificamos mundo
        val listaExistente = orbitadores[uuid]
        if (listaExistente != null && listaExistente.isNotEmpty() && listaExistente[0].world != player.world) {
            limpiarEntidades(uuid)
        }

        val entidades = orbitadores.getOrPut(uuid) {
            orbitMaterials.map { mat -> crearBloqueOrbitante(player.location, mat) }.toMutableList()
        }

        // 🔥 MAGIA MATEMÁTICA: Usamos System.currentTimeMillis() para un movimiento suave independiente del TPS.
        // Velocidad controlada: Dividimos entre 700.0 (más bajo = más rápido)
        val timeFactor = System.currentTimeMillis() / 700.0
        val radio = 1.35 // Ajustado para scale 0.5

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                // Calculamos el ángulo para este bloque específico
                val angle = timeFactor + (2 * Math.PI / entidades.size) * i

                val x = radio * cos(angle)
                val z = radio * sin(angle)
                // Movimiento "Onda" vertical suave
                val y = 0.6 + (0.15 * sin(angle * 2))

                // Aplicamos rotación al bloque para que "mire" hacia donde gira (Efecto Tornado)
                display.setRotation(Math.toDegrees(-angle).toFloat(), 0f)

                // Teleportación con suavizado del cliente
                display.teleport(player.location.clone().add(x, y, z))
            }
        }
    }

    private fun crearBloqueOrbitante(loc: Location, mat: Material): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()

            // Transformación inicial (Escala pequeña)
            bd.transformation = Transformation(
                JomlVector3f(-0.1f, -0.1f, -0.1f),
                Quaternionf(),
                JomlVector3f(0.2f, 0.2f, 0.2f), // Escala 0.2
                Quaternionf()
            )

            // 🔥 CLAVE DE LA FLUIDEZ:
            // teleportDuration: 3 ticks. Le dice al cliente "tarda 150ms en llegar al destino".
            // Como actualizamos cada 1 tick (50ms), el cliente siempre está interpolando suavemente.
            bd.teleportDuration = 3
            bd.interpolationDuration = 0

            // Iluminación máxima para evitar sombras feas al girar
            bd.brightness = Display.Brightness(15, 15)
        }
    }

    override fun mostrarTrail(player: Player) {
        // Partículas sutiles
        val loc = player.location.add(0.0, 0.3, 0.0)
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.DUST, ParticleDustData(1f, 0f, 0.8f, 1.2f)),
            false, Vector3d(loc.x, loc.y, loc.z), Vector3f(0.1f, 0.1f, 0.1f), 0.01f, 1
        )
        // Usamos una distancia de renderizado menor para no saturar clientes lejanos
        val distSq = 400.0 // 20 bloques
        for (p in player.world.players) {
            if (p.location.distanceSquared(loc) < distSq) {
                PacketEvents.getAPI().playerManager.sendPacket(p, packet)
            }
        }
    }

    private fun limpiarEntidades(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarEntidades(it.uniqueId)
            detenerMusica(it.uniqueId)
            it.getAttribute(Attribute.SCALE)?.baseValue = 1.0
        }

        colorsitoTasks.forEach { it.cancel() }
        colorsitoTasks.clear()
    }
}
