package liric.mistaken.models.core

/**
 * Interfaz base para todos los componentes del sistema ECS (Entity-Component-System) Lite.
 * 
 * Los componentes aíslan lógica específica (modelo, animación, movimiento) y 
 * se adhieren a un Character.
 */
interface CharacterComponent {
    /**
     * Llamado cuando el componente es añadido al Character.
     */
    fun onEnable(character: Character)

    /**
     * Llamado cuando el componente es removido o el Character es destruido.
     */
    fun onDisable()

    /**
     * Opcional: Llamado cada tick del servidor si el componente necesita actualización constante.
     * Si no se necesita, se puede dejar vacío o manejar internamente.
     */
    fun tick() {}
}
