package liric.mistaken.supervivientes.clases

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Notch: El Creador.
 * MECÁNICAS:
 * - Movilidad vertical (Vuelo falso).
 * - Control de área (Empuje de Bedrock).
 * - Supervivencia (Manzana de Notch).
 */
class Notch : Superviviente(
    "notch",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.notch.nombre", "Notch", "supervivientes_info")
) {

    private val pathBase = "supervivientes.notch"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientes()

        // 1. Habilidades
        listOf("habilidad1", "habilidad2", "habilidad3").forEach { key ->
            config.getString("$pathBase.items.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.GOLDEN_APPLE)
                    itemCache[key] = item
                }
            }
        }

        // 2. Armadura (Por defecto Oro/Diamante si no hay config)
        val armorParts = mapOf(
            "casco" to Material.GOLDEN_HELMET,
            "pechera" to Material.GOLDEN_CHESTPLATE,
            "pantalones" to Material.GOLDEN_LEGGINGS,
            "botas" to Material.GOLDEN_BOOTS
        )

        armorParts.forEach { (key, fallbackMat) ->
            val id = config.getString("$pathBase.armadura.$key")
            if (id != null && id != "none" && id.isNotEmpty()) {
                val item = CraftEngineUtils.getCustomItem(id)
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
                usarSaltoCreativo(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("$pathBase.items.habilidad2_cooldown", 30))) {
                usarMuroAdmin(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 60))) {
                usarManzanaCreador(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        val msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemCache.isEmpty()) preLoadKit()

        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return
            langConfig.getString("$pathBase.habilidades_nombres.$key")?.let {
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

    // --- H1: SALTO CREATIVO (Vuelo temporal) ---
    private fun usarSaltoCreativo(player: Player) {
        // Impulso físico
        val velocity = player.location.direction.multiply(1.2).setY(1.1) // Salto alto y hacia adelante
        player.velocity = velocity

        // SFX Combinado: Cohete + Experiencia (Sonido "Mágico")
        player.world.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.8f)
        player.world.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f)

        // Partículas: Nubes debajo de los pies
        player.world.spawnParticle(Particle.CLOUD, player.location, 15, 0.3, 0.1, 0.3, 0.05)
        player.world.spawnParticle(Particle.WAX_OFF, player.location, 10, 0.5, 0.5, 0.5) // Destellos blancos

        // Caída lenta para simular "Creative Mode" al bajar
        val task = player.scheduler.runDelayed(plugin, {
            if (player.isOnline) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0))
            }
        }, null, 15L)

        task?.let { activeTasks.add(it) }
    }

    // --- H2: MURO DEL ADMIN (Empuje de Bedrock) ---
    private fun usarMuroAdmin(player: Player) {
        // SFX Combinado: Yunque + Pistón (Sonido "Pesado/Denegado")
        player.world.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f)
        player.world.playSound(player.location, Sound.BLOCK_PISTON_EXTEND, 1.0f, 0.5f)

        // VFX: Círculo de Bedrock y Runas
        player.world.spawnParticle(Particle.BLOCK, player.location.add(0.0, 1.0, 0.0), 40, 3.0, 0.5, 3.0, Material.BEDROCK.createBlockData())
        player.world.spawnParticle(Particle.ENCHANT, player.location.add(0.0, 1.0, 0.0), 30, 2.0, 2.0, 2.0, 1.0)

        // Lógica de empuje
        player.getNearbyEntities(6.0, 6.0, 6.0).forEach { entity ->
            if (entity is Player && plugin.sessionManager.getSession(entity)?.esAsesino(entity.uniqueId) == true) {
                // Vector de rechazo fuerte
                val push = entity.location.toVector().subtract(player.location.toVector()).normalize().multiply(2.5).setY(0.4)
                entity.velocity = push

                // Efectos al asesino
                entity.sendMessage(mm.deserialize("<red><bold>DENIED!</bold> <gray>Acceso denegado por el administrador.</gray>"))
                entity.playSound(entity.location, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f)
            }
        }
    }

    // --- H3: MANZANA DEL CREADOR (Buffs Epicos) ---
    private fun usarManzanaCreador(player: Player) {
        // SFX Combinado: Tótem + Comer
        player.world.playSound(player.location, Sound.ITEM_TOTEM_USE, 1.0f, 1.2f)
        player.world.playSound(player.location, Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f)

        // VFX: Explosión de Tótem + Flash
        player.world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.location.add(0.0, 1.0, 0.0), 40, 0.5, 0.5, 0.5, 0.3)
        player.world.spawnParticle(Particle.FLASH, player.location.add(0.0, 1.0, 0.0), 1)

        // Buffs Poderosos
        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 100, 2)) // Regen III por 5s
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 100, 1))   // Resistencia II por 5s
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))        // Velocidad II por 5s

        // Curación instantánea visual
        val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = (player.health + 6.0).coerceAtMost(maxHealth) // Cura 3 corazones
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}
