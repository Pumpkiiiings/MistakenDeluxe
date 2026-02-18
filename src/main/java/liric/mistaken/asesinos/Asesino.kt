package liric.mistaken.asesinos

import kotlinx.coroutines.Job
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Asesino: Clase base polimórfica ultra-optimizada.
 * Usa Coroutines para el rastreo de habilidades y efectos sin saturar el Bukkit Scheduler.
 */
abstract class Asesino(val id: String, val nombre: String) {

    protected val plugin = Mistaken.instance
    protected val mm = Mistaken.mm

    // Cooldowns: UUID_Slot -> Timestamp (ms)
    private val cooldowns = ConcurrentHashMap<String, Long>()

    // Rastrero de tareas asíncronas (Jobs) para limpieza automática
    protected val activeJobs = ConcurrentHashMap.newKeySet<Job>()

    /**
     * Verifica si una habilidad está en cooldown y envía feedback visual.
     * @return true si está en cooldown (no se puede usar), false si está lista.
     */
    fun checkCooldown(player: Player, slot: Int): Boolean {
        val config = plugin.configManager.getAsesinos()
        val pathBase = "asesinos.$id.items.habilidad$slot"

        val cooldownSecs = config.getInt("${pathBase}_cooldown", 0)
        val nombreHabilidad = config.getString("${pathBase}_nombre", "Habilidad $slot") ?: "Habilidad $slot"

        if (cooldownSecs <= 0) return false

        val key = "${player.uniqueId}_$slot"
        val now = System.currentTimeMillis()
        val expireTime = cooldowns.getOrDefault(key, 0L)

        if (now < expireTime) {
            val remaining = (expireTime - now) / 1000.0
            player.sendActionBar(mm.deserialize(
                "<red><bold>!!</bold> <yellow>$nombreHabilidad</yellow> disponible en: <white>${"%.1f".format(remaining)}s</white>"
            ))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f)
            return true
        }

        // Feedback de uso exitoso
        player.sendMessage(mm.deserialize("<green><bold>✔</bold> Has utilizado <white>$nombreHabilidad</white>"))
        cooldowns[key] = now + (cooldownSecs * 1000L)
        return false
    }

    /**
     * Registra un Job de corrutina para ser cancelado automáticamente al terminar la partida.
     */
    protected fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    /**
     * Reproduce el sonido configurado para una habilidad.
     */
    fun reproducirEfectosHabilidad(player: Player, slot: Int) {
        val config = plugin.configManager.getAsesinos()
        val sonidoName = config.getString("asesinos.$id.items.habilidad${slot}_sonido") ?: return

        try {
            val sound = Sound.valueOf(sonidoName.uppercase())
            player.world.playSound(player.location, sound, 1.0f, 0.7f)
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Sonido inválido en config para $id (Habilidad $slot): $sonidoName")
        }
    }

    /**
     * Limpieza profunda del asesino.
     * Cancela todas las corrutinas activas y resetea al jugador físicamente.
     */
    open fun cleanup(player: Player?) {
        // 1. Cancelar todas las tareas (Trails de partículas, loops de efectos, etc)
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()

        player?.let { p ->
            if (p.isOnline) {
                // 2. Reset de inventario y equipo
                p.inventory.clear()
                p.inventory.armorContents = null

                // 3. Limpiar todos los efectos de poción de forma segura
                p.activePotionEffects.forEach { effect ->
                    p.removePotionEffect(effect.type)
                }

                // 4. Resetear estados físicos (Paper API)
                p.isFlying = false
                p.allowFlight = false
                p.isGlowing = false
                p.inventory.heldItemSlot = 0

                // 5. Limpiar sus cooldowns para liberar RAM
                val prefix = p.uniqueId.toString()
                cooldowns.keys.removeIf { it.startsWith(prefix) }

                p.updateInventory()
            }
        }
    }

    // --- MÉTODOS ABSTRACTOS ---

    abstract fun equipar(player: Player)
    abstract fun usarHabilidad(player: Player, slot: Int)
    abstract fun mostrarTrail(player: Player)

    /**
     * Trail físico (bloques, fuego, etc). Se ejecuta en el Main Thread.
     */
    open fun mostrarTrailFisico(player: Player) {}

    // --- GETTERS ---
    fun getRemainingCooldown(player: Player, slot: Int): Long {
        val key = "${player.uniqueId}_$slot"
        return (cooldowns.getOrDefault(key, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    }
}
