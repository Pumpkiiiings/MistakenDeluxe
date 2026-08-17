package liric.mistaken.characters.components

import liric.mistaken.characters.core.CharacterComponent
import liric.mistaken.characters.states.CharacterState
import liric.mistaken.characters.states.IdleState

/**
 * Componente que gestiona la máquina de estados del personaje.
 * Escucha peticiones de transición y evalúa prioridades.
 */
interface StateComponent : CharacterComponent {
    
    /**
     * El estado actual del personaje.
     */
    val currentState: CharacterState

    /**
     * Intenta transicionar a un nuevo estado.
     * La transición solo ocurre si el nuevo estado tiene permisos para interrumpir al actual
     * (determinado por CharacterState.canInterrupt()).
     * 
     * @param newState El nuevo estado al que se desea pasar.
     * @param force Si es true, ignora las reglas de prioridad e interrumpe forzosamente.
     * @return true si la transición fue exitosa, false si fue denegada por falta de prioridad.
     */
    fun transitionTo(newState: CharacterState, force: Boolean = false): Boolean

    /**
     * Fuerza la transición al estado Idle (por ejemplo, después de completar un ataque).
     */
    fun returnToIdle() {
        transitionTo(IdleState, force = true)
    }
}
