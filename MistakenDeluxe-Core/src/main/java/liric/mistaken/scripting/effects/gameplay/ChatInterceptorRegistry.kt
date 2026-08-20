package liric.mistaken.scripting.effects.gameplay

import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

object ChatInterceptorRegistry {
    // scriptId -> callback que toma Player y String y devuelve String?
    private val callbacks = ConcurrentHashMap<String, (Player, String) -> String?>()

    fun registerCallback(scriptId: String, callback: (Player, String) -> String?) {
        callbacks[scriptId] = callback
    }

    fun getCallback(scriptId: String): ((Player, String) -> String?)? {
        return callbacks[scriptId]
    }
    
    fun removeCallback(scriptId: String) {
        callbacks.remove(scriptId)
    }
}
