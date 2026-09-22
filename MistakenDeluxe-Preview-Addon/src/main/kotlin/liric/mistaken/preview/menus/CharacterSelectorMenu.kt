package liric.mistaken.preview.menus

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import liric.mistaken.api.MistakenAPI
import liric.mistaken.preview.PreviewAddon
import liric.mistaken.preview.engine.PreviewCamera
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Vector3d
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CharacterSelectorMenu(val player: Player) {

    private val spawnLoc: Location? = PreviewAddon.instance.config.getLocation("locations.spawn")
    private val cameraLoc: Location? = PreviewAddon.instance.config.getLocation("locations.camera")
    
    // Displays nativos del Core (VirtualTextDisplay)
    var titleDisplay: liric.mistaken.packet.fake.VirtualTextDisplay? = null
    var leftArrowDisplay: liric.mistaken.packet.fake.VirtualTextDisplay? = null
    var rightArrowDisplay: liric.mistaken.packet.fake.VirtualTextDisplay? = null
    var selectButtonDisplay: liric.mistaken.packet.fake.VirtualTextDisplay? = null
    
    // El índice del asesino o superviviente que estamos viendo
    var currentIndex = 0
    val roles = PreviewAddon.instance.mistaken.killerManager.catalogo.keys.toList()

    companion object {
        val activeMenus = ConcurrentHashMap<UUID, CharacterSelectorMenu>()

        fun openMenu(player: Player) {
            val menu = CharacterSelectorMenu(player)
            activeMenus[player.uniqueId] = menu
            menu.render()
        }
    }

    fun render() {
        if (spawnLoc == null || cameraLoc == null) {
            player.sendMessage("§cPreview locations are not set up!")
            return
        }

        // 1. Bloquear cámara (Usando PIG falso)
        val camera = PreviewCamera(player)
        camera.spawnAt(cameraLoc)
        
        // 2. Renderizar displays flotantes usando MistakenDeluxe-Core PacketFactory
        val rightDir = spawnLoc.direction.clone().setY(0.0).crossProduct(org.bukkit.util.Vector(0, 1, 0)).normalize()
        val leftLoc = spawnLoc.clone().add(rightDir.clone().multiply(-2.0)).add(0.0, 1.0, 0.0)
        val rightLoc = spawnLoc.clone().add(rightDir.clone().multiply(2.0)).add(0.0, 1.0, 0.0)
        
        leftArrowDisplay = liric.mistaken.packet.PacketFactory.displays.buildTextDisplay(listOf(player), leftLoc) {
            it.text = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<yellow><b><</b>")
            it.billboard = org.bukkit.entity.Display.Billboard.CENTER
        }
        
        rightArrowDisplay = liric.mistaken.packet.PacketFactory.displays.buildTextDisplay(listOf(player), rightLoc) {
            it.text = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<yellow><b>></b>")
            it.billboard = org.bukkit.entity.Display.Billboard.CENTER
        }
        
        // El botón seleccionar estará más cerca de la cámara (abajo)
        val selectLoc = cameraLoc.clone().add(cameraLoc.direction.clone().multiply(2.0)).add(0.0, -0.5, 0.0)
        selectButtonDisplay = liric.mistaken.packet.PacketFactory.displays.buildTextDisplay(listOf(player), selectLoc) {
            it.text = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<green>[ SELECCIONAR ]")
            it.billboard = org.bukkit.entity.Display.Billboard.CENTER
        }
        
        player.sendMessage("§aWelcome to the Immersive Store!")
        if (roles.isNotEmpty()) updateRoleDisplay()
    }

    fun updateRoleDisplay() {
        if (roles.isEmpty()) return
        val roleId = roles[currentIndex]
        val killer = PreviewAddon.instance.mistaken.killerManager.getClassById(roleId)
        
        // Limpiar título anterior si existe
        titleDisplay?.remove()
        
        val titleLoc = spawnLoc!!.clone().add(0.0, 2.5, 0.0)
        titleDisplay = liric.mistaken.packet.PacketFactory.displays.buildTextDisplay(listOf(player), titleLoc) {
            it.text = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold><b>${killer?.name?.uppercase() ?: roleId}</b></gold>")
            it.billboard = org.bukkit.entity.Display.Billboard.CENTER
        }
        
        // TODO: Enviar paquetes para spawnear el FakePlayer o ModelEngine de este killer en spawnLoc
        player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
    }

    fun cycleLeft() {
        currentIndex = if (currentIndex - 1 < 0) roles.size - 1 else currentIndex - 1
        updateRoleDisplay()
    }

    fun cycleRight() {
        currentIndex = (currentIndex + 1) % roles.size
        updateRoleDisplay()
    }

    fun select() {
        val roleId = roles[currentIndex]
        // TODO: Database integration / comprar / equipar
        player.sendMessage("§aYou selected: \$roleId!")
        close()
    }

    fun close() {
        // Remover cámara
        PreviewCamera.activeCameras[player.uniqueId]?.remove()
        
        titleDisplay?.remove()
        leftArrowDisplay?.remove()
        rightArrowDisplay?.remove()
        selectButtonDisplay?.remove()
        
        activeMenus.remove(player.uniqueId)
    }
}
