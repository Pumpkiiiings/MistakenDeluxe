package liric.mistaken.models.integration.bettermodel

import kr.toxicity.model.api.animation.AnimationIterator
import kr.toxicity.model.api.animation.AnimationModifier
import liric.mistaken.models.components.AnimationComponent
import liric.mistaken.models.components.ModelComponent
import liric.mistaken.models.core.Character

/**
 * Implementación de AnimationComponent utilizando BetterModel.
 */
class BetterModelAnimationComponent : AnimationComponent {

    private lateinit var character: Character
    
    
    

    override fun onEnable(character: Character) {
        this.character = character
    }

    override fun onDisable() {
        stopAll()
    }

    override fun play(
        animationName: String,
        speed: Float,
        loop: Boolean,
        priority: Int,
        onComplete: (() -> Unit)?
    ) {
        val tracker = getTracker()
        if (tracker == null) {
            onComplete?.invoke()
            return
        }

        val modifier = AnimationModifier.builder()
            .type(if (loop) AnimationIterator.Type.LOOP else AnimationIterator.Type.PLAY_ONCE)
            .speed(speed)
            .priority(priority)
            .start(3)
            .end(3)
            .build()

        if (onComplete != null) {
            tracker.animate(animationName, modifier) {
                onComplete()
            }
        } else {
            tracker.animate(animationName, modifier)
        }
    }

    override fun stop(animationName: String) {
        getTracker()?.stopAnimation(animationName)
    }

    override fun replace(oldAnimation: String, newAnimation: String, speed: Float, loop: Boolean) {
        val tracker = getTracker() ?: return
        
        val modifier = AnimationModifier.builder()
            .type(if (loop) AnimationIterator.Type.LOOP else AnimationIterator.Type.PLAY_ONCE)
            .speed(speed)
            .start(3)
            .end(3)
            .build()
            
        tracker.replace(oldAnimation, newAnimation, modifier)
    }

    override fun stopAll() {
        
        
        
    }

    private fun getTracker(): kr.toxicity.model.api.tracker.EntityTracker? {
        val modelComp = character.getComponent(ModelComponent::class.java) as? BetterModelComponent
        return modelComp?.getTracker()
    }
}
