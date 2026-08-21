package liric.mistaken.models.components

import liric.mistaken.models.core.CharacterComponent

/**
 * Componente base para manejar el modelo visual de un personaje.
 * Este componente aísla la integración directa con engines de modelos (como BetterModel).
 */
interface ModelComponent : CharacterComponent {
    
    /**
     * El identificador del modelo (ej. "killer_clown", "survivor_default").
     */
    val modelId: String

    /**
     * Spawnea o hace visible el modelo en el world.
     */
    fun spawn()

    /**
     * Despawnea o destruye la instancia visual del modelo.
     */
    fun despawn()

    /**
     * Cambia la escala visual del modelo.
     */
    fun setScale(scale: Float)
    
    /**
     * Aplica un color/tinte al modelo (útil para efectos de daño).
     * @param rgb El color en formato RGB, o null para remover el tinte.
     */
    fun setTint(rgb: Int?)
    
    /**
     * Fuerza una actualización visual (si el engine subyacente lo requiere).
     */
    fun forceUpdate()
}
