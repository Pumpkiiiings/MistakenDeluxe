package liric.mistaken.characters.components.impl

import liric.mistaken.characters.components.AnimationComponent
import liric.mistaken.characters.components.StateComponent
import liric.mistaken.characters.core.Character
import liric.mistaken.characters.states.CharacterState
import liric.mistaken.characters.states.IdleState

/**
 * Implementación estándar de la máquina de estados.
 * Gestiona transiciones y notifica automáticamente al AnimationComponent.
 */
class StandardStateComponent : StateComponent {
    
    private lateinit var character: Character
    private var _currentState: CharacterState = IdleState

    override val currentState: CharacterState
        get() = _currentState

    override fun onEnable(character: Character) {
        this.character = character
        // Iniciar en Idle
        _currentState.onEnter(character)
        playStateAnimation(_currentState)
    }

    override fun onDisable() {
        _currentState.onExit(character)
    }

    override fun transitionTo(newState: CharacterState, force: Boolean): Boolean {
        // Ignorar si ya estamos en ese estado
        if (_currentState.id == newState.id) {
            return false
        }

        // Verificar prioridad (a menos que se fuerce)
        if (!force && !newState.canInterrupt(_currentState)) {
            return false
        }

        val oldState = _currentState
        
        // Salir del estado actual
        _currentState.onExit(character)

        // Detener la animación del estado anterior
        oldState.defaultAnimation?.let {
            character.getComponent(AnimationComponent::class.java)?.stop(it)
        }

        // Cambiar estado
        _currentState = newState

        org.bukkit.Bukkit.broadcastMessage("§e[DEBUG] State changed: ${oldState.id} -> ${newState.id}")

        // Entrar al nuevo estado
        _currentState.onEnter(character)

        // Reproducir la animación asociada (si tiene una)
        playStateAnimation(_currentState)

        return true
    }

    private fun playStateAnimation(state: CharacterState) {
        val animationName = state.defaultAnimation ?: return
        val animationComponent = character.getComponent(AnimationComponent::class.java)
        
        // Si la prioridad es >= 30 (ej. Ataque, Habilidad), no loopea y vuelve a Idle al terminar
        val loop = state.priority < 30 
        
        animationComponent?.play(
            animationName = animationName,
            loop = loop,
            priority = state.priority,
            onComplete = if (!loop) { 
                {
                    // Cuando termina la animación, forzamos la vuelta a Idle
                    // si seguimos en el mismo estado que inició la animación.
                    if (_currentState.id == state.id) {
                        returnToIdle()
                    }
                }
            } else null
        )
    }
}
