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
        val viewer = (event.getPlayer() as? Player) ?: return
        val packetType = event.packetType

        
        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            val spawn = WrapperPlayServerSpawnEntity(event)
            
            if (manager.isHidden(spawn.uuid.get(), viewer.uniqueId)) {
                event.isCancelled = true
            }
        }
        
        
        else if (packetType == PacketType.Play.Server.ENTITY_TELEPORT) {
            val tp = WrapperPlayServerEntityTeleport(event)
            if (manager.isHidden(tp.entityId, viewer.uniqueId)) {
                event.isCancelled = true
            }
        }

        
        else if (packetType == PacketType.Play.Server.ENTITY_METADATA) {
            val meta = WrapperPlayServerEntityMetadata(event)
            if (manager.isHidden(meta.entityId, viewer.uniqueId)) {
                event.isCancelled = true
            }
        }

        
        else if (packetType == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            val info = WrapperPlayServerPlayerInfoUpdate(event)
            val entries = info.entries
            var shouldCancel = false
            
            val newEntries = entries.filter { entry ->
                if (manager.isHidden(entry.profileId, viewer.uniqueId)) {
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
                    
                    info.entries = newEntries
                }
            }
        }

        
        else if (packetType == PacketType.Play.Server.TEAMS) {
            val teamsPacket = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams(event)
            val infoOpt = teamsPacket.teamInfo
            
            if (infoOpt.isPresent) {
                val info = infoOpt.get()
                info.tagVisibility = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility.NEVER
            }
        }
    }
}
