package liric.mistaken.roles.survivors.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.Survivor
import liric.mistaken.utils.hooks.CraftEngine
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 *[LIRIC-MISTAKEN 2.0]
 * Kasane Teto: La Comandante.
 * FIX: RotaciÃ³n TrigonomÃ©trica Absoluta. Sombrero y Drills giran 100% pegados a la cabeza sin desarmarse.
 */
class KasaneTeto : Survivor(
    "teto",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "supervivientes.teto.nombre", "survivors_info")
) {

    private val pathBase = "supervivientes.teto"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()

    // Guardamos las piezas individuales para aplicarles la matemÃ¡tica de rotaciÃ³n
    private val tetoAccesorios = ConcurrentHashMap<UUID, MutableList<BlockDisplay>>()

    override fun useSkill(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSurvivorConfig(this.id)
        val langConfig = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 15))) {
                disparoParalizador(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 20))) {
                municionCegadora(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("items.skill3_cooldown", 12))) {
                disparoDeImpulso(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        val msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        player.getAttribute(Attribute.SCALE)?.baseValue = 0.8861

        val langInfo = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")
        val configMecanica = plugin.configManager.getSurvivorConfig(this.id)

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("armor.$key")
            else configMecanica.getString("items.$key")

            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else if (key == "skill1") ItemStack(Material.IRON_HOE) else null
            } ?: return

            val meta = item.itemMeta
            langInfo.getString("$pathBase.skill_names.$key")?.let {
                meta.displayName(mm.deserialize(it))
            }
            item.itemMeta = meta

            if (isArmor) {
                when(key) {
                    "helmet" -> inv.helmet = item
                    "chestplate" -> inv.chestplate = item
                    "leggings" -> inv.leggings = item
                    "boots" -> inv.boots = item
                }
            } else {
                inv.setItem(slot, item)
            }
        }

        deliver("helmet", 0, true); deliver("chestplate", 0, true)
        deliver("leggings", 0, true); deliver("boots", 0, true)

        deliver("skill1", 0); deliver("skill2", 1); deliver("skill3", 2)

        player.updateInventory()
        crearCosmeticosTeto(player)
    }

    // =========================================================================================
    // =                             SISTEMA DE ARMAS (REVÃ“LVER)                               =
    // =========================================================================================

    private fun disparoParalizador(player: Player) {
        val startLoc = player.eyeLocation
        val direction = startLoc.direction.normalize()

        player.world.playSound(startLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 0.5f)
        player.world.playSound(startLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 2.0f)

        val result = player.world.rayTrace(startLoc, direction, 25.0, FluidCollisionMode.NEVER, true, 0.5) { it is Player && it != player }

        val distance = result?.hitPosition?.distance(startLoc.toVector()) ?: 25.0
        for (i in 0..(distance * 2).toInt()) {
            val pLoc = startLoc.clone().add(direction.clone().multiply(i * 0.5))
            player.world.spawnParticle(org.bukkit.Particle.CRIT, pLoc, 1, 0.0, 0.0, 0.0, 0.0)
        }

        val hitEntity = result?.hitEntity as? Player
        if (hitEntity != null) {
            val session = plugin.sessionManager.getSession(hitEntity)
            if (session?.isKiller(hitEntity.uniqueId) == true)
            player.world.spawnParticle(org.bukkit.Particle.EXPLOSION, hitEntity.eyeLocation, 1)
            player.world.playSound(hitEntity.location, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.5f)

            hitEntity.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 4))
            hitEntity.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3))

            player.sendMessage(mm.deserialize("<green>Â¡Impacto directo! El asesino ha sido paralizado."))
        }
    }

    private fun municionCegadora(player: Player) {
        player.world.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.5f)
        val snowball = player.launchProjectile(Snowball::class.java)
        snowball.item = ItemStack(Material.CLAY_BALL)
        snowball.velocity = player.location.direction.multiply(2.5)

        var ticks = 0
        snowball.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks > 60 || !snowball.isValid) {
                val loc = snowball.location
                loc.world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.1f)
                loc.world.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, loc, 100, 3.0, 2.0, 3.0, 0.01)

                loc.world.getNearbyPlayers(loc, 4.0).forEach { victim ->
                    val session = plugin.sessionManager.getSession(victim)
                    if (session?.isKiller(victim.uniqueId) == true) {
                        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                        victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_SCREAM, 0.5f, 0.1f)
                    }
                }
                task.cancel()
                return@Consumer
            }
            snowball.world.spawnParticle(org.bukkit.Particle.SMOKE, snowball.location, 1, 0.0, 0.0, 0.0, 0.0)
            ticks++
        }, null, 1L, 1L)
    }

    private fun disparoDeImpulso(player: Player) {
        player.world.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f)
        player.world.spawnParticle(org.bukkit.Particle.EXPLOSION, player.location.add(0.0, 0.5, 0.0), 3)

        val dir = player.location.direction.multiply(-1.5).setY(0.6)
        player.velocity = dir

        val packet = WrapperPlayServerParticle(Particle(ParticleTypes.CLOUD), false, Vector3d(player.location.x, player.location.y, player.location.z), Vector3f(0.2f, 0.2f, 0.2f), 0.1f, 10)
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }

    // =========================================================================================
    // =                     SISTEMA DE GEOMETRÃA ABSOLUTA (SIN DEFORMACIONES)                 =
    // =========================================================================================

    private fun crearCosmeticosTeto(player: Player) {
        val uuid = player.uniqueId
        borrarCosmeticos(uuid)

        val startLoc = player.location
        val displays = mutableListOf<BlockDisplay>()

        // Helper para crear un bloque con la escala centrada en -50% (Para que rote desde el medio de sÃ­ mismo)
        fun spawnBlock(mat: Material, scale: JomlVector3f): BlockDisplay {
            return liric.mistaken.packet.PacketFactory.displays.buildBlockDisplay(org.bukkit.plugin.java.JavaPlugin.getPlugin(liric.mistaken.Mistaken::class.java).sessionManager.getSession(player)?.getPlayers() ?: listOf(player), startLoc) { bd ->
                bd.block = mat.createBlockData()
                bd.transformation = Transformation(JomlVector3f(-scale.x/2, -scale.y/2, -scale.z/2), Quaternionf(), scale, Quaternionf())
                bd.teleportDuration = 1
                bd.interpolationDuration = 1
                bd.isGlowing = false
            }.also { displays.add(it) }
        }

        // --- 1. SOMBRERO COMANDANTE (Ãndices 0, 1, 2, 3) ---
        spawnBlock(Material.BLACK_CONCRETE, JomlVector3f(0.7f, 0.05f, 0.7f)) // Visera
        spawnBlock(Material.ORANGE_TERRACOTTA, JomlVector3f(0.42f, 0.1f, 0.42f)) // Banda
        spawnBlock(Material.BLACK_CONCRETE, JomlVector3f(0.4f, 0.35f, 0.4f)) // Copa
        spawnBlock(Material.GOLD_BLOCK, JomlVector3f(0.1f, 0.1f, 0.02f)) // Logo

        // --- 2. DRILL IZQUIERDO (Ãndices 4, 5, 6) ---
        spawnBlock(Material.RED_CONCRETE, JomlVector3f(0.25f, 0.25f, 0.25f))
        spawnBlock(Material.RED_CONCRETE, JomlVector3f(0.2f, 0.2f, 0.2f))
        spawnBlock(Material.RED_CONCRETE, JomlVector3f(0.15f, 0.15f, 0.15f))

        // --- 3. DRILL DERECHO (Ãndices 7, 8, 9) ---
        spawnBlock(Material.RED_CONCRETE, JomlVector3f(0.25f, 0.25f, 0.25f))
        spawnBlock(Material.RED_CONCRETE, JomlVector3f(0.2f, 0.2f, 0.2f))
        spawnBlock(Material.RED_CONCRETE, JomlVector3f(0.15f, 0.15f, 0.15f))

        tetoAccesorios[uuid] = displays

        // BUCLE ACTUALIZADOR
        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!player.isOnline || player.isDead || !plugin.supervivienteManager.esSurvivorActivo(player)) {
                borrarCosmeticos(uuid)
                task.cancel()
                return@Consumer
            }
            actualizarMatematicas3D(player, displays)
        }, null, 1L, 1L)
    }

    private fun actualizarMatematicas3D(player: Player, displays: List<BlockDisplay>) {
        if (displays.size < 10) return

        val eyeLoc = player.eyeLocation
        val yawRad = -Math.toRadians(eyeLoc.yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(eyeLoc.pitch.coerceIn(-30f, 45f).toDouble()).toFloat() // LÃ­mite de inclinaciÃ³n

        val forward = eyeLoc.direction.clone().setY(0).normalize()
        val right = forward.clone().crossProduct(org.bukkit.util.Vector(0, 1, 0)).normalize()
        val up = org.bukkit.util.Vector(0, 1, 0)

        val bobbingY = if (player.velocity.lengthSquared() > 0.01) Math.sin((System.currentTimeMillis() / 100) % 360.toDouble()) * 0.05 else 0.0
        val baseHead = eyeLoc.clone().add(0.0, 0.25 + bobbingY, 0.0).add(forward.clone().multiply(-0.05))

        val headRot = Quaternionf().rotateY(yawRad).rotateX(-pitchRad)

        // FunciÃ³n para aplicar offset a un bloque basado en la rotaciÃ³n de la cabeza
        fun applyOffset(index: Int, rightOff: Double, upOff: Double, fwdOff: Double, rotExtra: Quaternionf? = null) {
            val pLoc = baseHead.clone()
                .add(right.clone().multiply(rightOff))
                .add(up.clone().multiply(upOff))
                .add(forward.clone().multiply(fwdOff))

            displays[index].teleport(pLoc)

            // Mantiene su centro, y rota con la cabeza (mÃ¡s una rotaciÃ³n extra si se requiere)
            val currentTrans = displays[index].transformation
            val finalRot = if (rotExtra != null) Quaternionf(headRot).mul(rotExtra) else headRot
            displays[index].transformation = Transformation(currentTrans.translation, finalRot, currentTrans.scale, Quaternionf())
        }

        // ===================================
        // SOMBRERO (Siempre pegado arriba)
        // ===================================
        applyOffset(0, 0.0, 0.15, 0.0) // Visera
        applyOffset(1, 0.0, 0.25, 0.0) // Banda
        applyOffset(2, 0.0, 0.40, 0.0) // Copa
        applyOffset(3, 0.0, 0.25, 0.22) // Logo (Hacia adelante en el sombrero)

        // ===================================
        // COLETAS (Rotadas hacia los lados)
        // ===================================
        val rotLeft = Quaternionf().rotateZ(0.2f) // Cae un poco hacia la izquierda
        applyOffset(4, -0.3, 0.0, -0.1, rotLeft)
        applyOffset(5, -0.32, -0.2, -0.1, rotLeft)
        applyOffset(6, -0.34, -0.4, -0.1, rotLeft)

        val rotRight = Quaternionf().rotateZ(-0.2f) // Cae un poco hacia la derecha
        applyOffset(7, 0.3, 0.0, -0.1, rotRight)
        applyOffset(8, 0.32, -0.2, -0.1, rotRight)
        applyOffset(9, 0.34, -0.4, -0.1, rotRight)
    }

    private fun borrarCosmeticos(uuid: UUID) {
        tetoAccesorios.remove(uuid)?.forEach { if (it.isValid) it.remove() }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            it.getAttribute(Attribute.SCALE)?.baseValue = 1.0
            borrarCosmeticos(it.uniqueId)
        }
    }
}






