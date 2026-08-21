package liric.mistaken.models.components

import liric.mistaken.models.core.CharacterComponent

/**
 * Componente opcional para manejar lÃ³gicas de combate.
 * Killers tendrÃ¡n uno, Survivors probablemente no.
 */
interface CombatComponent : CharacterComponent {
    
    /**
     * Inicia un ataque (por ejemplo, cuando el player hace clic izquierdo).
     */
    fun performAttack(attackId: String = "attack")

    /**
     * Aplica daÃ±o a este personaje.
     * Retorna true si el daÃ±o fue procesado exitosamente (ej. no estaba en invulnerabilidad).
     */
    fun takeDamage(amount: Double, source: Any? = null): Boolean
    
    /**
     * Callback para registrar listeners de hitboxes internos si el engine
     * lo soporta (ej. BetterModel HitBoxes).
     */
    fun registerHitBoxListeners() {}
}
