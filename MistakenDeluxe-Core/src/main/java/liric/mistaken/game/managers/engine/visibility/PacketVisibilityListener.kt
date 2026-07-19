package liric.mistaken.game.managers.engine.visibility

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import org.bukkit.entity.Player

class PacketVisibilityListener(private val manager: VisibilityManager) : PacketListenerAbstract() {

    override fun onPacketSend(event: PacketSendEvent) {
        val viewer = event.player as? Player ?: return
        val packetType = event.packetType

        // 1. Interceptar Spawn
        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            val spawn = WrapperPlayServerSpawnEntity(event)
            // Se debe buscar por UUID porque los Fake Displays usarán la lista de UUIDs
            if (manager.isHidden(spawn.uuid.get(), viewer.uniqueId)) {
                event.isCancelled = true
            }
        }
        
        // 2. Interceptar Teleport
        else if (packetType == PacketType.Play.Server.ENTITY_TELEPORT) {
            val tp = WrapperPlayServerEntityTeleport(event)
            if (manager.isHidden(tp.entityId, viewer.uniqueId)) {
                event.isCancelled = true
            }
        }

        // 3. Interceptar Metadata
        else if (packetType == PacketType.Play.Server.ENTITY_METADATA) {
            val meta = WrapperPlayServerEntityMetadata(event)
            if (manager.isHidden(meta.entityId, viewer.uniqueId)) {
                event.isCancelled = true
            }
        }

        // 4. Interceptar TAB
        else if (packetType == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            val info = WrapperPlayServerPlayerInfoUpdate(event)
            val entries = info.entries
            var shouldCancel = false
            
            val newEntries = entries.filter { entry ->
                if (manager.isHidden(entry.userProfile.uuid, viewer.uniqueId)) {
                    shouldCancel = true
                    false
                } else {
                    true
                }
            }
            
            if (shouldCancel) {
                if (newEntries.isEmpty()) {
                    event.isCancelled = true
                } else {
                    // Update the packet if there are remaining valid entries
                    info.entries = newEntries
                }
            }
        }
    }
}
