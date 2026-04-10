package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle as PEParticle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.HitboxVisualizer
import org.bukkit.*
import org.bukkit.Particle
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 *[LIRIC-MISTAKEN 2.0]
 * Caine: El Maestro de Ceremonias.
 * FIX: Sombrero y Batuta agrupados en ANCLAS INDEPENDIENTES para evitar deformaciones.
 */
class Caine : Asesino(
    "caine",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.caine.nombre", "<gradient:#ff0000:#ffffff><b>CAINE</b></gradient>", "asesinos_info")
) {

    private val pathBase = "asesinos.caine"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Almacenes Visuales (Ahora guardamos las anclas en vez de todas las partes)
    private val anchorHat = ConcurrentHashMap<UUID, BlockDisplay>()
    private val anchorBaton = ConcurrentHashMap<UUID, BlockDisplay>()
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<ItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val animacionBatuta = ConcurrentHashMap<UUID, Int>()

    private val eyeTextureUrl = "http://textures.minecraft.net/texture/a6c2221506db205176272d48d8275d8dec17577bc21198398a9e5159a07434"

    init {
        preLoadKit()
    }

    private fun getEyeSkull(): ItemStack {
        val skull = ItemStack(Material.PLAYER_HEAD)
        skull.editMeta { meta ->
            if (meta !is SkullMeta) return@editMeta
            val profile = Bukkit.createProfile(UUID.randomUUID())
            profile.setProperty(com.destroystokyo.paper.profile.ProfileProperty("textures", getBase64Texture(eyeTextureUrl)))
            meta.playerProfile = profile
        }
        return skull
    }

    private fun getBase64Texture(url: String): String {
        return Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}".toByteArray())
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("habilidad1", "habilidad2", "habilidad3", "habilidad4")

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

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")
        val configMecanica = plugin.configManager.getAsesinos()

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key") else configMecanica.getString("$pathBase.items.$key")
            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val mat = Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase())
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = "asesinos.caine.habilidades_nombres.$key"
            langInfo.getString(namePath)?.let { item.editMeta { meta -> meta.displayName(mm.deserialize(it)) } }

            if (isArmor) {
                when(key) {
                    "casco" -> inv.helmet = item; "pechera" -> inv.chestplate = item
                    "pantalones" -> inv.leggings = item; "botas" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        deliver("casco", 0, true); deliver("pechera", 0, true)
        deliver("pantalones", 0, true); deliver("botas", 0, true)
        deliver("habilidad1", 0); deliver("habilidad2", 1)
        deliver("habilidad3", 2); deliver("habilidad4", 3)

        player.updateInventory()
        crearAccesoriosEscenario(player)
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return

        animacionBatuta[player.uniqueId] = 10

        when (slot) {
            0 -> habilidadMazoChillon(player)
            1 -> habilidadDashPotente(player)
            2 -> habilidadDanzaObligatoria(player)
            3 -> habilidadProyectilOjo(player)
        }
    }

    // =========================================================================================
    // =                                  SISTEMA COMIC (TEXT 3D)                              =
    // =========================================================================================

    private fun spawnOnomatopoeia(loc: Location, text: String, colorText: String) {
        val spawnLoc = loc.clone().add(0.0, 1.5, 0.0)
        val textDisplay = loc.world.spawn(spawnLoc, TextDisplay::class.java) { td ->
            td.text(mm.deserialize("<$colorText><bold><italic>$text</italic></bold>"))
            td.billboard = Display.Billboard.CENTER
            td.isPersistent = false
            td.backgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0)
            td.isShadowed = true
            td.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(2.5f, 2.5f, 2.5f), Quaternionf())
            td.teleportDuration = 5
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
            textDisplay.teleport(spawnLoc.clone().add(0.0, 1.5, 0.0))
        }, 1L)

        plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
            textDisplay.remove()
        }, 20L)
    }

    // =========================================================================================
    // =                                      HABILIDADES                                      =
    // =========================================================================================

    private fun habilidadMazoChillon(player: Player) {
        val result = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, 15.0, 0.5) { it is Player && esObjetivoValido(player, it) }
        val victim = result?.hitEntity as? Player

        if (victim == null) {
            player.sendActionBar(mm.deserialize("<red>No hay nadie en la mira para aplastar."))
            return
        }

        spawnOnomatopoeia(player.location, "¡ZAS!", "yellow")
        player.playSound(player.location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1f)

        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 30, 255, false, false, false))
        victim.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 30, 250, false, false, false))
        victim.sendActionBar(mm.deserialize("<red><bold>¡MIRA ARRIBA!</bold>"))

        val targetLoc = victim.location.clone()

        val mazoHead = victim.world.spawn(targetLoc.clone().add(0.0, 5.0, 0.0), BlockDisplay::class.java) { bd ->
            bd.block = Material.RED_CONCRETE.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-1f, 0f, -1f), Quaternionf(), JomlVector3f(2f, 1.5f, 2f), Quaternionf())
            bd.interpolationDuration = 10
        }
        val mazoHandle = victim.world.spawn(targetLoc.clone().add(0.0, 6.5, 0.0), BlockDisplay::class.java) { bd ->
            bd.block = Material.OAK_WOOD.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-0.25f, 0f, -0.25f), Quaternionf(), JomlVector3f(0.5f, 3f, 0.5f), Quaternionf())
            bd.interpolationDuration = 10
        }

        plugin.server.regionScheduler.runDelayed(plugin, targetLoc, Consumer { _ ->
            mazoHead.transformation = Transformation(JomlVector3f(-1f, -4f, -1f), Quaternionf(), JomlVector3f(2f, 1.5f, 2f), Quaternionf())
            mazoHandle.transformation = Transformation(JomlVector3f(-0.25f, -4f, -0.25f), Quaternionf(), JomlVector3f(0.5f, 3f, 0.5f), Quaternionf())

            plugin.server.regionScheduler.runDelayed(plugin, targetLoc, Consumer { _ ->
                spawnOnomatopoeia(targetLoc, "¡BONK!", "red")
                victim.world.playSound(targetLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)
                victim.world.playSound(targetLoc, Sound.ENTITY_PARROT_DEATH, 1.5f, 0.8f)
                victim.world.spawnParticle(Particle.EXPLOSION, targetLoc, 2)

                if (victim.isOnline && esObjetivoValido(player, victim)) {
                    val dmg = 8.0
                    victim.health = max(0.0, victim.health - dmg)
                    if (victim.health <= 0.0) plugin.gameManager.playerController.handlePlayerDeath(victim)
                }

                for (i in 0 until 5) {
                    val dirt = victim.world.spawn(targetLoc.clone().add(Math.random()*2-1, 0.0, Math.random()*2-1), BlockDisplay::class.java) { bd ->
                        bd.block = Material.COBBLESTONE.createBlockData()
                        bd.transformation = Transformation(JomlVector3f(), Quaternionf().rotateX((Math.random()).toFloat()), JomlVector3f(0.6f, 0.6f, 0.6f), Quaternionf())
                        bd.teleportDuration = 15
                    }
                    plugin.server.regionScheduler.runDelayed(plugin, targetLoc, Consumer { _ ->
                        dirt.teleport(dirt.location.clone().subtract(0.0, 1.5, 0.0))
                    }, 40L)
                    plugin.server.regionScheduler.runDelayed(plugin, targetLoc, Consumer { _ -> dirt.remove() }, 60L)
                }

                mazoHead.remove(); mazoHandle.remove()

            }, 10L)
        }, 5L)
    }

    private fun habilidadDashPotente(player: Player) {
        spawnOnomatopoeia(player.location, "¡FWOOSH!", "aqua")
        player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.5f)

        val dir = player.location.direction.normalize().multiply(2.5).setY(0.2)
        HitboxVisualizer.createHitbox(player.location, 1.5, 1.5, 1.5, Material.RED_STAINED_GLASS)?.let { hitbox ->
            var ticks = 0
            var hitEntity = false

            player.scheduler.runAtFixedRate(plugin, Consumer { task ->
                if (ticks >= 15 || !player.isOnline || hitEntity) {
                    hitbox.remove()
                    task.cancel()
                    return@Consumer
                }

                player.velocity = dir
                hitbox.teleport(player.location)
                player.world.spawnParticle(Particle.CLOUD, player.location, 10, 0.5, 0.5, 0.5, 0.1)

                val victim = player.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>().firstOrNull { esObjetivoValido(player, it) }
                if (victim != null) {
                    hitEntity = true
                    spawnOnomatopoeia(victim.location, "¡BAM!", "gold")
                    victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)

                    val dmg = 10.0
                    victim.health = max(0.0, victim.health - dmg)
                    if (victim.health <= 0.0) plugin.gameManager.playerController.handlePlayerDeath(victim)

                    victim.velocity = dir.clone().multiply(0.5).setY(0.5)
                    hitbox.remove()
                    task.cancel()
                }
                ticks++
            }, null, 1L, 1L)
        }
    }

    private fun habilidadDanzaObligatoria(player: Player) {
        val loc = player.location
        spawnOnomatopoeia(loc, "¡DANCE!", "light_purple")
        loc.world.playSound(loc, Sound.MUSIC_DISC_CAT, 1f, 1f)
        HitboxVisualizer.drawInstantHitbox(plugin, loc, 10.0, 5.0, 10.0, 20L, Material.MAGENTA_STAINED_GLASS)

        val victims = loc.world.getNearbyPlayers(loc, 10.0).filter { esObjetivoValido(player, it) }

        victims.forEach { victim ->
            victim.sendActionBar(mm.deserialize("<light_purple>¡NO PUEDES PARAR DE BAILAR!"))
            var ticks = 0
            victim.scheduler.runAtFixedRate(plugin, Consumer { task ->
                if (ticks >= 100 || !victim.isOnline) {
                    task.cancel()
                    return@Consumer
                }

                victim.setRotation(victim.yaw + 35f, victim.pitch)
                if (ticks % 10 == 0) {
                    victim.velocity = victim.location.direction.multiply(-0.3).setY(0.4)
                    victim.world.playSound(victim.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, (Math.random()*2).toFloat())
                    victim.world.spawnParticle(Particle.NOTE, victim.location.add(0.0, 2.0, 0.0), 2, 0.5, 0.5, 0.5, 1.0)
                }
                ticks++
            }, null, 1L, 1L)
        }
    }

    private fun habilidadProyectilOjo(player: Player) {
        spawnOnomatopoeia(player.location, "¡PEW!", "green")
        player.playSound(player.location, Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f)

        val proj = player.launchProjectile(Snowball::class.java)
        val visualOjo = player.world.spawn(proj.location, ItemDisplay::class.java) { id ->
            id.setItemStack(getEyeSkull())
            id.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.8f, 0.8f, 0.8f), Quaternionf())
            id.teleportDuration = 1
        }

        var ticks = 0
        proj.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 100 || !proj.isValid) {
                spawnOnomatopoeia(visualOjo.location, "¡SPLAT!", "dark_green")
                visualOjo.world.spawnParticle(Particle.ITEM_SLIME, visualOjo.location, 50, 0.5, 0.5, 0.5, 0.1)

                val victim = visualOjo.world.getNearbyPlayers(visualOjo.location, 1.5).firstOrNull { esObjetivoValido(player, it) }
                victim?.let {
                    plugin.gameManager.combatManager.takeDamage(it)
                    it.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                }

                visualOjo.remove()
                task.cancel()
                return@Consumer
            }
            visualOjo.teleport(proj.location)
            ticks++
        }, null, 1L, 1L)
    }

    // =========================================================================================
    // =               SISTEMA DE GRUPOS ANCLADOS (SOMBRERO Y BATUTA SEPARADOS)                =
    // =========================================================================================

    private fun crearAccesoriosEscenario(player: Player) {
        val uuid = player.uniqueId
        borrarAccesorios(uuid)

        val startLoc = player.location

        // 🔥 1. ANCLA DEL SOMBRERO (Invisible, Base 0,0,0)
        val aHat = startLoc.world.spawn(startLoc, BlockDisplay::class.java) { bd ->
            bd.block = Material.BARRIER.createBlockData()
            bd.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0f, 0f, 0f), Quaternionf())
            bd.isPersistent = false; bd.teleportDuration = 1; bd.interpolationDuration = 1; bd.isGlowing = false
        }

        // Construir piezas del Sombrero y añadirlas como pasajeros del ancla
        val hatBrim = startLoc.world.spawn(startLoc, BlockDisplay::class.java) { bd ->
            bd.block = Material.BLACK_CONCRETE.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-0.4f, 0.0f, -0.4f), Quaternionf(), JomlVector3f(0.8f, 0.05f, 0.8f), Quaternionf())
        }
        val hatBand = startLoc.world.spawn(startLoc, BlockDisplay::class.java) { bd ->
            bd.block = Material.RED_CONCRETE.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-0.31f, 0.05f, -0.31f), Quaternionf(), JomlVector3f(0.62f, 0.15f, 0.62f), Quaternionf())
        }
        val hatTop = startLoc.world.spawn(startLoc, BlockDisplay::class.java) { bd ->
            bd.block = Material.BLACK_CONCRETE.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-0.3f, 0.2f, -0.3f), Quaternionf(), JomlVector3f(0.6f, 0.5f, 0.6f), Quaternionf())
        }
        aHat.addPassenger(hatBrim); aHat.addPassenger(hatBand); aHat.addPassenger(hatTop)
        anchorHat[uuid] = aHat

        // 🔥 2. ANCLA DE LA BATUTA (Invisible, Base 0,0,0)
        val aBaton = startLoc.world.spawn(startLoc, BlockDisplay::class.java) { bd ->
            bd.block = Material.BARRIER.createBlockData()
            bd.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0f, 0f, 0f), Quaternionf())
            bd.isPersistent = false; bd.teleportDuration = 1; bd.interpolationDuration = 1; bd.isGlowing = false
        }

        // Construir piezas de la Batuta y añadirlas como pasajeros
        val batonStick = startLoc.world.spawn(startLoc, BlockDisplay::class.java) { bd ->
            bd.block = Material.BLACK_CONCRETE.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-0.02f, -0.4f, -0.02f), Quaternionf(), JomlVector3f(0.04f, 0.8f, 0.04f), Quaternionf())
        }
        val batonBall = startLoc.world.spawn(startLoc, BlockDisplay::class.java) { bd ->
            bd.block = Material.GOLD_BLOCK.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-0.06f, 0.4f, -0.06f), Quaternionf(), JomlVector3f(0.12f, 0.12f, 0.12f), Quaternionf())
        }
        aBaton.addPassenger(batonStick); aBaton.addPassenger(batonBall)
        anchorBaton[uuid] = aBaton

        // 3. OJOS ORBITANTES (Independientes, no necesitan ancla porque rotan circularmente)
        val ojos = mutableListOf<ItemDisplay>()
        val skullItem = getEyeSkull()
        repeat(4) {
            ojos.add(player.world.spawn(player.location, ItemDisplay::class.java) { id ->
                id.setItemStack(skullItem)
                id.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.5f, 0.5f, 0.5f), Quaternionf())
                id.teleportDuration = 3
            })
        }
        orbitadores[uuid] = ojos

        // BUCLE ACTUALIZADOR
        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!player.isOnline || player.isDead || !plugin.asesinoManager.esElAsesino(player)) {
                borrarAccesorios(uuid)
                task.cancel()
                return@Consumer
            }
            actualizarFisicasGrupos(player, aHat, aBaton, ojos)
        }, null, 1L, 1L)
    }

    private fun actualizarFisicasGrupos(player: Player, aHat: BlockDisplay, aBaton: BlockDisplay, ojos: List<ItemDisplay>) {
        val uuid = player.uniqueId
        val pLoc = player.location
        val eyeLoc = player.eyeLocation

        val yawRad = -Math.toRadians(pLoc.yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pLoc.pitch.coerceIn(-45f, 45f).toDouble()).toFloat()

        val forward = pLoc.direction.clone().setY(0).normalize()
        val right = forward.clone().crossProduct(org.bukkit.util.Vector(0, 1, 0)).normalize()

        val tickAnim = (System.currentTimeMillis() / 100) % 360
        val bobbingY = if (player.velocity.lengthSquared() > 0.01) Math.sin(tickAnim.toDouble()) * 0.05 else 0.0

        // ===============================================
        // 1. MOVER ANCLA DEL SOMBRERO
        // ===============================================
        val headLoc = eyeLoc.clone().add(0.0, 0.4 + bobbingY, 0.0)

        // Mover posición del ancla
        aHat.teleport(headLoc)
        // Rotar ancla (esto rota a sus pasajeros sin deformarlos)
        aHat.transformation = Transformation(JomlVector3f(), Quaternionf().rotateY(yawRad).rotateX(-pitchRad), JomlVector3f(), Quaternionf())

        // ===============================================
        // 2. MOVER ANCLA DE LA BATUTA
        // ===============================================
        val animTicks = animacionBatuta.getOrDefault(uuid, 0)
        var swingAngle = 0f

        if (animTicks > 0) {
            swingAngle = Math.toRadians((animTicks * 9).toDouble()).toFloat()
            animacionBatuta[uuid] = animTicks - 1
            if (animTicks == 5) player.world.spawnParticle(Particle.END_ROD, eyeLoc.clone().add(forward.clone().multiply(1.5)), 5, 0.1, 0.1, 0.1, 0.1)
        }

        // Posicionada a la derecha y adelante
        val handLoc = eyeLoc.clone().add(0.0, -0.4 + bobbingY, 0.0).add(right.multiply(0.5)).add(forward.multiply(0.6))

        aBaton.teleport(handLoc)
        aBaton.transformation = Transformation(JomlVector3f(), Quaternionf().rotateY(yawRad).rotateX(-pitchRad - swingAngle), JomlVector3f(), Quaternionf())

        // ===============================================
        // 3. OJOS ORBITANTES
        // ===============================================
        val anguloActual = (angulos.getOrDefault(uuid, 0.0) + 0.15) % (Math.PI * 2)
        for (i in ojos.indices) {
            if (ojos[i].isValid) {
                val offset = (2 * Math.PI / ojos.size) * i
                val x = 1.4 * cos(anguloActual + offset)
                val z = 1.4 * sin(anguloActual + offset)
                val y = 1.2 + (0.3 * sin((anguloActual + offset) * 2))
                val targetLoc = pLoc.clone().add(x, y, z)
                targetLoc.direction = pLoc.toVector().subtract(targetLoc.toVector())
                ojos[i].teleport(targetLoc)
            }
        }
        angulos[uuid] = anguloActual
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val pos = Vector3d(player.location.x, player.location.y + 0.2, player.location.z)
        val packet = WrapperPlayServerParticle(PEParticle(ParticleTypes.DUST, ParticleDustData(1f, 0f, 0f, 1f)), false, pos, Vector3f(0.3f, 0.1f, 0.3f), 0.01f, 2)
        player.world.players.forEach { if (it.location.distanceSquared(player.location) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(it, packet) }
    }

    override fun mostrarTrailFisico(player: Player) {}

    private fun borrarAccesorios(uuid: UUID) {
        // Borrar pasajeros de los anclas primero, luego el ancla
        anchorHat.remove(uuid)?.let { a -> a.passengers.forEach { it.remove() }; a.remove() }
        anchorBaton.remove(uuid)?.let { a -> a.passengers.forEach { it.remove() }; a.remove() }

        orbitadores.remove(uuid)?.forEach { if (it.isValid) it.remove() }
        angulos.remove(uuid)
        animacionBatuta.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { borrarAccesorios(it.uniqueId) }
    }
}
