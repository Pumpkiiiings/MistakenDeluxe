package liric.mistaken.supervivientes.clases

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.listeners.supervivientes.SupervivienteHabilidadListener
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.utils.mainThread
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

/**
 * [LIRIC-MISTAKEN 2.0]
 * Repartidor: Soporte táctico y control de área.
 *
 * OPTIMIZACIÓN:
 * - Uso de PDC para proyectiles (Paper Native).
 * - Uso de HashSet Global para bloques (Adiós Metadata lenta).
 * - Corrutinas sincronizadas con el MainThread de Bukkit.
 */
class Repartidor : Superviviente("repartidor", "Repartidor") {

    private val pathBase = "supervivientes.repartidor.items"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Llave para marcar el proyectil de forma eficiente
    private val pedidoKey = NamespacedKey("mistaken", "pedido")

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
        player.sendMessage(mm.deserialize("$prefix<gray>¡Tu pedido ha llegado! Clase: <gold>$nombre"))
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val config = plugin.configManager.getSupervivientes()
        val section = config.getConfigurationSection(pathBase) ?: return

        val key = "habilidad${slot + 1}"
        val cooldownSecs = section.getInt("${key}_cooldown", 30)

        if (checkCooldown(player, slot, cooldownSecs)) return

        // --- MENSAJE Y SONIDO ---
        val mensaje = section.getString("${key}_mensaje")
        if (!mensaje.isNullOrEmpty()) {
            val prefix = plugin.config.getString("settings.prefix", "<red>Mistaken <dark_gray>» ")
            player.sendMessage(mm.deserialize(mensaje.replace("<prefix>", prefix!!)))
        }

        val soundName = section.getString("${key}_sonido", "ENTITY_GENERIC_EAT")
        runCatching {
            player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f)
        }

        when (slot) {
            0 -> usarBebidaEnergetica(player)
            1 -> lanzarPedido(player)
            2 -> usarDerrame(player)
        }
    }

    private fun usarBebidaEnergetica(player: Player) {
        // Efecto inmediato de adrenalina
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 1))

        val job = scope.launch {
            delay(6000) // 6 segundos de duración del efecto

            // Regresamos al hilo de Bukkit de forma segura
            withContext(plugin.mainThread) {
                if (player.isOnline) {
                    // "El bajón": Cansancio después de la bebida
                    player.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 80, 1))
                    player.playSound(player.location, Sound.ENTITY_PLAYER_BURP, 0.8f, 0.8f)
                }
            }
        }
        trackJob(job)
    }

    private fun lanzarPedido(player: Player) {
        // Lanzamos Snowball con PDC (Persistent Data Container)
        // Es infinitamente más rápido que setMetadata()
        player.launchProjectile(Snowball::class.java).apply {
            persistentDataContainer.set(pedidoKey, PersistentDataType.BYTE, 1.toByte())
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f)
    }

    private fun usarDerrame(player: Player) {
        val blockLoc = player.location.block.location

        // 🔥 OPTIMIZACIÓN SÉNIOR: Usamos el HashSet del Listener
        // Esto evita que Spark detecte bloqueos por Metadata en bloques.
        SupervivienteHabilidadListener.marcarBloque(blockLoc)

        // Visuales
        player.world.spawnParticle(Particle.ITEM_SLIME, player.location.add(0.0, 0.1, 0.0), 40, 0.5, 0.0, 0.5, 0.1)
        player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.5f)

        // Tarea de limpieza asíncrona
        val job = scope.launch {
            delay(10000) // 10 segundos de duración del charco

            withContext(plugin.mainThread) {
                // Removemos la localización del HashSet
                SupervivienteHabilidadListener.desmarcarBloque(blockLoc)

                // Efecto visual de secado
                blockLoc.world.spawnParticle(
                    Particle.DRIPPING_WATER,
                    blockLoc.clone().add(0.5, 0.1, 0.5),
                    15, 0.2, 0.1, 0.2
                )
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
        // Detenemos todos los timers de bebidas y derrame de este jugador
        scope.coroutineContext.cancelChildren()
    }
}
