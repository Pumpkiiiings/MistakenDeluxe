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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Clase base para todos los Killers basados en el nuevo ECS Framework.
 * Al extender de CoreKiller, hereda el comportamiento Singleton que requiere el KillerManager.
 */
abstract class BaseKiller(id: String, nombre: String) : CoreKiller(id, nombre) {

    // Registra los characters activos de cada jugador usando este asesino
    protected val activeCharacters = ConcurrentHashMap<UUID, Character>()

    override fun equip(player: Player) {
        val character = Character(player)
        activeCharacters[player.uniqueId] = character

        // 1. Configurar infraestructura compartida (ECS)
        character.addComponent(ModelComponent::class.java, liric.mistaken.characters.integration.modelengine.ModelEngineComponent(getModelId()))
        character.addComponent(AnimationComponent::class.java, liric.mistaken.characters.integration.modelengine.ModelEngineAnimationComponent())
        character.addComponent(StateComponent::class.java, StandardStateComponent())
        character.addComponent(MovementComponent::class.java, BukkitMovementComponent())
        
        // 2. Componentes exclusivos del rol
        setupAdditionalComponents(character)
        
        // 3. Inicializar modelo
        character.getComponent(ModelComponent::class.java)?.spawn()
    }

    /**
     * El identificador del modelo en BetterModel (ej. "clown_killer").
     */
    abstract fun getModelId(): String

    /**
     * Permite inyectar componentes extra (ej. CombatComponent) al instanciar.
     */
    open fun setupAdditionalComponents(character: Character) {}

    /**
     * Helper para transicionar de estado fácilmente para un jugador específico.
     */
    protected fun transitionTo(player: Player, state: CharacterState, force: Boolean = false) {
        val character = activeCharacters[player.uniqueId] ?: return
        character.getComponent(StateComponent::class.java)?.transitionTo(state, force)
    }

    /**
     * Obtiene el Character (ECS) asociado a un jugador.
     */
    fun getCharacter(player: Player): Character? {
        return activeCharacters[player.uniqueId]
    }

    /**
     * Llama al tick de todos los characters activos.
     */
    fun tickAll() {
        for (character in activeCharacters.values) {
            character.tick()
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            activeCharacters.remove(it.uniqueId)?.destroy()
        }
    }
    
    // Stubs para cumplir con la interfaz abstracta heredada de CoreKiller/Killer
    override fun showTrail(player: Player) {}
    override fun useSkill(player: Player, slot: Int) {}
}
