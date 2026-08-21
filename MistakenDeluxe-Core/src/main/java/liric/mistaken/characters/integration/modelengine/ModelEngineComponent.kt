package liric.mistaken.characters.integration.modelengine

import com.ticxo.modelengine.api.ModelEngineAPI
import com.ticxo.modelengine.api.entity.Dummy
import com.ticxo.modelengine.api.model.ActiveModel
import com.ticxo.modelengine.api.model.ModeledEntity
import liric.mistaken.characters.components.ModelComponent
import liric.mistaken.characters.core.Character
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class ModelEngineComponent(override val modelId: String) : ModelComponent {

    private lateinit var character: Character
    private var modeledEntity: ModeledEntity? = null
    private var activeModel: ActiveModel? = null
    private var dummy: Dummy<Player>? = null
    private var taskId: Int = -1
    private var lastLocation: Location? = null
    private var modelAdded: Boolean = false
    private var walkTicks: Int = 0

    override fun onEnable(character: Character) {
        this.character = character
    }

    override fun onDisable() {
        despawn()
    }

    override fun spawn() {
        val player = character.entity as? Player ?: return

        val blueprint = ModelEngineAPI.getBlueprint(modelId)
        if (blueprint == null) {
            Bukkit.getLogger().warning("[ModelEngineComponent] No se pudo encontrar el modelo '$modelId' en ModelEngine.")
            return
        }

        
        
        dummy = Dummy(player)
        dummy?.setLocation(player.location)
        dummy?.yHeadRot = player.location.yaw
        dummy?.xHeadRot = player.location.pitch
        dummy?.yBodyRot = player.location.yaw
        dummy?.setForceViewing(player, true)

        modeledEntity = ModelEngineAPI.createModeledEntity(dummy)
        
        
        modeledEntity?.base?.setCollidableWith(player, false)
        
        activeModel = ModelEngineAPI.createActiveModel(blueprint)
        if (activeModel != null) {
            activeModel!!.isHitboxVisible = false 
            activeModel!!.setHitboxScale(0.0) 
            
        }
        
        modeledEntity?.base?.setMaxStepHeight(1.5)

        
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, Int.MAX_VALUE, 0, false, false))

        
        taskId = Bukkit.getScheduler().runTaskTimer(liric.mistaken.Mistaken.instance, Runnable {
            syncDummy(player)
        }, 0L, 1L).taskId
    }

    private fun syncDummy(player: Player) {
        val currentDummy = dummy ?: return
        val loc = player.location
        val lastLoc = lastLocation ?: loc

        
        if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, Int.MAX_VALUE, 0, false, false))
        }

        
        val session = liric.mistaken.Mistaken.instance.sessionManager.getSession(player)
        val shouldShow = session == null || session.currentState == liric.mistaken.game.enums.GameState.INGAME

        if (shouldShow && !modelAdded) {
            activeModel?.let { modeledEntity?.addModel(it, true) }
            modelAdded = true
        } else if (!shouldShow && modelAdded) {
            activeModel?.let { modeledEntity?.removeModel(it.blueprint.name) }
            modelAdded = false
        }

        
        val dx = loc.x - lastLoc.x
        val dz = loc.z - lastLoc.z
        val distSq = dx * dx + dz * dz

        if (distSq > 0.0001) {
            walkTicks = 3
        } else if (walkTicks > 0) {
            walkTicks--
        }

        currentDummy.isWalking = walkTicks > 0
        currentDummy.isJumping = !player.isOnGround

        
        currentDummy.setLocation(loc)
        currentDummy.yHeadRot = loc.yaw
        currentDummy.yBodyRot = loc.yaw
        currentDummy.xHeadRot = loc.pitch

        lastLocation = loc.clone()
    }

    override fun despawn() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId)
            taskId = -1
        }
        
        val player = character.entity as? Player
        player?.removePotionEffect(PotionEffectType.INVISIBILITY)

        if (modeledEntity != null) {
            try {
                modeledEntity?.destroy()
            } catch (e: Exception) {
                org.bukkit.Bukkit.getLogger().warning("[ModelEngineComponent] Error destruyendo entidad: ${e.message}")
            }
            modeledEntity = null
            activeModel = null
            dummy = null
            modelAdded = false
        }
    }

    override fun setScale(scale: Float) {
        activeModel?.setScale(scale.toDouble())
    }

    override fun setTint(rgb: Int?) {
        if (rgb == null) {
            activeModel?.defaultTint = org.bukkit.Color.WHITE 
        } else {
            activeModel?.defaultTint = org.bukkit.Color.fromRGB(rgb)
        }
    }

    override fun forceUpdate() {
        
    }

    /**
     * Acceso interno para el AnimationComponent
     */
    fun getActiveModel(): ActiveModel? {
        return activeModel
    }
}
