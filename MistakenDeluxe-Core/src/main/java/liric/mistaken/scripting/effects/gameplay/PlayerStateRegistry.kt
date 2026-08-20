package liric.mistaken.scripting.effects.gameplay

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerStateRegistry {

    // scriptId -> UUID -> Key -> Value
    private val states = ConcurrentHashMap<String, ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>>>()

    fun set(scriptId: String, playerUuid: UUID, key: String, value: String) {
        val scriptStates = states.computeIfAbsent(scriptId) { ConcurrentHashMap() }
        val playerStates = scriptStates.computeIfAbsent(playerUuid) { ConcurrentHashMap() }
        playerStates[key] = value
    }

    fun get(scriptId: String, playerUuid: UUID, key: String): String? {
        return states[scriptId]?.get(playerUuid)?.get(key)
    }

    fun clear(scriptId: String, playerUuid: UUID, key: String) {
        states[scriptId]?.get(playerUuid)?.remove(key)
    }

    fun clearAllForPlayer(playerUuid: UUID) {
        states.values.forEach { scriptStates ->
            scriptStates.remove(playerUuid)
        }
    }
    
    fun clearAllForScript(scriptId: String) {
        states.remove(scriptId)
    }
}
