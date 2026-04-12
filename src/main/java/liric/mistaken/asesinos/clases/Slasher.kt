package liric.mistaken.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.HitboxVisualizer
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
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
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class Slasher : Asesino(
    "slasher",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.slasher.nombre", "<white><b>PUMPKIN WHITE</b>", "asesinos_info")
), Listener {

    private val pathBase = "asesinos.slasher"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()
    private val temporaryEntities = ConcurrentHashMap.newKeySet<Entity>()

    // 🔥 Sistema de sonidos sin repetición
    private val attackSoundsQueue = ConcurrentHashMap<UUID, MutableList<Int>>()

    init {
        preLoadKit()
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4")

        armor.forEach { k ->
            config.getString("$pathBase.armadura.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.NETHERITE_HELMET)
                }
            }
        }

        items.forEach { k ->
            config.getString("$pathBase.items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.PAPER)
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        when (slot) {
            1 -> if (!checkCooldown(player, 1)) { habilidadSedDeSangre(player); reproducirEfectosHabilidad(player, 1) }
            2 -> if (!checkCooldown(player, 2)) { habilidadMacheteLanzable(player); reproducirEfectosHabilidad(player, 2) }
            3 -> if (!checkCooldown(player, 3)) { habilidadPresencia(player); reproducirEfectosHabilidad(player, 3) }
            4 -> if (!checkCooldown(player, 4)) { habilidadEjecucion(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

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
        deliver("habilidad3", 3); deliver("habilidad4", 4)
        deliver("arma", 8)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSlasherAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val session = plugin.sessionManager.getSession(attacker) ?: return
        if (session.esAsesino(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
            if (esObjetivoValido(attacker, victim)) {
                val uuid = attacker.uniqueId
                val queue = attackSoundsQueue.getOrPut(uuid) { mutableListOf(1, 2, 3, 4).apply { shuffle() } }

                if (queue.isEmpty()) {
                    queue.addAll(listOf(1, 2, 3, 4))
                    queue.shuffle()
                }
                val soundIndex = queue.removeAt(0)
                val soundName = "mistaken:whitepumpkin_ataque_$soundIndex"
                attacker.world.playSound(attacker.location, soundName, SoundCategory.PLAYERS, 3.0f, 1.0f)
            }
        }
    }

    private fun habilidadSedDeSangre(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 160, 2))
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 160, 1))
        dibujarEstrella(player, Color.RED, 1.5, 5)

        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
            }
        }, null, 160L)
    }

    private fun habilidadMacheteLanzable(player: Player) {
        val macheteItem = itemKitCache["arma"]?.clone() ?: ItemStack(Material.IRON_SWORD)
        val spawnLoc = player.eyeLocation.clone()

        val machete = player.world.spawn(spawnLoc, ItemDisplay::class.java) { id ->
            id.setItemStack(macheteItem)
            id.transformation = Transformation(JomlVector3f(), Quaternionf().rotateX(Math.toRadians(90.0).toFloat()), JomlVector3f(0.7f, 0.7f, 0.7f), Quaternionf())
            id.interpolationDuration = 1; id.teleportDuration = 1
        }

        temporaryEntities.add(machete)
        val direction = player.location.direction.multiply(1.4)

        // 🔥 HITBOX: Proyectil
        val hitbox = HitboxVisualizer.createHitbox(spawnLoc, 1.2, 1.2, 1.2, Material.ORANGE_STAINED_GLASS)

        var ticks = 0
        machete.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 30 || !machete.isValid) {
                if (machete.isValid) machete.remove()
                hitbox?.remove()
                task.cancel()
                return@Consumer
            }

            machete.teleport(machete.location.add(direction))
            hitbox?.teleport(machete.location) // Sigue al machete

            val hit = machete.getNearbyEntities(1.2, 1.2, 1.2).filterIsInstance<Player>().firstOrNull { esObjetivoValido(player, it) }

            if (hit != null || machete.location.block.type.isSolid) {
                hit?.let {
                    plugin.combatManager.takeDamage(it)
                    it.playSound(it.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.8f)
                    hitbox?.block = Material.RED_STAINED_GLASS.createBlockData() // Feedback visual
                }
                machete.remove()

                player.scheduler.runDelayed(plugin, Consumer { _ -> hitbox?.remove() }, null, 2L)
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

    private fun habilidadPresencia(player: Player) {
        player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.8f)

        // 🔥 HITBOX: Grito en área
        HitboxVisualizer.drawInstantHitbox(plugin, player.location, 8.0, 8.0, 8.0, 20L, Material.PURPLE_STAINED_GLASS)

        player.getNearbyEntities(8.0, 8.0, 8.0).filterIsInstance<Player>().forEach { victim ->
            if (esObjetivoValido(player, victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 100, 1))
            }
        }
    }

    private fun habilidadEjecucion(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 300, 3))
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 300, 2))
        dibujarEstrella(player, Color.MAROON, 2.5, 5)

        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline && plugin.asesinoManager.esElAsesino(player)) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 2))
            }
        }, null, 300L)
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return
        val pos = Vector3d(player.location.x, player.location.y + 1.2, player.location.z)
        val blood = WrapperPlayServerParticle(Particle(ParticleTypes.DUST, ParticleDustData(1f, 0f, 0f, 0.8f)), false, pos, Vector3f(0.1f, 0.2f, 0.1f), 0.02f, 1)
        player.world.players.forEach { if (it.location.distanceSquared(player.location) < 625.0) PacketEvents.getAPI().playerManager.sendPacket(it, blood) }
    }

    override fun mostrarTrailFisico(player: Player) {}

    private fun dibujarEstrella(player: Player, color: Color, radio: Double, puntas: Int) {
        val loc = player.location.add(0.0, 0.1, 0.0)
        val dust = org.bukkit.Particle.DustOptions(color, 1.0f)
        for (i in 0 until puntas) {
            val a = i * Math.PI * 2 / puntas
            val na = (i + 2) * Math.PI * 2 / puntas
            val p1 = loc.clone().add(cos(a) * radio, 0.0, sin(a) * radio)
            val p2 = loc.clone().add(cos(na) * radio, 0.0, sin(na) * radio)
            val dir = p2.toVector().subtract(p1.toVector())
            val len = dir.length(); dir.normalize()
            var d = 0.0

            plugin.server.regionScheduler.run(plugin, loc, Consumer { _ ->
                while (d < len) {
                    player.world.spawnParticle(org.bukkit.Particle.DUST, p1.clone().add(dir.clone().multiply(d)), 1, dust)
                    d += 0.3
                }
            })
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let { attackSoundsQueue.remove(it.uniqueId) }
        temporaryEntities.forEach { it.remove() }
        temporaryEntities.clear()
    }
}
