package liric.mistaken.game.managers.cinematic.profiles

import com.ticxo.modelengine.api.ModelEngineAPI
import com.ticxo.modelengine.api.entity.Dummy
import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.CameraStyle
import liric.mistaken.game.managers.cinematic.CinematicProfile
import liric.mistaken.game.managers.cinematic.DisplayManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player

class WardenProfile : CinematicProfile {
    override val id = "warden"
    override val isFloating = false
    override val introCameraStyle = CameraStyle.PAN_UP_REVEAL
    override val outroCameraStyle = CameraStyle.ORBIT_ZOOM_IN
    
    // El usuario confirmó que la animación 'spawn' dura 6 segundos (120 ticks)
    override val introDuration: Int = 120

    override fun getIntroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        val mm = MiniMessage.miniMessage()
        val title = mm.deserialize("<gradient:#004e6e:#00a399><b>EL WARDEN</b></gradient>")
        val subtitle = mm.deserialize("<gray>Cuidado por donde pisas...</gray>")
        return Pair(title, subtitle)
    }

    override fun getOutroTexts(plugin: Mistaken, realName: String): Pair<Component, Component> {
        val mm = MiniMessage.miniMessage()
        val title = mm.deserialize("<gradient:#004e6e:#00a399><b>EL WARDEN</b></gradient>")
        val subtitle = mm.deserialize("<red>No pudiste escapar del abismo...</red>")
        return Pair(title, subtitle)
    }

    override fun getDialogs(isIntro: Boolean): List<String> {
        return if (isIntro) {
            listOf(
                "<dark_aqua>...",
                "<dark_aqua>*ruidos de eco*"
            )
        } else {
            listOf(
                "<dark_aqua>La oscuridad te consume...",
                "<dark_red>Tu alma nos pertenece."
            )
        }
    }

    override fun applyPose(dummy: ArmorStand, isIntro: Boolean) {
        // En ModelEngine no necesitamos poses del ArmorStand porque el modelo lo cubre por completo
        dummy.isVisible = false // Ocultamos el armorstand
    }

    override fun applyEquipment(killer: Player, dummy: ArmorStand, isIntro: Boolean) {
        // No necesitamos equipamiento
    }

    override fun playEffects(
        plugin: Mistaken,
        loc: Location,
        dummy: ArmorStand,
        isIntro: Boolean,
        displayManager: DisplayManager,
        viewers: List<Player>
    ) {
        if (!isIntro) return
        
        // Creamos una entidad dummy de ModelEngine conectada al ArmorStand
        val meDummy = Dummy<ArmorStand>(dummy)
        meDummy.setLocation(loc)
        meDummy.yHeadRot = loc.yaw
        meDummy.yBodyRot = loc.yaw
        meDummy.xHeadRot = loc.pitch
        
        val modeledEntity = ModelEngineAPI.createModeledEntity(meDummy)
        val activeModel = ModelEngineAPI.createActiveModel("warden")
        
        if (activeModel != null) {
            activeModel.isHitboxVisible = false
            activeModel.setHitboxScale(0.0)
            modeledEntity.addModel(activeModel, true)
            
            // Reproducimos la animación 'spawn' (dura 6s)
            activeModel.animationHandler.playAnimation("spawn", 1.0, 0.0, 1.0, true)
        }
        
        // Destruir el modelo cuando la cinemática acabe
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (activeModel != null) {
                meDummy.isRemoved = true
            }
        }, introDuration.toLong())
    }
}
