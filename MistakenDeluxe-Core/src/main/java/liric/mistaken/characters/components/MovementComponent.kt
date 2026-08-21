package liric.mistaken.characters.components

import liric.mistaken.characters.core.CharacterComponent

/**
 * Componente que detecta los cambios físicos del player o entidad.
 * No reproduce animaciones directamente; su trabajo es notificar al StateComponent
 * cuando detecta que el Character empezó a correr, caminar, saltar, etc.
 */
interface MovementComponent : CharacterComponent {
    
    /**
     * La velocidad física actual del personaje.
     */
    val currentVelocity: Double

    /**
     * Comprueba si el personaje está en el suelo.
     */
    val isGrounded: Boolean

    /**
     * Comprueba si el personaje se está moviendo.
     */
    val isMoving: Boolean

    /**
     * Permite habilitar o deshabilitar la detección de movimiento 
     * (por ejemplo, si el personaje está stuneado, se puede bloquear el movimiento).
     */
    fun setMovementEnabled(enabled: Boolean)
}
