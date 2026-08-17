package liric.mistaken.characters.states

/**
 * Estados base universales que aplican a casi cualquier Character animado.
 */
object IdleState : CharacterState {
    override val id = "idle"
    override val priority = 0
}

object WalkState : CharacterState {
    override val id = "walk"
    override val priority = 10
}

object RunState : CharacterState {
    override val id = "run"
    override val priority = 20
}

object FallState : CharacterState {
    override val id = "fall"
    override val priority = 30
}

object DeathState : CharacterState {
    override val id = "death"
    override val priority = 100 // Máxima prioridad general
    
    // Nadie puede interrumpir la muerte a menos que sea un estado de "Revive" especial
    override fun canInterrupt(currentState: CharacterState): Boolean = true 
}
