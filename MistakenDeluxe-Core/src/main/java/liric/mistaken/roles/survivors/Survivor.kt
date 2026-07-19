package liric.mistaken.roles.survivors

import kotlinx.coroutines.Job
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Survivor: Clase base para los humanos del juego.
 * Optimizada para soportar habilidades secundarias y tareas asíncronas ligeras.
 */
abstract class Survivor(val id: String, val nombre: String) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    // Cooldowns: UUID_Slot -> Timestamp (ms)
    private val cooldowns = ConcurrentHashMap<String, Long>()

    // Rastrero de tareas asíncronas (Jobs) para limpieza profunda
    protected val activeJobs = ConcurrentHashMap.newKeySet<Job>()

    /**
     * Habilidad secundaria (Clic Izquierdo / Interacciones especiales).
     * Se mantiene 'open' para que clases como Jesse puedan sobreescribirla.
     */
    open fun trackearHeridos(player: Player) {
        // Por defecto no hace nada, evitando errores en clases básicas como Civilian.
    }

    /**
     * Verifica el enfriamiento de una habilidad y envía feedback visual.
     * @return true si aún está en cooldown, false si se puede usar.
     */
    fun checkCooldown(player: Player, slot: Int, seconds: Int): Boolean {
        if (seconds <= 0) return false

        val key = "${player.uniqueId}_$slot"
        val now = System.currentTimeMillis()
        val expireTime = cooldowns.getOrDefault(key, 0L)

        if (now < expireTime) {
            val remaining = (expireTime - now) / 1000.0
            player.sendActionBar(pumpking.lib.color.ColorTranslator.translate("<red>Cooldown: <white>${"%.1f".format(remaining)}s</white>"))
            return true
        }

        // Registrar nuevo cooldown
        cooldowns[key] = now + (seconds * 1000L)
        return false
    }

    /**
     * Registra una corrutina (Job) para ser cancelada automáticamente al finalizar la partida.
     */
    protected fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    /**
     * Limpieza total de estados, tareas e inventario del superviviente.
     */
    open fun cleanup(player: Player?) {
        // 1. Detener todas las corrutinas de la clase (rastreos, partículas, etc.)
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()

        player?.let { p ->
            if (p.isOnline) {
                // 2. Limpiar inventario
                p.inventory.clear()

                // 3. Quitar efectos de poción que la clase haya podido aplicar
                p.activePotionEffects.forEach { effect ->
                    p.removePotionEffect(effect.type)
                }

                // 4. Limpiar cooldowns de la memoria RAM
                val prefix = p.uniqueId.toString()
                cooldowns.keys.removeIf { it.startsWith(prefix) }

                p.updateInventory()
            }
        }
    }

    // --- MÉTODOS ABSTRACTOS ---

    abstract fun equip(player: Player)
    abstract fun useSkill(player: Player, slot: Int)
}
