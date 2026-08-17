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

        // SOLUCIÓN PARA F5: Usamos un Dummy en lugar del Player directamente.
        // Esto permite forzar que el propio jugador vea el modelo.
        dummy = Dummy(player)
        dummy?.setLocation(player.location)
        dummy?.yHeadRot = player.location.yaw
        dummy?.xHeadRot = player.location.pitch
        dummy?.yBodyRot = player.location.yaw
        dummy?.setForceViewing(player, true)
        dummy?.isDetectingPlayers = false // FIX SLABS: Evitar que el Dummy detecte o colisione con jugadores

        modeledEntity = ModelEngineAPI.createModeledEntity(dummy)
        
        // FIX RUBBERBAND: Evitar que el jugador colisione con el hitbox de su propio modelo
        modeledEntity?.base?.setCollidableWith(player, false)
        
        activeModel = ModelEngineAPI.createActiveModel(blueprint)
        if (activeModel != null) {
            activeModel!!.isHitboxVisible = false // Esto solo lo oculta visualmente
            activeModel!!.setHitboxScale(0.0) // FIX SLABS: Elimina físicamente el hitbox reduciéndolo a 0
            // FIX CINEMATIC: No añadimos el modelo aquí. Lo añadiremos en syncDummy cuando estemos INGAME.
        }
        
        modeledEntity?.base?.setMaxStepHeight(1.5)

        // Esconder al jugador real para que no se sobreponga
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, Int.MAX_VALUE, 0, false, false))

        // Sincronizar Dummy cada tick para animaciones y movimiento
        taskId = Bukkit.getScheduler().runTaskTimer(liric.mistaken.Mistaken.instance, Runnable {
            syncDummy(player)
        }, 0L, 1L).taskId
    }

    private fun syncDummy(player: Player) {
        val currentDummy = dummy ?: return
        val loc = player.location
        val lastLoc = lastLocation ?: loc

        // FIX VISIBILIDAD: Asegurar que el jugador siga invisible aunque otro plugin limpie los efectos
        if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, Int.MAX_VALUE, 0, false, false))
        }

        // FIX CINEMATIC: Solo mostrar el modelo si el juego ya empezó (o si no hay sesión activa)
        val session = liric.mistaken.Mistaken.instance.sessionManager.getSession(player)
        val shouldShow = session == null || session.currentState == liric.mistaken.game.enums.GameState.INGAME

        if (shouldShow && !modelAdded) {
            activeModel?.let { modeledEntity?.addModel(it, true) }
            modelAdded = true
        } else if (!shouldShow && modelAdded) {
            activeModel?.let { modeledEntity?.removeModel(it.blueprint.name) }
            modelAdded = false
        }

        // Detectar si se movió para la animación 'walk' por defecto de ModelEngine
        val moved = loc.x != lastLoc.x || loc.z != lastLoc.z || loc.y != lastLoc.y
        currentDummy.isWalking = moved
        currentDummy.isJumping = !player.isOnGround

        // Actualizar posición
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
            dummy?.let { ModelEngineAPI.removeModeledEntity(it.uuid) }
            modeledEntity?.destroy()
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
            activeModel?.defaultTint = org.bukkit.Color.WHITE // Reset tint
        } else {
            activeModel?.defaultTint = org.bukkit.Color.fromRGB(rgb)
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
