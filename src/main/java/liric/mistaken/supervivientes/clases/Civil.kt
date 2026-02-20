package liric.mistaken.supervivientes.clases

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.Superviviente
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

/**
 * [LIRIC-MISTAKEN 2.0]
 * Civil: La clase balanceada y versátil.
 *
 * MEJORAS:
 * - Uso de PDC para la Roca (Adiós Metadata).
 * - Sincronización refinada con el MainThread.
 * - Cache de mensajes para evitar lecturas de disco en delays.
 */
class Civil : Superviviente("civil", "Civil") {

    private val pathBase = "supervivientes.civil.items"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Llave única para identificar la roca de forma ultra-rápida
    private val rocaKey = NamespacedKey("mistaken", "roca")

    override fun equipar(player: Player) {
        player.inventory.clear()
        val config = plugin.configManager.getSupervivientes()
        val section = config.getConfigurationSection(pathBase) ?: return

        for (i in 1..3) {
            val matName = section.getString("habilidad$i", "BARRIER")
            val displayName = section.getString("habilidad${i}_nombre", "<red>Habilidad $i")
            val mat = Material.matchMaterial(matName ?: "BARRIER") ?: Material.BARRIER

            player.inventory.setItem(i - 1, createItem(mat, displayName!!))
        }

        val prefix = plugin.config.getString("settings.prefix", "<red>Mistaken <dark_gray>» ")
        player.sendMessage(mm.deserialize("$prefix<gray>Has sido equipado como: <white>$nombre"))
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val config = plugin.configManager.getSupervivientes()
        val section = config.getConfigurationSection(pathBase) ?: return

        val key = "habilidad${slot + 1}"
        val cooldownSecs = section.getInt("${key}_cooldown", 30)

        if (checkCooldown(player, slot, cooldownSecs)) return

        // --- FEEDBACK INMEDIATO ---
        val mensaje = section.getString("${key}_mensaje")
        if (!mensaje.isNullOrEmpty()) {
            val prefix = plugin.config.getString("settings.prefix", "<red>Mistaken <dark_gray>» ")
            player.sendMessage(mm.deserialize(mensaje.replace("<prefix>", prefix!!)))
        }

        val soundName = section.getString("${key}_sonido", "UI_BUTTON_CLICK")
        runCatching {
            player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f)
        }

        when (slot) {
            0 -> usarAdrenalina(player)
            1 -> usarInvisibilidad(player, section.getString("habilidad2_mensaje_fin"))
            2 -> lanzarRoca(player)
        }
    }

    private fun usarAdrenalina(player: Player) {
        // Boost de velocidad
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))

        val job = scope.launch {
            delay(5000) // 5 segundos de adrenalina

            // Regresamos al hilo de Bukkit para aplicar el "bajón"
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
        // Aplicar invisibilidad sin partículas para ser sigiloso
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false, false))

        val job = scope.launch {
            delay(5000)

            withContext(plugin.mainThread) {
                if (player.isOnline) {
                    if (!mensajeFin.isNullOrEmpty()) {
                        val prefix = plugin.config.getString("settings.prefix", "<red>Mistaken <dark_gray>» ")
                        player.sendMessage(mm.deserialize(mensajeFin.replace("<prefix>", prefix!!)))
                    }
                    player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f)
                }
            }
        }
        trackJob(job)
    }

    private fun lanzarRoca(player: Player) {
        // Usamos PDC (Persistent Data Container) de Paper.
        // Es mucho más eficiente que la Metadata vieja de Bukkit.
        player.launchProjectile(Snowball::class.java).apply {
            persistentDataContainer.set(rocaKey, PersistentDataType.BYTE, 1.toByte())
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.5f)
    }

    private fun createItem(mat: Material, name: String): ItemStack {
        return ItemStack(mat).apply {
            editMeta { it.displayName(mm.deserialize(name)) }
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        // Detenemos los procesos de adrenalina o invisibilidad si el jugador muere
        scope.coroutineContext.cancelChildren()
    }
}
