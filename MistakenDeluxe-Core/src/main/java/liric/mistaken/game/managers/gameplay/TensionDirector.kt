package liric.mistaken.game.managers.gameplay

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * TensionDirector: decide CUÁNDO puede pasar algo, no QUÉ pasa.
 *
 * Antes los sustos salían de un dado plano cada 100 ms, idéntico en el segundo 10
 * que en el 290, con el asesino encima o al otro lado del mapa. Un ritmo constante
 * se convierte en ruido de fondo: el jugador modela la distribución y deja de
 * reaccionar.
 *
 * Aquí el ritmo sale del estado real de la partida y está limitado por un
 * presupuesto: tras cada evento hay un silencio obligatorio. Menos sustos totales,
 * más efecto por susto.
 */
class TensionDirector(private val plugin: Mistaken) {

    enum class State {
        /** Asesino lejos. No puede pasar nada: es el silencio que da valor al resto. */
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
     * superviviente: hacerlo dentro del bucle era O(supervivientes²) por tick.
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

    // Silencio obligatorio tras un evento, por estado. En CALMA no se dispara nada.
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
     * Recalcula el estado del superviviente. Debe llamarse desde el hilo principal
     * (o la región del superviviente): lee estado de Bukkit del propio jugador.
     * Del asesino solo usa el [snapshot], nunca el objeto vivo.
     */
    fun evaluate(survivor: Player, snapshot: KillerSnapshot): State {
        val tension = tensions.getOrPut(survivor.uniqueId) { Tension() }

        // Otro mundo = fuera de juego para efectos de tensión.
        if (survivor.world.uid != snapshot.worldUid) {
            return applyState(tension, State.CALMA)
        }

        if (snapshot.isDisguised) {
            return applyState(tension, State.CALMA)
        }

        val distSq = survivor.location.distanceSquared(snapshot.location)

        // El orden importa: `distSq` cortocircuita antes del rayTrace, así que
        // solo trazamos dentro de 20 bloques.
        val next = when {
            // Último vivo: máxima presión pase lo que pase.
            snapshot.aliveSurvivors <= 1 -> State.CAZA
            // Cerca, con visión, y el asesino mirando hacia ti.
            distSq < 400.0 && hasLineOfSight(survivor, snapshot) && isLookedAt(survivor, snapshot) -> State.CAZA
            distSq < 400.0 && hasLineOfSight(survivor, snapshot) -> State.ACECHO
            distSq < 1600.0 -> State.INQUIETUD
            // La recta final aprieta aunque el asesino esté lejos.
            snapshot.generatorsLeft in 1..2 -> State.INQUIETUD
            else -> State.CALMA
        }

        return applyState(tension, next)
    }

    /** Supervivientes vivos de la sesión. Calcular una vez por tick, no por jugador. */
    fun countAliveSurvivors(session: GameSession): Int =
        session.getPlayers().count { p ->
            !session.isKiller(p.uniqueId) && !plugin.spectatorManager.isSpectator(p)
        }

    private fun applyState(tension: Tension, next: State): State {
        // La escalada solo sube dentro de la persecución. Bajarla a mitad le enseña
        // al jugador que ya sobrevivió y lo relaja justo cuando no debe.
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

    /** ¿El asesino tiene al superviviente dentro de su cono de visión? */
    private fun isLookedAt(survivor: Player, snapshot: KillerSnapshot): Boolean {
        val toSurvivor = survivor.location.toVector().subtract(snapshot.location.toVector())
        if (toSurvivor.lengthSquared() < 0.001) return true
        // ~60° de cono frontal
        return snapshot.lookDirection.dot(toSurvivor.normalize()) > 0.5
    }

    fun clear(uuid: UUID) {
        tensions.remove(uuid)
    }

    fun clearAll() {
        tensions.clear()
    }
}
