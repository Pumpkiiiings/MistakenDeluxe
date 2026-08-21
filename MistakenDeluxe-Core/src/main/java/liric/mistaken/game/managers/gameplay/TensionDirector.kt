package liric.mistaken.game.managers.gameplay

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


class TensionDirector(private val plugin: Mistaken) {

    enum class State {
        /** Killer lejos. No puede pasar nada: es el silencio que da valor al resto. */
        CALMA,
        /** Se acerca, o la partida entra en su fase final. */
        INQUIETUD,
        /** Cerca y con línea de visión. */
        ACECHO,
        /** Te está mirando, o eres el último vivo. */
        CAZA
    }

    /**
     * Foto inmutable de la sesión tomada una vez por tick en el hilo principal.
     * Los datos compartidos (vivos, generadores) se calculan aquí y no por
     * survivor: hacerlo dentro del bucle era O(survivors²) por tick.
     */
    data class KillerSnapshot(
        val location: Location,
        val lookDirection: org.bukkit.util.Vector,
        val worldUid: UUID,
        val aliveSurvivors: Int,
        val generatorsLeft: Int,
        val isDisguised: Boolean = false
    )

    private class Tension {
        var state: State = State.CALMA
        var lastEventAt: Long = 0L
        /** Sube dentro de una persecución, nunca baja hasta volver a CALMA. */
        var escalation: Int = 0
    }

    private val tensions = ConcurrentHashMap<UUID, Tension>()

    
    private fun cooldownMillis(state: State): Long = when (state) {
        State.CALMA -> Long.MAX_VALUE
        State.INQUIETUD -> 40_000L
        State.ACECHO -> 30_000L
        State.CAZA -> 20_000L
    }

    /** Estado actual sin recalcular. Para consumidores que solo quieren leer (latido, HUD). */
    fun stateOf(uuid: UUID): State = tensions[uuid]?.state ?: State.CALMA

    fun escalationOf(uuid: UUID): Int = tensions[uuid]?.escalation ?: 0

    /**
     * Recalcula el estado del survivor. Debe llamarse desde el hilo principal
     * (o la región del survivor): lee estado de Bukkit del propio player.
     * Del killer solo usa el [snapshot], nunca el objeto vivo.
     */
    fun evaluate(survivor: Player, snapshot: KillerSnapshot): State {
        val tension = tensions.getOrPut(survivor.uniqueId) { Tension() }

        
        if (survivor.world.uid != snapshot.worldUid) {
            return applyState(tension, State.CALMA)
        }

        if (snapshot.isDisguised) {
            return applyState(tension, State.CALMA)
        }

        val distSq = survivor.location.distanceSquared(snapshot.location)

        
        
        val next = when {
            
            snapshot.aliveSurvivors <= 1 -> State.CAZA
            
            distSq < 400.0 && hasLineOfSight(survivor, snapshot) && isLookedAt(survivor, snapshot) -> State.CAZA
            distSq < 400.0 && hasLineOfSight(survivor, snapshot) -> State.ACECHO
            distSq < 1600.0 -> State.INQUIETUD
            
            snapshot.generatorsLeft in 1..2 -> State.INQUIETUD
            else -> State.CALMA
        }

        return applyState(tension, next)
    }

    /** Survivors vivos de la sesión. Calcular una vez por tick, no por player. */
    fun countAliveSurvivors(session: GameSession): Int =
        session.getPlayers().count { p ->
            !session.isKiller(p.uniqueId) && !plugin.spectatorManager.isSpectator(p)
        }

    private fun applyState(tension: Tension, next: State): State {
        
        
        if (next == State.CALMA) {
            tension.escalation = 0
        } else if (next.ordinal > tension.state.ordinal) {
            tension.escalation = (tension.escalation + 1).coerceAtMost(3)
        }
        tension.state = next
        return next
    }

    /**
     * Pide permiso para disparar un evento. Consume el presupuesto si lo concede.
     * Devuelve false en CALMA siempre, y dentro del silencio posterior a un evento.
     */
    fun requestEvent(uuid: UUID): Boolean {
        val tension = tensions[uuid] ?: return false
        if (tension.state == State.CALMA) return false

        val now = System.currentTimeMillis()
        if (now - tension.lastEventAt < cooldownMillis(tension.state)) return false

        tension.lastEventAt = now
        return true
    }

    private fun hasLineOfSight(survivor: Player, snapshot: KillerSnapshot): Boolean =
        survivor.world.rayTraceBlocks(
            survivor.eyeLocation,
            snapshot.location.toVector().subtract(survivor.eyeLocation.toVector()),
            survivor.eyeLocation.distance(snapshot.location)
        ) == null

    /** ¿El killer tiene al survivor dentro de su cono de visión? */
    private fun isLookedAt(survivor: Player, snapshot: KillerSnapshot): Boolean {
        val toSurvivor = survivor.location.toVector().subtract(snapshot.location.toVector())
        if (toSurvivor.lengthSquared() < 0.001) return true
        
        return snapshot.lookDirection.dot(toSurvivor.normalize()) > 0.5
    }

    fun clear(uuid: UUID) {
        tensions.remove(uuid)
    }

    fun clearAll() {
        tensions.clear()
    }
}
