package liric.mistaken.roles.survivors.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.Survivor
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
 * Villager: Clase de evasión y distracción.
 * MECÁNICAS:
 * - Pánico (Velocidad).
 * - Soborno (Proyectil aturdidor).
 * - Golem (Empuje en área).
 */
class Villager : Survivor(
    "aldeano",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "supervivientes.aldeano.nombre", "survivors_info")
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
        val config = plugin.configManager.getSurvivorConfig(this.id)

        // 1. Cargar Habilidades
        listOf("skill1", "skill2", "skill3").forEach { key ->
            config.getString("items.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngine.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.EMERALD)
                    itemCache[key] = item
                }
            }
        }

        // 2. Cargar Armadura
        val armorParts = mapOf(
            "helmet" to Material.LEATHER_HELMET,
            "chestplate" to Material.LEATHER_CHESTPLATE,
            "leggings" to Material.LEATHER_LEGGINGS,
            "boots" to Material.LEATHER_BOOTS
        )

        armorParts.forEach { (key, fallbackMat) ->
            val id = config.getString("armor.$key")
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

    override fun useSkill(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSurvivorConfig(this.id)
        val langConfig = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 20))) {
                usarPanico(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 15))) {
                lanzarSoborno(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("items.skill3_cooldown", 40))) {
                invocarGolem(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) player.sendMessage(pumpking.lib.color.ColorTranslator.translate(msg))

        // Sonido por defecto "Hrmm"
        val soundName = mech.getString("$pathBase.items.${key}_sound", "ENTITY_VILLAGER_YES")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Recarga segura
        preLoadKit()

        val langInfo = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return
            langInfo.getString("$pathBase.skill_names.$key")?.let {
                item.editMeta { m -> m.displayName(pumpking.lib.color.ColorTranslator.translate(it)) }
            }
            inv.setItem(slot, item)
        }

        giveLocalizedSkill(0, "skill1")
        giveLocalizedSkill(1, "skill2")
        giveLocalizedSkill(2, "skill3")

        itemCache["helmet"]?.let { inv.helmet = it }
        itemCache["chestplate"]?.let { inv.chestplate = it }
        itemCache["leggings"]?.let { inv.leggings = it }
        itemCache["boots"]?.let { inv.boots = it }

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
        val item = itemCache["skill2"] ?: ItemStack(Material.EMERALD)

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
            if (session?.isKiller(victim.uniqueId) == true) {
                // Vector de empuje fuerte hacia atrás
                val knockback = victim.location.toVector().subtract(player.location.toVector()).normalize().multiply(2.5).setY(0.5)
                victim.velocity = knockback

                victim.playSound(victim.location, Sound.ENTITY_IRON_GOLEM_HURT, 1f, 1f)
                victim.sendMessage(pumpking.lib.color.ColorTranslator.translate("<red><b>[!]</b> ¡El Golem te ha rechazado!"))
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





