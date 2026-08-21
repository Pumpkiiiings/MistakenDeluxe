package liric.mistaken.models.components.impl

import liric.mistaken.models.components.AnimationComponent
import liric.mistaken.models.components.StateComponent
import liric.mistaken.models.core.Character
import liric.mistaken.models.states.CharacterState
import liric.mistaken.models.states.IdleState

/**
 * ImplementaciÃ³n estÃ¡ndar de la mÃ¡quina de estados.
 * Gestiona transiciones y notifica automÃ¡ticamente al AnimationComponent.
 */
class StandardStateComponent : StateComponent {
    
    private lateinit var character: Character
    private var _currentState: CharacterState = IdleState

    override val currentState: CharacterState
        get() = _currentState

    override fun onEnable(character: Character) {
        this.character = character
        
        _currentState.onEnter(character)
        playStateAnimation(_currentState)
    }

    override fun onDisable() {
        _currentState.onExit(character)
    }

    override fun transitionTo(newState: CharacterState, force: Boolean): Boolean {
        
        if (_currentState.id == newState.id) {
            return false
        }

        
        if (!force && !newState.canInterrupt(_currentState)) {
            return false
        }

        val oldState = _currentState
        
        
        _currentState.onExit(character)

        
        oldState.defaultAnimation?.let {
            character.getComponent(AnimationComponent::class.java)?.stop(it)
        }

        
        _currentState = newState

        
        _currentState.onEnter(character)

        
        playStateAnimation(_currentState)

        return true
    }

    override fun returnToIdle() {
        val movement = character.getComponent(liric.mistaken.models.components.MovementComponent::class.java)
        if (movement != null && movement.isMoving) {
            val entity = character.entity
            val newState = if (entity is org.bukkit.entity.Player && entity.isSprinting) {
                liric.mistaken.models.states.RunState
            } else {
                liric.mistaken.models.states.WalkState
            }
            transitionTo(newState, force = true)
        } else {
            super.returnToIdle()
        }
    }

    private fun playStateAnimation(state: CharacterState) {
        val animationName = state.defaultAnimation
        if (animationName == null) {
            
            
            
            if (state.priority >= 30) {
                if (_currentState.id == state.id) {
                    returnToIdle()
                }
            }
            return
        }

        val animationComponent = character.getComponent(AnimationComponent::class.java)
        
        
        val loop = state.priority < 30 
        
        if (animationComponent == null) {
            
            if (!loop) {
                if (_currentState.id == state.id) {
                    returnToIdle()
                }
            }
            return
        }

        animationComponent.play(
            animationName = animationName,
            loop = loop,
            priority = state.priority,
            onComplete = if (!loop) { 
                {
                    
                    
                    if (_currentState.id == state.id) {
                        returnToIdle()
                    }
                }
            } else null
        )
    }
}
