package liric.mistaken.models.components

import liric.mistaken.models.core.CharacterComponent
import liric.mistaken.models.states.CharacterState
import liric.mistaken.models.states.IdleState

/**
 * Componente que gestiona la mÃ¡quina de estados del personaje.
 * Escucha peticiones de transiciÃ³n y evalÃºa prioridades.
 */
interface StateComponent : CharacterComponent {
    
    /**
     * El estado actual del personaje.
     */
    val currentState: CharacterState

    /**
     * Intenta transicionar a un nuevo estado.
     * La transiciÃ³n solo ocurre si el nuevo estado tiene permisos para interrumpir al actual
     * (determinado por CharacterState.canInterrupt()).
     * 
     * @param newState El nuevo estado al que se desea pasar.
     * @param force Si es true, ignora las reglas de prioridad e interrumpe forzosamente.
     * @return true si la transiciÃ³n fue exitosa, false si fue denegada por falta de prioridad.
     */
    fun transitionTo(newState: CharacterState, force: Boolean = false): Boolean

    /**
     * Fuerza la transiciÃ³n al estado Idle (por ejemplo, despuÃ©s de completar un ataque).
     */
    fun returnToIdle() {
        transitionTo(IdleState, force = true)
    }
}
