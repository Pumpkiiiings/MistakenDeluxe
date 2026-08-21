package liric.mistaken.models.core

/**
 * Interfaz base para todos los componentes del sistema ECS (Entity-Component-System) Lite.
 * 
 * Los componentes aÃ­slan lÃ³gica especÃ­fica (modelo, animaciÃ³n, movimiento) y 
 * se adhieren a un Character.
 */
interface CharacterComponent {
    /**
     * Llamado cuando el componente es aÃ±adido al Character.
     */
    fun onEnable(character: Character)

    /**
     * Llamado cuando el componente es removido o el Character es destruido.
     */
    fun onDisable()

    /**
     * Opcional: Llamado cada tick del servidor si el componente necesita actualizaciÃ³n constante.
     * Si no se necesita, se puede dejar vacÃ­o o manejar internamente.
     */
    fun tick() {}
}
