package liric.mistaken.roles.killers

import liric.mistaken.characters.components.AnimationComponent
import liric.mistaken.characters.components.CombatComponent
import liric.mistaken.characters.components.ModelComponent
import liric.mistaken.characters.components.MovementComponent
import liric.mistaken.characters.components.StateComponent
import liric.mistaken.characters.components.impl.BukkitMovementComponent
import liric.mistaken.characters.components.impl.StandardStateComponent
import liric.mistaken.characters.core.Character
import liric.mistaken.characters.integration.bettermodel.BetterModelAnimationComponent
import liric.mistaken.characters.integration.bettermodel.BetterModelComponent
import liric.mistaken.characters.states.CharacterState
import org.bukkit.entity.Player

/**
 * Clase base para todos los Killers.
 * Ensambla los componentes genéricos del Character Framework y añade 
 * componentes específicos (como combate y habilidades) exclusivos del Killer.
 */
abstract class BaseKiller(val player: Player) {

    // El Character genérico subyacente
    protected val character = Character(player)

    init {
        // 1. Configurar infraestructura compartida
        character.addComponent(ModelComponent::class.java, BetterModelComponent(getModelId()))
        character.addComponent(AnimationComponent::class.java, BetterModelAnimationComponent())
        character.addComponent(StateComponent::class.java, StandardStateComponent())
        character.addComponent(MovementComponent::class.java, BukkitMovementComponent())
        
        // 2. Configurar componentes exclusivos de este rol
        // character.addComponent(CombatComponent::class.java, KillerMeleeCombatComponent())
        
        // 3. Inicializar modelo
        character.getComponent(ModelComponent::class.java)?.spawn()
    }

    /**
     * El identificador del modelo en BetterModel (ej. "clown_killer").
     */
    abstract fun getModelId(): String

    /**
     * Helper para transicionar de estado fácilmente.
     */
    protected fun transitionTo(state: CharacterState, force: Boolean = false) {
        character.getComponent(StateComponent::class.java)?.transitionTo(state, force)
    }

    /**
     * Limpia los componentes cuando el Killer es destruido.
     */
    open fun destroy() {
        character.destroy()
    }
}
