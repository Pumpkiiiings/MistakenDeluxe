package liric.mistaken.roles.killers.clases

import liric.mistaken.characters.components.CombatComponent
import liric.mistaken.characters.core.Character
import liric.mistaken.characters.states.CharacterState
import liric.mistaken.roles.killers.BaseKiller
import org.bukkit.entity.Player

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
    // Al tener prioridad >= 30, la animación se reproducirá UNA VEZ y el personaje
    // volverá automáticamente a Idle. (Si quisieras que el stun durara más tiempo,
    // tendrías que modificar el StandardStateComponent o manejar un loop custom).
}

// --- Implementación del Personaje ---

class WardenKiller(player: Player) : BaseKiller(player) {
    
    // BetterModel buscará el archivo "warden.bbmodel"
    override fun getModelId(): String = "warden"

    private var comboStep = 0
    private var lastAttackTime = 0L

    init {
        // Agregamos un componente de combate básico para que puedas
        // llamar a performAttack() desde tus eventos (ej: PlayerInteractEvent)
        character.addComponent(CombatComponent::class.java, object : CombatComponent {
            override fun onEnable(character: Character) {}
            override fun onDisable() {}
            
            override fun performAttack(attackId: String) {
                this@WardenKiller.attack()
            }

            override fun takeDamage(amount: Double, source: Any?): Boolean {
                return true
            }
        })
    }

    /**
     * Lógica de combo simple: Alterna entre swipe 1, 2 y 3.
     */
    fun attack() {
        val now = System.currentTimeMillis()
        
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
        // El framework reproducirá la animación y al terminar volverá a IdleState.
        transitionTo(state, force = true)
        
        lastAttackTime = now
        comboStep = (comboStep + 1) % 3 // Cicla entre 0, 1 y 2
    }

    /**
     * Aturde al Warden temporalmente.
     * Puedes llamar a esto cuando alguien le tire un pallet, por ejemplo.
     */
    fun applyStun() {
        transitionTo(WardenStunState, force = true)
    }
}
