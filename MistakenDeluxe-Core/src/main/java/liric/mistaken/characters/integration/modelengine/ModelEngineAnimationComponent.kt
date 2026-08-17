package liric.mistaken.characters.integration.modelengine

import liric.mistaken.characters.components.AnimationComponent
import liric.mistaken.characters.components.ModelComponent
import liric.mistaken.characters.core.Character
import org.bukkit.Bukkit

/**
 * Implementación de AnimationComponent utilizando ModelEngine 4.1.0.
 */
class ModelEngineAnimationComponent : AnimationComponent {

    private lateinit var character: Character

    override fun onEnable(character: Character) {
        this.character = character
    }

    override fun onDisable() {
        // ModelEngine limpia sus animaciones cuando se destruye el ActiveModel
    }

    override fun play(
        animationName: String,
        speed: Float,
        loop: Boolean,
        priority: Int,
        onComplete: (() -> Unit)?
    ) {
        val meComponent = character.getComponent(ModelComponent::class.java) as? ModelEngineComponent
        val activeModel = meComponent?.getActiveModel()

        if (activeModel != null) {
            val handler = activeModel.animationHandler
            
            // ModelEngine playAnimation: (String animation, double lerpIn, double lerpOut, double speed, boolean force)
            val property = handler.playAnimation(animationName, 0.0, 0.0, speed.toDouble(), true)
            
            if (property != null && onComplete != null) {
                val lengthInSeconds = property.blueprintAnimation.length
                val delayTicks = (lengthInSeconds * 20.0 / speed).toLong().coerceAtLeast(1L)
                
                Bukkit.getScheduler().runTaskLater(liric.mistaken.Mistaken.instance, Runnable {
                    onComplete()
                }, delayTicks)
            }
        }
    }

    override fun stop(animationName: String) {
        val meComponent = character.getComponent(ModelComponent::class.java) as? ModelEngineComponent
        meComponent?.getActiveModel()?.animationHandler?.stopAnimation(animationName)
    }

    override fun replace(oldAnimation: String, newAnimation: String, speed: Float, loop: Boolean) {
        stop(oldAnimation)
        play(newAnimation, speed, loop)
    }

    override fun stopAll() {
        val meComponent = character.getComponent(ModelComponent::class.java) as? ModelEngineComponent
        meComponent?.getActiveModel()?.animationHandler?.forceStopAllAnimations()
    }
}
