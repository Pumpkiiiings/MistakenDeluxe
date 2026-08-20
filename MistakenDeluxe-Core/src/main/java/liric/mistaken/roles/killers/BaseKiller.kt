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

    /**
     * YAGNI: El pipeline ECS (modelo + state machine + movement) solo se inicializa
     * si getModelId() retorna un valor no-null. Killers sin modelo 3D (Null, Charlie,
     * 303, Romeo, etc.) no necesitan ningún componente ECS y se comportan igual que
     * sus equivalentes Kotlin vía CoreKiller.
     *
     * Si en el futuro se necesita un killer Lua con state machine pero sin modelo 3D,
     * este bloque debe separarse en dos flags independientes (getModelId() y
     * usesStateMachine()). Por ahora, un solo condicional es suficiente.
     */
    open override fun equip(player: Player) {
        val character = Character(player)
        activeCharacters[player.uniqueId] = character

        val modelId = getModelId()
        if (modelId != null) {
            // Solo cargar ECS + modelo si hay modelo 3D definido
            val pluginManager = org.bukkit.Bukkit.getPluginManager()
            if (pluginManager.isPluginEnabled("ModelEngine")) {
                character.addComponent(ModelComponent::class.java, liric.mistaken.characters.integration.modelengine.ModelEngineComponent(modelId))
                character.addComponent(AnimationComponent::class.java, liric.mistaken.characters.integration.modelengine.ModelEngineAnimationComponent())
            } else if (pluginManager.isPluginEnabled("BetterModel")) {
                character.addComponent(ModelComponent::class.java, BetterModelComponent(modelId))
                character.addComponent(AnimationComponent::class.java, BetterModelAnimationComponent())
            } else {
                org.bukkit.Bukkit.getLogger().warning("Neither ModelEngine nor BetterModel is enabled! Cannot load models for killer.")
            }

            character.addComponent(StateComponent::class.java, StandardStateComponent())
            character.addComponent(MovementComponent::class.java, BukkitMovementComponent())

            // Componentes exclusivos del rol (CombatComponent, etc.)
            setupAdditionalComponents(character)

            // Inicializar modelo
            character.getComponent(ModelComponent::class.java)?.spawn()
        }
    }

    /**
     * El identificador del modelo en BetterModel (ej. "warden").
     * Retorna null si el killer no usa modelo 3D — en ese caso, el pipeline
     * ECS completo (modelo, animaciones, estado, movimiento) se omite.
     */
    abstract fun getModelId(): String?

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
