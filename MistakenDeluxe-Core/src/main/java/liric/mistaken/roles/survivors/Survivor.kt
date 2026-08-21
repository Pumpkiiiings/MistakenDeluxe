package liric.mistaken.roles.survivors

import kotlinx.coroutines.Job
import liric.mistaken.Mistaken
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.utils.color.ColorTranslator

import liric.mistaken.api.roles.GameRole


abstract class Survivor(override val id: String, override val nombre: String) : liric.mistaken.api.roles.ISurvivor {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    // Cooldowns: UUID_Slot -> Timestamp (ms)
    private val cooldowns = ConcurrentHashMap<String, Long>()

    // Rastrero de tareas as�ncronas (Jobs) para limpieza profunda
    protected val activeJobs = ConcurrentHashMap.newKeySet<Job>()

    /**
     * Ability secundaria (Clic Izquierdo / Interacciones especiales).
     * Se mantiene 'open' para que classes como Jesse puedan sobreescribirla.
     */
    open fun trackearHeridos(player: Player) {
        // Por defecto no hace nada, evitando errores en classes b�sicas como Civilian.
    }

    /**
     * Verifica el enfriamiento de una ability y env�a feedback visual.
     * @return true si a�n est� en cooldown, false si se puede usar.
     */
    fun checkCooldown(player: Player, slot: Int, seconds: Int): Boolean {
        if (seconds <= 0) return false

        val key = "${player.uniqueId}_$slot"
        val now = System.currentTimeMillis()
        val expireTime = cooldowns.getOrDefault(key, 0L)

        if (now < expireTime) {
            val remaining = (expireTime - now) / 1000.0
            player.sendActionBar(ColorTranslator.translate("<red>Cooldown: <white>${"%.1f".format(remaining)}s</white>"))
            return true
        }

        // Registrar nuevo cooldown
        cooldowns[key] = now + (seconds * 1000L)
        return false
    }

    /**
     * Registra una corrutina (Job) para ser cancelada autom�ticamente al finalizar la partida.
     */
    protected fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    open override fun cleanup(player: Player?) {
        // 1. Detener todas las corrutinas de la clase (rastreos, part�culas, etc.)
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()

        player?.let { p ->
            if (p.isOnline) {
                // 2. Clear inventario
                p.inventory.clear()

                // 3. Quitar efectos de poci�n que la clase haya podido apply
                p.activePotionEffects.forEach { effect ->
                    p.removePotionEffect(effect.type)
                }

                // 4. Clear cooldowns de la memoria RAM
                val prefix = p.uniqueId.toString()
                cooldowns.keys.removeIf { it.startsWith(prefix) }

                p.updateInventory()
            }
        }
    }

    // --- M�TODOS ABSTRACTOS ---

    abstract override fun equip(player: Player)
    abstract override fun useSkill(player: Player, slot: Int)
}
