package liric.mistaken.models.states

/**
 * Estados base universales que aplican a casi cualquier Character animado.
 */
object IdleState : CharacterState {
    override val id = "idle"
    override val priority = 0
    override fun canInterrupt(currentState: CharacterState): Boolean {
        return super.canInterrupt(currentState) || currentState == FallState || currentState == WalkState || currentState == RunState
    }
}

object WalkState : CharacterState {
    override val id = "walk"
    override val priority = 10
    override fun canInterrupt(currentState: CharacterState): Boolean {
        return super.canInterrupt(currentState) || currentState == FallState || currentState == IdleState || currentState == RunState
    }
}

object RunState : CharacterState {
    override val id = "run"
    override val priority = 20
    override fun canInterrupt(currentState: CharacterState): Boolean {
        return super.canInterrupt(currentState) || currentState == FallState || currentState == IdleState || currentState == WalkState
    }
}

object FallState : CharacterState {
    override val id = "fall"
    override val priority = 30
}

object DeathState : CharacterState {
    override val id = "death"
    override val priority = 100 
    
    
    override fun canInterrupt(currentState: CharacterState): Boolean = true 
}

object AttackState : CharacterState {
    override val id = "attack"
    override val priority = 50
}
