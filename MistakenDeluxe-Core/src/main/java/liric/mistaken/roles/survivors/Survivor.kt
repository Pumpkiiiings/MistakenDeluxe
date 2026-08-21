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

    
    private val cooldowns = ConcurrentHashMap<String, Long>()

    
    protected val activeJobs = ConcurrentHashMap.newKeySet<Job>()

    /**
     * Ability secundaria (Clic Izquierdo / Interacciones especiales).
     * Se mantiene 'open' para que classes como Jesse puedan sobreescribirla.
     */
    open fun trackearHeridos(player: Player) {
        
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
        
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()

        player?.let { p ->
            if (p.isOnline) {
                
                p.inventory.clear()

                
                p.activePotionEffects.forEach { effect ->
                    p.removePotionEffect(effect.type)
                }

                
                val prefix = p.uniqueId.toString()
                cooldowns.keys.removeIf { it.startsWith(prefix) }

                p.updateInventory()
            }
        }
    }

    

    abstract override fun equip(player: Player)
    abstract override fun useSkill(player: Player, slot: Int)
}
