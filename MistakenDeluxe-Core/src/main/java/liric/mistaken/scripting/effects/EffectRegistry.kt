package liric.mistaken.scripting.effects

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registro central de efectos activos (bookkeeping only).
 * NO tickea nada — cada EffectHandle posee su propio ScheduledTask.
 * Solo guarda referencias para poder cancelar por scriptId o ownerUuid.
 */
object EffectRegistry {

    private val byScript = ConcurrentHashMap<String, CopyOnWriteArrayList<EffectHandle>>()
    private val byOwner = ConcurrentHashMap<UUID, CopyOnWriteArrayList<EffectHandle>>()

    fun register(handle: EffectHandle) {
        byScript.getOrPut(handle.scriptId) { CopyOnWriteArrayList() }.add(handle)
        byOwner.getOrPut(handle.ownerUuid) { CopyOnWriteArrayList() }.add(handle)
    }

    /** Detiene todos los efectos de un script (recarga/descarga). */
    fun stopAll(scriptId: String) {
        byScript.remove(scriptId)?.forEach { it.stop() }
        byOwner.values.forEach { list -> list.removeIf { it.scriptId == scriptId } }
        byOwner.entries.removeIf { it.value.isEmpty() }
    }

    /** Detiene todos los efectos de un jugador (quit/death/removeKiller). */
    fun stopAll(ownerUuid: UUID) {
        byOwner.remove(ownerUuid)?.forEach { it.stop() }
        byScript.values.forEach { list -> list.removeIf { it.ownerUuid == ownerUuid } }
        byScript.entries.removeIf { it.value.isEmpty() }
    }

    /** Detiene efectos de un jugador específico dentro de un script. */
    fun stopAll(scriptId: String, ownerUuid: UUID) {
        byScript[scriptId]?.let { list ->
            val toRemove = list.filter { it.ownerUuid == ownerUuid }
            toRemove.forEach { it.stop() }
            list.removeAll(toRemove.toSet())
        }
        byOwner[ownerUuid]?.removeIf { it.scriptId == scriptId }
    }

    /** Elimina handles muertos (limpieza periódica). */
    fun cleanup() {
        byScript.values.forEach { list -> list.removeIf { !it.isAlive } }
        byOwner.values.forEach { list -> list.removeIf { !it.isAlive } }
        byScript.entries.removeIf { it.value.isEmpty() }
        byOwner.entries.removeIf { it.value.isEmpty() }
    }
}
