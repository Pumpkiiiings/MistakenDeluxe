package liric.mistaken.supervivientes.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
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
 * FIX: Sistema de Equipamiento igual al del Asesino Slasher (100% CraftEngine).
 */
class KasaneTeto : Superviviente(
    "teto",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.teto.nombre", "Kasane Teto", "supervivientes_info")
) {

    private val pathBase = "supervivientes.teto"
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

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

        val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_PLAYER_ATTACK_SWEEP")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    // --- 🛠️ EQUIPAMIENTO (ESTILO SLASHER) ---
    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        player.getAttribute(Attribute.SCALE)?.baseValue = 0.8861

        val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
        val configMecanica = plugin.configManager.getSupervivientes() // El global supervivientes.yml

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
            else configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            // Intentamos sacar el ítem de CraftEngine
            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            // Si son baguettes, les ponemos marcas PDC
            val meta = item.itemMeta
            if (key == "habilidad2") meta.persistentDataContainer.set(MELEE_BAGUETTE_KEY, PersistentDataType.BYTE, 1.toByte())

            // Le ponemos el nombre
            val namePath = "$pathBase.habilidades_nombres.$key"
            langInfo.getString(namePath)?.let {
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

        deliver("casco", 0, true)
        deliver("pechera", 0, true)
        deliver("pantalones", 0, true)
        deliver("botas", 0, true)

        deliver("habilidad1", 0)
        deliver("habilidad2", 1)
        deliver("habilidad3", 2)

        player.updateInventory()
    }

    // --- H1: DASH (Con Debuff) ---
    private fun usarDash(player: Player) {
        val dir = player.location.direction.normalize().multiply(2.0).setY(0.3)
        player.velocity = dir
        player.world.spawnParticle(org.bukkit.Particle.CRIT, player.location, 10, 0.2, 0.2, 0.2, 0.1)
        aplicarDebuff(player)
    }

    // --- H2: MELEE BAGUETTE ---
    fun aplicarGolpeBaguette(victim: Player, attacker: Player) {
        val knockback = victim.location.toVector().subtract(attacker.location.toVector()).normalize().multiply(3.5).setY(0.6)
        victim.velocity = knockback

        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0)) // 5s
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 0))  // 5s
        victim.world.playSound(victim.location, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f)

        aplicarDebuff(attacker)
    }

    // --- H3: BAGUETTE LANZABLE ---
    private fun lanzarBaguette(player: Player) {
        // Al lanzarla, necesitamos crear un pan temporal porque ya no usamos la caché vieja
        val projItem = CraftEngineUtils.getCustomItem(plugin.configManager.getSupervivientes().getString("$pathBase.items.habilidad3")) ?: ItemStack(Material.BREAD)

        val projectile = player.launchProjectile(Snowball::class.java)
        projectile.item = projItem
        projectile.persistentDataContainer.set(THROW_BAGUETTE_KEY, PersistentDataType.BYTE, 1.toByte())

        player.playSound(player.location, Sound.ENTITY_EGG_THROW, 1f, 0.8f)
        aplicarDebuff(player)
    }

    private fun aplicarDebuff(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
        player.sendActionBar(mm.deserialize("<red><i>¡Sobrecarga sensorial!</i>"))
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.removePotionEffect(PotionEffectType.BLINDNESS)
        player?.getAttribute(Attribute.SCALE)?.baseValue = 1.0
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}
