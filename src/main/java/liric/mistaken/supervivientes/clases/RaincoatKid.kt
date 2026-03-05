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
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Raincoat Kid: El niño del impermeable.
 * CARACTERÍSTICAS:
 * - Escala: 0.8 (Más pequeño, hitbox reducida).
 * - Mecánica: Velocidad y Stun, pero con penalización de cansancio.
 */
class RaincoatKid : Superviviente(
    "raincoatkid",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.raincoatkid.nombre", "Raincoat Kid", "supervivientes_info")
) {

    private val pathBase = "supervivientes.raincoatkid"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    // Llave para identificar el palo aturdidor en los eventos de daño
    private val STICK_KEY = NamespacedKey("mistaken", "kid_stick")

    init {
        preLoadKit()
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientes()
        listOf("habilidad1", "habilidad2", "habilidad3").forEach { key ->
            config.getString("$pathBase.items.$key")?.let { id ->
                if (id != "none" && id.isNotEmpty()) {
                    val item = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id) ?: Material.STICK)
                    itemCache[key] = item
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 25))) {
                usarSprint(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("$pathBase.items.habilidad2_cooldown", 15))) {
                usarDash(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad2")
            }
            2 -> {
                // La habilidad 3 es un objeto de golpe (Palo), no de click derecho.
                // Aquí solo enviamos mensaje si intentan clickearlo, o lo dejamos vacío.
            }
        }
    }

    private fun sendAbilityMessage(
        player: Player,
        lang: org.bukkit.configuration.file.FileConfiguration,
        mech: org.bukkit.configuration.file.FileConfiguration,
        key: String
    ) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) {
            player.sendMessage(mm.deserialize(msg))
        }
        val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_BAT_TAKEOFF")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        if (itemCache.isEmpty()) preLoadKit()

        // 🔥 APLICAR ESCALA DE NIÑO (0.8)
        player.getAttribute(Attribute.SCALE)?.baseValue = 0.8

        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return

            // Si es la habilidad 3 (Palo), le ponemos la marca PDC para detectar el golpe
            if (key == "habilidad3") {
                val meta = item.itemMeta
                meta.persistentDataContainer.set(STICK_KEY, PersistentDataType.BYTE, 1.toByte())
                item.itemMeta = meta
            }

            langConfig.getString("$pathBase.habilidades_nombres.$key")?.let {
                item.editMeta { m -> m.displayName(mm.deserialize(it)) }
            }
            inv.setItem(slot, item)
        }

        giveLocalizedSkill(0, "habilidad1")
        giveLocalizedSkill(1, "habilidad2")
        giveLocalizedSkill(2, "habilidad3")

        player.updateInventory()
    }

    // --- H1: SPRINT (Correr rápido + Cansancio posterior) ---
    private fun usarSprint(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 2)) // Speed III por 5s
        player.world.spawnParticle(org.bukkit.Particle.CLOUD, player.location, 5, 0.2, 0.1, 0.2, 0.05)

        // Tarea para aplicar el Debuff de cansancio al terminar
        val task = player.scheduler.runDelayed(plugin, {
            if (player.isOnline) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0)) // Slowness I por 3s
                player.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 1f, 0.8f)
                player.sendActionBar(mm.deserialize("<red><i>*jadeo*</i>"))
            }
        }, null, 100L) // 5 segundos después

        task?.let { activeTasks.add(it) }
    }

    // --- H2: DASH (Impulso + Cansancio inmediato) ---
    private fun usarDash(player: Player) {
        val dir = player.location.direction.normalize().multiply(1.8).setY(0.4)
        player.velocity = dir
        player.playSound(player.location, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f)

        // Debuff inmediato por el esfuerzo
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0)) // Slowness I por 3s
    }

    // --- H3: LOGICA DEL PALO (Para llamar desde el Listener) ---
    fun aplicarGolpePalo(victim: Player) {
        // Efecto al Asesino: Lentitud 3 y Ceguera por 5s
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 2))
        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0))
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1f, 0.5f)
        victim.world.spawnParticle(org.bukkit.Particle.CRIT, victim.eyeLocation, 10)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            // RESTAURAR TAMAÑO ORIGINAL
            it.getAttribute(Attribute.SCALE)?.baseValue = 1.0
            it.removePotionEffect(PotionEffectType.SPEED)
            it.removePotionEffect(PotionEffectType.SLOWNESS)
        }
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()
    }
}
