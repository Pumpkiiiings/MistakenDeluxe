package liric.mistaken.characters.components.impl

import liric.mistaken.characters.components.MovementComponent
import liric.mistaken.characters.components.StateComponent
import liric.mistaken.characters.core.Character
import liric.mistaken.characters.states.FallState
import liric.mistaken.characters.states.IdleState
import liric.mistaken.characters.states.RunState
import liric.mistaken.characters.states.WalkState
import org.bukkit.Location
import org.bukkit.entity.Player
import kotlin.math.sqrt

/**
 * Implementación de MovementComponent para Bukkit.
 * Calcula el movimiento basándose en deltas de posición durante el tick(),
 * lo cual es más eficiente que usar PlayerMoveEvent.
 */
class BukkitMovementComponent : MovementComponent {

    private lateinit var character: Character
    private var lastLocation: Location? = null
    private var ticksStationary = 0
    
    override var currentVelocity: Double = 0.0
        private set
        
    override var isGrounded: Boolean = true
        private set
        
    override var isMoving: Boolean = false
        private set
        
    private var isEnabled = true

    override fun onEnable(character: Character) {
        this.character = character
        lastLocation = character.entity.location
    }

    override fun onDisable() {
        lastLocation = null
    }

    override fun setMovementEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    override fun tick() {
        if (!isEnabled) return
        
        val entity = character.entity
        val currentLocation = entity.location
        val lastLoc = lastLocation ?: currentLocation
        
        // Calcular velocidad horizontal (delta XZ)
        val dx = currentLocation.x - lastLoc.x
        val dz = currentLocation.z - lastLoc.z
        currentVelocity = sqrt(dx * dx + dz * dz)
        
        // Consideramos "movimiento" si la distancia horizontal supera un mínimo
        if (currentVelocity > 0.01) {
            ticksStationary = 0
            isMoving = true
        } else {
            ticksStationary++
            if (ticksStationary > 2) { // Debounce de 2 ticks (100ms)
                isMoving = false
            }
        }
        
        isGrounded = entity.isOnGround
        
        lastLocation = currentLocation

        val stateComponent = character.getComponent(StateComponent::class.java) ?: return

        // Aquí viene la magia: Notificamos a la máquina de estados basándonos en la física pura.
        // La máquina de estados decidirá si acepta esta transición (por ejemplo, si el player 
        // está STUN, la máquina de estados ignorará el WalkState o RunState).
        
        if (!isGrounded && entity.velocity.y < -0.1) {
            // Está cayendo (no solo saltando hacia arriba)
            stateComponent.transitionTo(FallState)
        } else if (isMoving) {
            if (entity is Player && entity.isSprinting) {
                stateComponent.transitionTo(RunState)
            } else {
                stateComponent.transitionTo(WalkState)
            }
        } else {
            stateComponent.transitionTo(IdleState)
        }
    }
}
