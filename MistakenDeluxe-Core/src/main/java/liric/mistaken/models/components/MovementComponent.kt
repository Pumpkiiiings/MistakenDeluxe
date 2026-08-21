package liric.mistaken.models.components

import liric.mistaken.models.core.CharacterComponent

/**
 * Componente que detecta los cambios fÃ­sicos del player o entidad.
 * No reproduce animaciones directamente; su trabajo es notificar al StateComponent
 * cuando detecta que el Character empezÃ³ a correr, caminar, saltar, etc.
 */
interface MovementComponent : CharacterComponent {
    
    /**
     * La velocidad fÃ­sica actual del personaje.
     */
    val currentVelocity: Double

    /**
     * Comprueba si el personaje estÃ¡ en el suelo.
     */
    val isGrounded: Boolean

    /**
     * Comprueba si el personaje se estÃ¡ moviendo.
     */
    val isMoving: Boolean

    /**
     * Permite habilitar o deshabilitar la detecciÃ³n de movimiento 
     * (por ejemplo, si el personaje estÃ¡ stuneado, se puede bloquear el movimiento).
     */
    fun setMovementEnabled(enabled: Boolean)
}
