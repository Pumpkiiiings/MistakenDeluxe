package liric.mistaken.scripting.effects

import java.util.UUID

/**
 * Handle controlable para un efecto activo.
 * Cada handle posee su propio ScheduledTask (Folia-safe).
 * EffectRegistry guarda referencias para cancelación centralizada.
 */
interface EffectHandle {
    val scriptId: String
    val ownerUuid: UUID
    val isAlive: Boolean
    fun stop()
    fun remove() { stop() }
}
