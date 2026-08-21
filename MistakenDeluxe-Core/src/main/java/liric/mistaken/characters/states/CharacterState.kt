package liric.mistaken.characters.states

import liric.mistaken.characters.core.Character

/**
 * Representa un estado discreto en la máquina de estados del Character (StateComponent).
 * Ejemplos de implementaciones: WalkState, AttackState, StunState, etc.
 */
interface CharacterState {
    
    /**
     * Identificador único del estado (ej. "walk", "attack").
     */
    val id: String
    
    /**
     * Prioridad del estado.
     * Al intentar transicionar, el StateComponent verifica si el nuevo estado tiene 
     * suficiente prioridad para interrumpir el actual.
     * Mayor número = Mayor prioridad. (Ej: DEATH=100, STUN=90, ATTACK=50, WALK=10, IDLE=0)
     */
    val priority: Int
    
    /**
     * Animación por defecto que debe reproducirse al entrar a este estado.
     * Si es null, no se solicita ninguna animación automáticamente.
     */
    val defaultAnimation: String?
        get() = id 

    /**
     * Define si este estado puede interrumpir al estado actual especificado.
     * La implementación por defecto usa la prioridad, pero puede ser sobreescrita 
     * para casos especiales (ej. un combo de ataque).
     */
    fun canInterrupt(currentState: CharacterState): Boolean {
        return this.priority >= currentState.priority
    }

    /**
     * Llamado cuando el Character entra en este estado.
     * Aquí se puede ejecutar lógica como iniciar cooldowns, apply lentitud, etc.
     */
    fun onEnter(character: Character) {
        
    }

    /**
     * Llamado cuando el Character sale de este estado.
     * Aquí se deben clear timers o efectos temporales aplicados en onEnter.
     */
    fun onExit(character: Character) {
        
    }
}
