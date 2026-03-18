package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.game.entities.GeoffreyEXE
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.*
import org.bukkit.entity.Entity
import org.bukkit.entity.EvokerFangs
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
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

/**
 *[LIRIC-MISTAKEN 2.0]
 * Sowoul: El Mago de las Ilusiones.
 * FIX: Corrutinas eliminadas y Nombres de Habilidades corregidos en el YAML.
 */
class Sowoul : Asesino(
    "sowoul",
    Mistaken.instance.messageConfig.getRawString(null, "sowoul.nombre", "<gradient:#5b00ff:#ff00ff><b>SOWOUL</b></gradient>", "asesinos_info")
) {

    private val pathBase = "asesinos.sowoul"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Visuales y cooldowns manuales
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val fakeEntities = ConcurrentHashMap.newKeySet<Entity>()

    // Cooldown manual estricto para Geoffrey (45 segundos)
    private val geoffreyCooldown = ConcurrentHashMap<UUID, Long>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$pathBase.armadura.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.LEATHER_HELMET)
                }
            }
        }

        items.forEach { k ->
            config.getString("$pathBase.items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return

        when (slot) {
            1 -> { habilidadDashMagico(player); dibujarCirculoMagico(player, org.bukkit.Particle.PORTAL, 2.0) }
            2 -> { habilidadLanzarCartas(player); dibujarEspiral(player, org.bukkit.Particle.ENCHANT, 1.5) }
            3 -> { habilidadFaucesEvocador(player); dibujarPentagrama(player, org.bukkit.Particle.WITCH, 3.0) }
            4 -> {
                // Cooldown estricto de 45 segundos exclusivo para esta habilidad super rota
                val lastUsed = geoffreyCooldown.getOrDefault(player.uniqueId, 0L)
                val timeLeft = 45000L - (System.currentTimeMillis() - lastUsed)
                if (timeLeft > 0) {
                    player.sendActionBar(mm.deserialize("<red>Geoffrey 3.0 recargando... (${timeLeft / 1000}s)"))
                    return
                }
                geoffreyCooldown[player.uniqueId] = System.currentTimeMillis()

                habilidadGeoffreyEXE(player)
                dibujarCoronaEstrellas(player, org.bukkit.Particle.DRAGON_BREATH, 2.5)
            }
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 🎨 FUNCIONES DE DIBUJO GEOMÉTRICO ---

    private fun dibujarCirculoMagico(player: Player, particula: org.bukkit.Particle, radio: Double) {
        val loc = player.location.clone().add(0.0, 0.1, 0.0)
        plugin.server.regionScheduler.run(plugin, loc, Consumer { _ ->
            for (i in 0 until 360 step 10) {
                val angulo = Math.toRadians(i.toDouble())
                val x = radio * cos(angulo)
                val z = radio * sin(angulo)
                loc.world.spawnParticle(particula, loc.clone().add(x, 0.0, z), 1, 0.0, 0.0, 0.0, 0.0)
            }
        })
    }

    private fun dibujarEspiral(player: Player, particula: org.bukkit.Particle, radioMax: Double) {
        val loc = player.location.clone().add(0.0, 0.1, 0.0)
        plugin.server.regionScheduler.run(plugin, loc, Consumer { _ ->
            var radioActual = 0.0
            var yOffset = 0.0
            for (i in 0 until 360 * 3 step 20) {
                val angulo = Math.toRadians(i.toDouble())
                val x = radioActual * cos(angulo)
                val z = radioActual * sin(angulo)
                loc.world.spawnParticle(particula, loc.clone().add(x, yOffset, z), 1, 0.0, 0.0, 0.0, 0.0)
                radioActual += (radioMax / (360 * 3 / 20.0))
                yOffset += 0.05
            }
        })
    }

    private fun dibujarPentagrama(player: Player, particula: org.bukkit.Particle, radio: Double) {
        val loc = player.location.clone().add(0.0, 0.2, 0.0)
        val puntas = 5
        plugin.server.regionScheduler.run(plugin, loc, Consumer { _ ->
            for (i in 0 until puntas) {
                val a1 = Math.toRadians((i * 360.0 / puntas) - 90)
                val a2 = Math.toRadians(((i + 2) * 360.0 / puntas) - 90)

                val p1 = loc.clone().add(radio * cos(a1), 0.0, radio * sin(a1))
                val p2 = loc.clone().add(radio * cos(a2), 0.0, radio * sin(a2))

                val distance = p1.distance(p2)
                val vector = p2.toVector().subtract(p1.toVector()).normalize().multiply(0.2)

                var length = 0.0
                val currentP = p1.clone()
                while (length < distance) {
                    loc.world.spawnParticle(particula, currentP, 1, 0.0, 0.0, 0.0, 0.0)
                    currentP.add(vector)
                    length += 0.2
                }
            }
            dibujarCirculoMagico(player, particula, radio)
        })
    }

    private fun dibujarCoronaEstrellas(player: Player, particula: org.bukkit.Particle, radio: Double) {
        val loc = player.location.clone().add(0.0, 2.5, 0.0)
        plugin.server.regionScheduler.run(plugin, loc, Consumer { _ ->
            for (i in 0 until 8) {
                val angulo = Math.toRadians(i * 45.0)
                val x = radio * cos(angulo)
                val z = radio * sin(angulo)
                val pLoc = loc.clone().add(x, 0.0, z)

                loc.world.spawnParticle(particula, pLoc, 5, 0.1, 0.1, 0.1, 0.05)
                loc.world.spawnParticle(org.bukkit.Particle.END_ROD, pLoc, 2, 0.0, -0.5, 0.0, 0.02)
            }
        })
    }

    // --- 🎩 HABILIDADES DEL MAGO ---

    private fun habilidadDashMagico(player: Player) {
        val dir = player.location.direction.normalize().multiply(3.0).setY(0.4)
        player.velocity = dir
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f)
        player.playSound(player.location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.5f)

        var ticks = 0
        val hitted = mutableSetOf<UUID>()

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 15 || !player.isOnline) {
                task.cancel()
                return@Consumer
            }

            if (ticks < 5) player.velocity = dir.clone().multiply(1.0 - (ticks * 0.1))
            player.world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, player.location, 25, 0.5, 0.5, 0.5, 0.2)

            player.getNearbyEntities(2.5, 2.5, 2.5).filterIsInstance<Player>().forEach { victim ->
                if (esObjetivoValido(player, victim) && hitted.add(victim.uniqueId)) {
                    plugin.gameManager.combatManager.takeDamage(victim)
                    victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2))
                    victim.playSound(victim.location, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f)
                }
            }
            ticks++
        }, null, 1L, 1L)
    }

    private fun habilidadLanzarCartas(player: Player) {
        val carta = player.world.spawn(player.eyeLocation, ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(Material.PAPER))
            id.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
            id.teleportDuration = 1; id.interpolationDuration = 1
        }

        fakeEntities.add(carta)
        val dir = player.location.direction.normalize().multiply(1.5)

        var ticks = 0
        carta.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 40 || !carta.isValid) {
                if (carta.isValid) carta.remove()
                task.cancel()
                return@Consumer
            }

            carta.teleport(carta.location.add(dir))
            val t = carta.transformation
            t.leftRotation.rotateY(0.8f).rotateZ(0.8f)
            carta.transformation = t

            carta.world.spawnParticle(org.bukkit.Particle.ENCHANT, carta.location, 3, 0.1, 0.1, 0.1, 0.0)

            val hit = carta.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull { esObjetivoValido(player, it) }

            if (hit != null || carta.location.block.type.isSolid) {
                hit?.let {
                    plugin.gameManager.combatManager.takeDamage(it)
                    it.addPotionEffect(PotionEffect(PotionEffectType.POISON, 60, 0))
                    it.playSound(it.location, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 1.2f)
                }
                carta.world.spawnParticle(org.bukkit.Particle.FIREWORK, carta.location, 15, 0.3, 0.3, 0.3, 0.1)
                carta.remove()
                fakeEntities.remove(carta)
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

    private fun habilidadFaucesEvocador(player: Player) {
        player.playSound(player.location, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 1f)
        val loc = player.location
        val dir = loc.direction.setY(0).normalize()

        // Genera 8 fauces en línea recta, como el ataque clásico del Evocador
        for (i in 1..8) {
            val offsetLoc = loc.clone().add(dir.clone().multiply(i * 1.5))

            player.scheduler.runDelayed(plugin, Consumer { _ ->
                if (!player.isOnline) return@Consumer

                var targetY = offsetLoc.blockY
                val world = offsetLoc.world

                while (world.getBlockAt(offsetLoc.blockX, targetY, offsetLoc.blockZ).isPassable && targetY > loc.blockY - 3) {
                    targetY--
                }
                while (!world.getBlockAt(offsetLoc.blockX, targetY, offsetLoc.blockZ).isPassable && targetY < loc.blockY + 3) {
                    targetY++
                }

                offsetLoc.y = targetY.toDouble()

                world.spawn(offsetLoc, EvokerFangs::class.java) { fangs ->
                    fangs.owner = player
                }
            }, null, (i * 2).toLong())
        }
    }

    private fun habilidadGeoffreyEXE(player: Player) {
        val geoffrey = GeoffreyEXE(plugin)
        // Lo invocamos un poco más arriba de la cabeza del mago
        geoffrey.spawn(player.location.clone().add(0.0, 2.0, 0.0))
    }

    // --- 🛠️ EQUIPAMIENTO ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

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

            // 🔥 FIX: Ruta correcta al YAML "asesinos_info.yml"
            val namePath = if (key == "arma") "sowoul.habilidades_nombres.arma"
            else "sowoul.habilidades_nombres.$key"

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

        deliver("casco", 0, true)
        deliver("pechera", 0, true)
        deliver("pantalones", 0, true)
        deliver("botas", 0, true)
        deliver("habilidad1", 1)
        deliver("habilidad2", 2)
        deliver("habilidad3", 3)
        deliver("habilidad4", 4)
        deliver("arma", 8)
    }

    // --- 🃏 VISUALES (CARTAS ORBITANTES) ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { limpiarVisuales(uuid); return }

        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarVisuales(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<ItemDisplay>().apply {
                repeat(4) {
                    add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                        id.setItemStack(ItemStack(Material.PAPER))
                        id.transformation = Transformation(
                            JomlVector3f(0f, 0f, 0f),
                            Quaternionf(),
                            JomlVector3f(0.5f, 0.5f, 0.5f),
                            Quaternionf()
                        )
                        id.teleportDuration = 3
                        id.interpolationDuration = 3
                    })
                }
            }
        }

        val anguloActual = (angulos.getOrDefault(uuid, 0.0) + 0.15) % (Math.PI * 2)
        val radio = 1.4
        val playerLoc = player.location

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val offset = (2 * Math.PI / entidades.size) * i
                val x = radio * cos(anguloActual + offset)
                val z = radio * sin(anguloActual + offset)
                val y = 1.0 + (0.3 * sin((anguloActual + offset) * 2))

                val targetLoc = playerLoc.clone().add(x, y, z)
                targetLoc.yaw = ((anguloActual * 200) % 360).toFloat()
                targetLoc.pitch = ((anguloActual * 100) % 360).toFloat()

                display.teleport(targetLoc)
            }
        }
        angulos[uuid] = anguloActual
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val pos = Vector3d(player.location.x, player.location.y + 0.2, player.location.z)
        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.ENCHANT), false, pos, Vector3f(0.3f, 0.1f, 0.3f), 0.01f, 1)

        player.world.players.forEach { viewer ->
            if (viewer != player && viewer.location.distanceSquared(player.location) < 625.0) {
                PacketEvents.getAPI().playerManager.sendPacket(viewer, packet)
            }
        }
    }

    private fun limpiarVisuales(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarVisuales(it.uniqueId)
            geoffreyCooldown.remove(it.uniqueId)
        }
        fakeEntities.forEach { it.remove() }
        fakeEntities.clear()
    }
}
