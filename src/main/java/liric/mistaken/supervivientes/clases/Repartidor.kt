package liric.mistaken.supervivientes.clases

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.Superviviente
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * [LIRIC-MISTAKEN 2.0]
 * Repartidor: Soporte táctico y control de área.
 * Optimización: Manejo de bloques con Metadata temporal y Coroutines para efectos secundarios.
 */
class Repartidor : Superviviente("repartidor", "Repartidor") {

    private val pathBase = "supervivientes.repartidor.items"
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
        player.sendMessage(mm.deserialize("$prefix<gray>¡Tu pedido ha llegado! Clase: <gold>$nombre"))
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val section = plugin.configManager.getSupervivientes().getConfigurationSection(pathBase) ?: return

        val key = "habilidad${slot + 1}"
        val cooldownSecs = section.getInt("${key}_cooldown", 30)

        if (checkCooldown(player, slot, cooldownSecs)) return

        // --- MENSAJE DINÁMICO ---
        val mensaje = section.getString("${key}_mensaje")
        if (!mensaje.isNullOrEmpty()) {
            val prefix = plugin.config.getString("settings.prefix", "<red>Mistaken <dark_gray>» ")
            player.sendMessage(mm.deserialize(mensaje.replace("<prefix>", prefix!!)))
        }

        val soundName = section.getString("${key}_sonido", "ENTITY_GENERIC_EAT")
        try {
            player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f)
        } catch (ignored: Exception) {}

        when (slot) {
            0 -> usarBebidaEnergética(player)
            1 -> lanzarPedido(player)
            2 -> usarDerrame(player)
        }
    }

    private fun usarBebidaEnergética(player: Player) {
        // Velocidad II por 6 segundos
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 1))

        val job = scope.launch {
            delay(6000) // 6 segundos de efecto
            withContext(Dispatchers.Main) {
                if (player.isOnline) {
                    // El bajón: Hambre por 4 segundos
                    player.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 80, 1))
                }
            }
        }
        trackJob(job)
    }

    private fun lanzarPedido(player: Player) {
        // Lanzamos el proyectil con la metadata que el Listener detectará para curar compas o marear al malo
        player.launchProjectile(Snowball::class.java).apply {
            setMetadata("mistaken_pedido", FixedMetadataValue(plugin, true))
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f)
    }

    private fun usarDerrame(player: Player) {
        val bloque = player.location.block

        // Marcamos el bloque como resbaloso/pegajoso
        bloque.setMetadata("mistaken_derrame", FixedMetadataValue(plugin, true))

        player.spawnParticle(Particle.ITEM_SLIME, player.location, 50, 1.0, 0.0, 1.0)
        player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.5f)

        // Limpieza automática del charco con Coroutine
        val job = scope.launch {
            delay(10000) // 10 segundos de duración
            withContext(Dispatchers.Main) {
                if (bloque.hasMetadata("mistaken_derrame")) {
                    bloque.removeMetadata("mistaken_derrame", plugin)
                    // Partículas de "secado"
                    bloque.world.spawnParticle(Particle.DRIPPING_WATER, bloque.location.add(0.5, 0.1, 0.5), 20, 0.2, 0.1, 0.2)
                }
            }
        }
        trackJob(job)
    }

    private fun createItem(mat: Material, name: String): ItemStack {
        return ItemStack(mat).apply {
            editMeta { it.displayName(mm.deserialize(name)) }
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        // Apagamos todos los cronómetros de bebidas y charcos si el bando se limpia
        scope.coroutineContext.cancelChildren()
    }
}
