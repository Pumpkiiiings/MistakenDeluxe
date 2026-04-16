package liric.mistaken.roles.supervivientes.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.supervivientes.Superviviente
import liric.mistaken.utils.hooks.CraftEngineHook
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
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
 * FIX: Adaptado a Entity Schedulers (Folia Ready).
 */
class Aldeano : Superviviente(
    "aldeano",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.aldeano.nombre", "Aldeano", "supervivientes_info")
) {

    private val pathBase = "supervivientes.aldeano"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    val EMERALD_KEY = NamespacedKey("mistaken", "villager_emerald")

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientes()

        listOf("habilidad1", "habilidad2", "habilidad3").forEach { key ->
            config.getString("$pathBase.items.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngineHook.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.EMERALD)
                    itemCache[key] = item
                }
            }
        }

        val armorParts = mapOf(
            "casco" to Material.LEATHER_HELMET,
            "pechera" to Material.LEATHER_CHESTPLATE,
            "pantalones" to Material.LEATHER_LEGGINGS,
            "botas" to Material.LEATHER_BOOTS
        )

        armorParts.forEach { (key, fallbackMat) ->
            val id = config.getString("$pathBase.armadura.$key")
            if (id != null && id != "none" && id.isNotEmpty()) {
                val item = CraftEngineHook.getCustomItem(id)
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

    private fun sendAbilityMessage(player: Player, lang: FileConfiguration, mech: FileConfiguration, key: String) {
        player.scheduler.run(plugin, { _ ->
            val msg = lang.getString("$pathBase.habilidades_mensajes.$key")
            if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))

            val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_VILLAGER_YES")
            runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
        }, null)
    }

    override fun equipar(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val inv = player.inventory
            inv.clear()

            if (itemCache.isEmpty()) preLoadKit()

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
        }, null)
    }

    private fun usarPanico(player: Player) {
        player.scheduler.run(plugin, { _ ->
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 2))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            player.world.spawnParticle(Particle.ANGRY_VILLAGER, player.location.add(0.0, 2.0, 0.0), 5)
        }, null)
    }

    private fun lanzarSoborno(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val item = itemCache["habilidad2"] ?: ItemStack(Material.EMERALD)
            val proj = player.launchProjectile(Snowball::class.java)
            proj.item = item
            proj.persistentDataContainer.set(EMERALD_KEY, PersistentDataType.BYTE, 1.toByte())

            player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 1f)
        }, null)
    }

    private fun invocarGolem(player: Player) {
        player.scheduler.run(plugin, { _ ->
            player.world.playSound(player.location, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f)
            player.world.spawnParticle(Particle.BLOCK, player.location, 30, 2.0, 0.5, 2.0, Material.IRON_BLOCK.createBlockData())

            player.world.getNearbyPlayers(player.location, 5.0).forEach { victim ->
                val session = plugin.sessionManager?.getSession(victim)
                if (session?.esAsesino(victim.uniqueId) == true) {
                    victim.scheduler.run(plugin, { _ ->
                        val knockback = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(2.5).setY(0.5)
                        victim.velocity = knockback

                        victim.playSound(victim.location, Sound.ENTITY_IRON_GOLEM_HURT, 1f, 1f)
                        victim.sendMessage(mm.deserialize("<red><b>[!]</b> ¡El Golem te ha rechazado!"))
                    }, null)
                }
            }
        }, null)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.scheduler?.run(plugin, { _ ->
            player.removePotionEffect(PotionEffectType.SPEED)
        }, null)
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}
