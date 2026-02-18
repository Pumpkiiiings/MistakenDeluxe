package liric.mistaken.supervivientes.clases

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.Superviviente
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * [LIRIC-MISTAKEN 2.0]
 * Civil: La clase balanceada para el pueblo.
 * Optimización: Timers de habilidades con Coroutines (Zero-Lag).
 */
class Civil : Superviviente("civil", "Civil") {

    private val pathBase = "supervivientes.civil.items"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun equipar(player: Player) {
        player.inventory.clear()
        val section = plugin.configManager.getSupervivientes().getConfigurationSection(pathBase) ?: return

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
        val section = plugin.configManager.getSupervivientes().getConfigurationSection(pathBase) ?: return

        val key = "habilidad${slot + 1}"
        val cooldownSecs = section.getInt("${key}_cooldown", 30)

        if (checkCooldown(player, slot, cooldownSecs)) return

        // --- MENSAJE Y SONIDO DINÁMICO ---
        val mensaje = section.getString("${key}_mensaje")
        if (!mensaje.isNullOrEmpty()) {
            val prefix = plugin.config.getString("settings.prefix", "<red>Mistaken <dark_gray>» ")
            player.sendMessage(mm.deserialize(mensaje.replace("<prefix>", prefix!!)))
        }

        val soundName = section.getString("${key}_sonido", "UI_BUTTON_CLICK")
        try {
            player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f)
        } catch (ignored: Exception) {}

        when (slot) {
            0 -> usarAdrenalina(player)
            1 -> usarInvisibilidad(player)
            2 -> lanzarRoca(player)
        }
    }

    private fun usarAdrenalina(player: Player) {
        // Efecto inicial: Velocidad II por 5s (100 ticks)
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))

        // Timer asíncrono para el bajón de energía
        val job = scope.launch {
            delay(5000) // 5 segundos
            withContext(Dispatchers.Main) {
                if (player.isOnline) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                }
            }
        }
        trackJob(job)
    }

    private fun usarInvisibilidad(player: Player) {
        // Invisibilidad por 5s
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false, true))

        val job = scope.launch {
            delay(5000)
            withContext(Dispatchers.Main) {
                if (player.isOnline) {
                    val section = plugin.configManager.getSupervivientes().getConfigurationSection(pathBase)
                    val msgFin = section?.getString("habilidad2_mensaje_fin")

                    if (!msgFin.isNullOrEmpty()) {
                        val prefix = plugin.config.getString("settings.prefix", "<red>Mistaken <dark_gray>» ")
                        player.sendMessage(mm.deserialize(msgFin.replace("<prefix>", prefix!!)))
                    }
                }
            }
        }
        trackJob(job)
    }

    private fun lanzarRoca(player: Player) {
        // Lanzamos el proyectil con la metadata que checa el Listener
        player.launchProjectile(Snowball::class.java).apply {
            setMetadata("mistaken_roca", FixedMetadataValue(plugin, true))
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
        // Cancelar todas las corrutinas de adrenalina/invisibilidad pendientes
        scope.coroutineContext.cancelChildren()
    }
}
