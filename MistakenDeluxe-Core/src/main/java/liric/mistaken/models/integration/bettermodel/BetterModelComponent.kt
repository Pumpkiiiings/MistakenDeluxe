package liric.mistaken.models.integration.bettermodel

import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter
import kr.toxicity.model.api.tracker.EntityTracker
import kr.toxicity.model.api.tracker.TrackerModifier
import liric.mistaken.models.components.ModelComponent
import liric.mistaken.models.core.Character

/**
 * Implementación de ModelComponent utilizando BetterModel.
 */
class BetterModelComponent(override val modelId: String) : ModelComponent {

    private lateinit var character: Character
    private var tracker: EntityTracker? = null

    override fun onEnable(character: Character) {
        this.character = character
    }

    override fun onDisable() {
        despawn()
    }

    override fun spawn() {
        if (tracker != null && !tracker!!.isClosed) return

        val renderer = kr.toxicity.model.api.BetterModel.modelOrNull(modelId)
        if (renderer == null) {
            val keys = kr.toxicity.model.api.BetterModel.modelKeys().joinToString(", ")
            org.bukkit.Bukkit.getLogger().warning("[BetterModelComponent] No se pudo encontrar el modelo '$modelId'. Modelos disponibles: $keys")
            return
        }
        
        val platformEntity = BukkitAdapter.adapt(character.entity)
        val baseEntity = kr.toxicity.model.api.entity.BaseEntity.of(platformEntity)

        val modifier = TrackerModifier.builder()
            .damageAnimation(false) 
            .build()

        tracker = renderer.getOrCreate(baseEntity, modifier) { t ->
            t.hideOption(kr.toxicity.model.api.tracker.EntityHideOption.DEFAULT)
        }
        
        org.bukkit.Bukkit.getLogger().info("[BetterModelComponent] Tracker creado para $modelId. Scheduled: ${tracker?.isScheduled()}")
    }

    override fun despawn() {
        if (tracker != null) {
            tracker?.close()
            tracker = null
        }
    }

    override fun setScale(scale: Float) {
        
        
    }

    override fun setTint(rgb: Int?) {
        if (tracker == null || tracker!!.isClosed) return
        
        if (rgb != null) {
            tracker!!.update(kr.toxicity.model.api.tracker.TrackerUpdateAction.Tint(rgb))
        } else {
            tracker!!.update(kr.toxicity.model.api.tracker.TrackerUpdateAction.PreviousTint.INSTANCE)
        }
    }

    override fun forceUpdate() {
        tracker?.forceUpdate(true)
    }

    /**
     * Acceso interno para el AnimationComponent
     */
    fun getTracker(): EntityTracker? {
        return if (tracker != null && !tracker!!.isClosed) tracker else null
    }
}
