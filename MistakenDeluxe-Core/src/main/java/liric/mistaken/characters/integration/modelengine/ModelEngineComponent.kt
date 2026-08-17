package liric.mistaken.characters.integration.modelengine

import com.ticxo.modelengine.api.ModelEngineAPI
import com.ticxo.modelengine.api.model.ActiveModel
import com.ticxo.modelengine.api.model.ModeledEntity
import liric.mistaken.characters.components.ModelComponent
import liric.mistaken.characters.core.Character
import org.bukkit.Bukkit

/**
 * Implementación de ModelComponent utilizando ModelEngine 4.1.0.
 */
class ModelEngineComponent(override val modelId: String) : ModelComponent {

    private lateinit var character: Character
    private var modeledEntity: ModeledEntity? = null
    private var activeModel: ActiveModel? = null

    override fun onEnable(character: Character) {
        this.character = character
    }

    override fun onDisable() {
        despawn()
    }

    override fun spawn() {
        if (modeledEntity != null && !modeledEntity!!.isDestroyed) return

        val blueprint = ModelEngineAPI.getBlueprint(modelId)
        if (blueprint == null) {
            Bukkit.getLogger().warning("[ModelEngineComponent] No se pudo encontrar el modelo '$modelId' en ModelEngine.")
            return
        }

        val baseEntity = character.entity

        // Crear ModeledEntity
        modeledEntity = ModelEngineAPI.createModeledEntity(baseEntity)
        modeledEntity?.setBaseEntityVisible(false) // Esconder al jugador base

        // Crear y añadir ActiveModel
        activeModel = ModelEngineAPI.createActiveModel(blueprint)
        if (activeModel != null) {
            modeledEntity?.addModel(activeModel!!, true)
        }

        Bukkit.getLogger().info("[ModelEngineComponent] ModeledEntity creado para $modelId en la entidad ${baseEntity.name}.")
    }

    override fun despawn() {
        if (modeledEntity != null) {
            modeledEntity?.setBaseEntityVisible(true)
            
            // ModelEngine recomienda remover a través de API
            ModelEngineAPI.removeModeledEntity(character.entity.uniqueId)
            
            modeledEntity?.destroy()
            modeledEntity = null
            activeModel = null
        }
    }

    override fun setScale(scale: Float) {
        activeModel?.setScale(scale.toDouble())
    }

    override fun setTint(rgb: Int?) {
        if (rgb != null) {
            val color = org.bukkit.Color.fromRGB(rgb)
            activeModel?.setDefaultTint(color)
        } else {
            // No hay manera directa de limpiar el tint, se asume blanco puro o nulo
            activeModel?.setDefaultTint(org.bukkit.Color.WHITE)
        }
    }

    override fun forceUpdate() {
        // En ModelEngine los updates son automáticos, no requiere forceUpdate explícito
    }

    /**
     * Acceso interno para el AnimationComponent
     */
    fun getActiveModel(): ActiveModel? {
        return activeModel
    }
}
