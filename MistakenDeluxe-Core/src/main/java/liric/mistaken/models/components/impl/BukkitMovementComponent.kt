package liric.mistaken.models.components.impl

import liric.mistaken.models.components.MovementComponent
import liric.mistaken.models.components.StateComponent
import liric.mistaken.models.core.Character
import liric.mistaken.models.states.FallState
import liric.mistaken.models.states.IdleState
import liric.mistaken.models.states.RunState
import liric.mistaken.models.states.WalkState
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
        
        
        val dx = currentLocation.x - lastLoc.x
        val dz = currentLocation.z - lastLoc.z
        currentVelocity = sqrt(dx * dx + dz * dz)
        
        
        if (currentVelocity > 0.01) {
            ticksStationary = 0
            isMoving = true
        } else {
            ticksStationary++
            if (ticksStationary > 2) { 
                isMoving = false
            }
        }
        
        isGrounded = entity.isOnGround
        
        lastLocation = currentLocation

        val stateComponent = character.getComponent(StateComponent::class.java) ?: return

        
        
        
        
        if (!isGrounded && entity.velocity.y < -0.1) {
            
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
