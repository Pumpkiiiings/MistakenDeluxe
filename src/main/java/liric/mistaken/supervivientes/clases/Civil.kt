package liric.mistaken.supervivientes.clases

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.mainThread
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Civil: La clase balanceada y versátil.
 *
 * FIXES:
 * - Error de inferencia en la creación de ItemStack corregido.
 * - Soporte Multi-Idioma real (Nombres de ítems traducidos por jugador).
 * - Sincronización de hilos segura para Paper 1.21.4.
 */
class Civil : Superviviente(
    "civil",
    Mistaken.instance.messageConfig.getSpecificFile(null, "supervivientes").getString("supervivientes.civil.nombre", "Civil")!!
) {

    private val pathBase = "supervivientes.civil.items"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Llave única para identificar la roca de forma ultra-rápida en el Listener
    private val rocaKey = NamespacedKey("mistaken", "roca")

    init {
        preLoadKit()
    }

    /**
     * 🔥 PRE-LOAD LÓGICO (Corregido):
     * Carga los materiales base del archivo supervivientes.yml de la RAIZ.
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientesConfig(null)
        val skillKeys = listOf("habilidad1", "habilidad2", "habilidad3")

        skillKeys.forEach { key ->
            val id = config.getString("supervivientes.civil.items.$key")

            if (id != null && id != "none" && id.isNotEmpty()) {
                // --- FIX DE ERROR DE TIPOS ---
                // Separamos la lógica para que Kotlin 2.1 no se confunda con el ItemStack
                val customItem = CraftEngineUtils.getCustomItem(id)

                val item = if (customItem != null) {
                    customItem
                } else {
                    val mat = Material.matchMaterial(id) ?: Material.PAPER
                    ItemStack(mat)
                }

                itemCache[key] = item
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes")

        // Mapeo: Tecla 1 (Slot 0) a Tecla 3 (Slot 2)
        when (slot) {
            0 -> if (!checkCooldown(player, 0, langConfig.getInt("supervivientes.civil.items.habilidad1_cooldown", 30))) {
                usarAdrenalina(player)
                sendAbilityMessage(player, langConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, langConfig.getInt("supervivientes.civil.items.habilidad2_cooldown", 45))) {
                usarInvisibilidad(player, langConfig.getString("supervivientes.civil.items.habilidad2_mensaje_fin"))
                sendAbilityMessage(player, langConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, langConfig.getInt("supervivientes.civil.items.habilidad3_cooldown", 20))) {
                lanzarRoca(player)
                sendAbilityMessage(player, langConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, key: String) {
        val msg = lang.getString("supervivientes.civil.items.${key}_mensaje")
        if (!msg.isNullOrEmpty()) {
            player.sendMessage(mm.deserialize(msg))
        }
        val soundName = lang.getString("supervivientes.civil.items.${key}_sonido", "UI_BUTTON_CLICK")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    // --- 🛠️ EQUIPAMIENTO (CON TRADUCCIÓN AL VUELO) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        // Si el caché falló por timing de carga, reintentamos
        if (itemCache.isEmpty()) preLoadKit()

        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return

            // 🔥 Buscamos el nombre traducido en la carpeta del idioma del jugador
            val name = langConfig.getString("supervivientes.civil.items.${key}_nombre")

            if (name != null) {
                item.editMeta { it.displayName(mm.deserialize(name)) }
            }
            inv.setItem(slot, item)
        }

        // Entregar las 3 habilidades en slots 0, 1, 2
        giveLocalizedSkill(0, "habilidad1")
        giveLocalizedSkill(1, "habilidad2")
        giveLocalizedSkill(2, "habilidad3")

        player.sendMessage(plugin.messageConfig.getMessage(player, "supervivientes.civil.equip-msg"))
        player.updateInventory()
    }

    // --- ⚡ LÓGICA DE HABILIDADES ---

    private fun usarAdrenalina(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))
        val job = scope.launch {
            delay(5000) // 5 segundos de adrenalina
            withContext(plugin.mainThread) {
                if (player.isOnline) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                    player.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 0.8f, 1.2f)
                }
            }
        }
        trackJob(job)
    }

    private fun usarInvisibilidad(player: Player, mensajeFin: String?) {
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false, false))
        val job = scope.launch {
            delay(5000)
            withContext(plugin.mainThread) {
                if (player.isOnline) {
                    mensajeFin?.let { player.sendMessage(mm.deserialize(it)) }
                    player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f)
                }
            }
        }
        trackJob(job)
    }

    private fun lanzarRoca(player: Player) {
        player.launchProjectile(Snowball::class.java).apply {
            persistentDataContainer.set(rocaKey, PersistentDataType.BYTE, 1.toByte())
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.5f)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        // Reset de estado visual al limpiar
        player?.let {
            it.removePotionEffect(PotionEffectType.DARKNESS)
            it.isSwimming = false
        }
        scope.coroutineContext.cancelChildren()
    }
}
