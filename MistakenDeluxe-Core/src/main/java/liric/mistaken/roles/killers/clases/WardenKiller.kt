package liric.mistaken.roles.killers.clases

import liric.mistaken.characters.components.CombatComponent
import liric.mistaken.characters.core.Character
import liric.mistaken.characters.states.CharacterState
import liric.mistaken.roles.killers.BaseKiller
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// --- Definición de Estados Exclusivos del Warden ---

object WardenSwipe1State : CharacterState {
    override val id = "swipe_1"
    override val priority = 50
}

object WardenSwipe2State : CharacterState {
    override val id = "swipe_2"
    override val priority = 50
}

object WardenSwipe3State : CharacterState {
    override val id = "swipe_3"
    override val priority = 50
}

object WardenStunState : CharacterState {
    override val id = "stun"
    override val priority = 90
}

// --- Implementación del Personaje ---

class WardenKiller : BaseKiller("warden", "Warden") {
    
    override fun getModelId(): String = "warden"

    // Registra en qué parte del combo está cada jugador y su último ataque
    private val comboSteps = ConcurrentHashMap<UUID, Int>()
    private val lastAttackTimes = ConcurrentHashMap<UUID, Long>()

    override fun setupAdditionalComponents(character: Character) {
        val uuid = character.entity.uniqueId
        
        character.addComponent(CombatComponent::class.java, object : CombatComponent {
            override fun onEnable(character: Character) {}
            override fun onDisable() {}
            
            override fun performAttack(attackId: String) {
                if (character.entity is Player) {
                    this@WardenKiller.attack(character.entity as Player)
                }
            }

            override fun takeDamage(amount: Double, source: Any?): Boolean {
                return true
            }
        })
    }

    /**
     * Lógica de combo simple por jugador: Alterna entre swipe 1, 2 y 3.
     */
    fun attack(player: Player) {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        val lastAttackTime = lastAttackTimes.getOrDefault(uuid, 0L)
        
        var comboStep = comboSteps.getOrDefault(uuid, 0)
        
        // Resetea el combo si pasó mucho tiempo (ej: más de 1.5 segundos) sin atacar
        if (now - lastAttackTime > 1500) {
            comboStep = 0
        }

        val state = when (comboStep) {
            0 -> WardenSwipe1State
            1 -> WardenSwipe2State
            else -> WardenSwipe3State
        }

        // Forzamos la transición al estado de ataque.
        transitionTo(player, state, force = true)
        
        lastAttackTimes[uuid] = now
        comboSteps[uuid] = (comboStep + 1) % 3 // Cicla entre 0, 1 y 2
    }

    /**
     * Aturde al Warden temporalmente.
     */
    fun applyStun(player: Player) {
        transitionTo(player, WardenStunState, force = true)
    }
}
