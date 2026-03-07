package liric.mistaken.supervivientes.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Kasane Teto: La Quimera.
 * FIX: Removidos los debuffs de usuario y añadido Dash Sonoro + Partículas Custom.
 */
class KasaneTeto : Superviviente(
    "teto",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.teto.nombre", "Kasane Teto", "supervivientes_info")
) {

    private val pathBase = "supervivientes.teto"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val MELEE_BAGUETTE_KEY = NamespacedKey("mistaken", "teto_melee")
    val THROW_BAGUETTE_KEY = NamespacedKey("mistaken", "teto_throw")

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 15))) {
                usarDash(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> { /* Melee */ }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 25))) {
                lanzarBaguette(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        player.getAttribute(Attribute.SCALE)?.baseValue = 0.8861

        val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
        val configMecanica = plugin.configManager.getSupervivientes()

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
            else configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val meta = item.itemMeta
            if (key == "habilidad2") meta.persistentDataContainer.set(MELEE_BAGUETTE_KEY, PersistentDataType.BYTE, 1.toByte())

            langInfo.getString("$pathBase.habilidades_nombres.$key")?.let {
                meta.displayName(mm.deserialize(it))
            }
            item.itemMeta = meta

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
        deliver("habilidad1", 0); deliver("habilidad2", 1); deliver("habilidad3", 2)

        player.updateInventory()
    }

    // --- H1: DASH TÁCTICO SONORO ---
    private fun usarDash(player: Player) {
        val dir = player.location.direction.normalize().multiply(2.0).setY(0.3)
        player.velocity = dir

        // Partículas iniciales
        player.world.spawnParticle(org.bukkit.Particle.CRIT, player.location, 10, 0.2, 0.2, 0.2, 0.1)

        // Tarea asíncrona para sonido repetitivo durante el vuelo
        val job = scope.launch {
            var count = 0
            while (isActive && count < 8 && player.isOnline) { // 8 ticks de vuelo aprox
                withContext(plugin.bukkitDispatcher) {
                    player.world.playSound(player.location, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.8f, 1.5f)

                    // Rastro rosa de Teto
                    val pos = Vector3d(player.location.x, player.location.y + 0.5, player.location.z)
                    val particle = WrapperPlayServerParticle(Particle(ParticleTypes.DUST, ParticleDustData(1f, 0.4f, 0.8f, 1f)), false, pos, Vector3f(0.2f, 0.2f, 0.2f), 0.01f, 5)
                    PacketEvents.getAPI().playerManager.sendPacket(player, particle)
                }
                delay(50)
                count++
            }
        }
        trackJob(job)
    }

    // --- H2: MELEE BAGUETTE ---
    fun aplicarGolpeBaguette(victim: Player, attacker: Player) {
        val knockback = victim.location.toVector().subtract(attacker.location.toVector()).normalize().multiply(3.5).setY(0.6)
        victim.velocity = knockback

        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 0))
        victim.world.playSound(victim.location, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f)

        // Efecto visual al golpear
        victim.world.spawnParticle(org.bukkit.Particle.EXPLOSION, victim.eyeLocation, 1)
    }

    // --- H3: BAGUETTE LANZABLE ---
    private fun lanzarBaguette(player: Player) {
        val projId = plugin.configManager.getSupervivientes().getString("$pathBase.items.habilidad3")
        val projItem = CraftEngineUtils.getCustomItem(projId) ?: ItemStack(Material.BREAD)

        val projectile = player.launchProjectile(Snowball::class.java)
        projectile.item = projItem
        projectile.persistentDataContainer.set(THROW_BAGUETTE_KEY, PersistentDataType.BYTE, 1.toByte())

        player.playSound(player.location, Sound.ENTITY_EGG_THROW, 1f, 0.8f)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.getAttribute(Attribute.SCALE)?.baseValue = 1.0
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
        scope.coroutineContext.cancelChildren()
    }
}
