package liric.mistaken.roles.supervivientes.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.supervivientes.Superviviente
import liric.mistaken.utils.hooks.CraftEngine
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Aldeano: Clase de evasión y distracción.
 * MECÁNICAS:
 * - Pánico (Velocidad).
 * - Soborno (Proyectil aturdidor).
 * - Golem (Empuje en área).
 */
class Aldeano : Superviviente(
    "aldeano",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.aldeano.nombre", "Aldeano", "supervivientes_info")
) {

    private val pathBase = "supervivientes.aldeano"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    // Llave para la esmeralda lanzable
    val EMERALD_KEY = NamespacedKey("mistaken", "villager_emerald")

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientes()

        // 1. Cargar Habilidades
        listOf("habilidad1", "habilidad2", "habilidad3").forEach { key ->
            config.getString("$pathBase.items.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.EMERALD)
                    itemCache[key] = item
                }
            }
        }

        // 2. Cargar Armadura
        val armorParts = mapOf(
            "casco" to Material.LEATHER_HELMET,
            "pechera" to Material.LEATHER_CHESTPLATE,
            "pantalones" to Material.LEATHER_LEGGINGS,
            "botas" to Material.LEATHER_BOOTS
        )

        armorParts.forEach { (key, fallbackMat) ->
            val id = config.getString("$pathBase.armadura.$key")
            if (id != null && id != "none" && id.isNotEmpty()) {
                val item = CraftEngine.getCustomItem(id)
                if (item != null) {
                    itemCache[key] = item
                } else {
                    val cleanId = id.replace(".*:".toRegex(), "").uppercase()
                    val mat = Material.matchMaterial(cleanId) ?: fallbackMat
                    itemCache[key] = ItemStack(mat)
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 20))) {
                usarPanico(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("$pathBase.items.habilidad2_cooldown", 15))) {
                lanzarSoborno(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 40))) {
                invocarGolem(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))

        // Sonido por defecto "Hrmm"
        val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_VILLAGER_YES")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Recarga segura
        preLoadKit()

        val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return
            langInfo.getString("$pathBase.habilidades_nombres.$key")?.let {
                item.editMeta { m -> m.displayName(mm.deserialize(it)) }
            }
            inv.setItem(slot, item)
        }

        giveLocalizedSkill(0, "habilidad1")
        giveLocalizedSkill(1, "habilidad2")
        giveLocalizedSkill(2, "habilidad3")

        itemCache["casco"]?.let { inv.helmet = it }
        itemCache["pechera"]?.let { inv.chestplate = it }
        itemCache["pantalones"]?.let { inv.leggings = it }
        itemCache["botas"]?.let { inv.boots = it }

        player.updateInventory()
    }

    // --- H1: PÁNICO (Velocidad Explosiva) ---
    private fun usarPanico(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 2)) // Speed III por 3s
        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
        player.world.spawnParticle(Particle.ANGRY_VILLAGER, player.location.add(0.0, 2.0, 0.0), 5)
    }

    // --- H2: SOBORNO (Proyectil Esmeralda) ---
    private fun lanzarSoborno(player: Player) {
        val item = itemCache["habilidad2"] ?: ItemStack(Material.EMERALD)

        val proj = player.launchProjectile(Snowball::class.java)
        proj.item = item
        proj.persistentDataContainer.set(EMERALD_KEY, PersistentDataType.BYTE, 1.toByte())

        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 1f)
    }

    // --- H3: AYUDA DEL GOLEM (Onda de Choque) ---
    private fun invocarGolem(player: Player) {
        player.world.playSound(player.location, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f)
        player.world.spawnParticle(Particle.BLOCK_CRUMBLE, player.location, 30, 2.0, 0.5, 2.0, Material.IRON_BLOCK.createBlockData())

        // Empujar al asesino si está cerca (5 bloques)
        player.world.getNearbyPlayers(player.location, 5.0).forEach { victim ->
            val session = plugin.sessionManager.getSession(victim)
            if (session?.esAsesino(victim.uniqueId) == true) {
                // Vector de empuje fuerte hacia atrás
                val knockback = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(2.5).setY(0.5)
                victim.velocity = knockback

                victim.playSound(victim.location, Sound.ENTITY_IRON_GOLEM_HURT, 1f, 1f)
                victim.sendMessage(mm.deserialize("<red><b>[!]</b> ¡El Golem te ha rechazado!"))
            }
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.removePotionEffect(PotionEffectType.SPEED)
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}
