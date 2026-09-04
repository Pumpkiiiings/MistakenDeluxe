package liric.mistaken.roles.common.triggers.traps

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object WorldTrapRegistry {

    private val traps = ConcurrentHashMap<BlockKey, TrapDefinition>()
    private var packetListener: PacketListenerAbstract? = null

    fun registerTrap(trap: TrapDefinition) {
        val key = BlockKey.fromLocation(trap.location)
        traps[key] = trap
    }

    fun unregisterTrap(location: Location) {
        val key = BlockKey.fromLocation(location)
        traps.remove(key)
    }

    fun cleanupByOwner(ownerUuid: UUID) {
        traps.entries.removeIf { it.value.ownerUuid == ownerUuid }
    }

    fun init(plugin: Mistaken) {
        if (packetListener != null) return
        
        packetListener = object : PacketListenerAbstract() {
            override fun onPacketReceive(event: PacketReceiveEvent) {
                if (event.packetType == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                    val packet = WrapperPlayClientPlayerBlockPlacement(event)
                    val pos = packet.blockPosition
                    val player = event.getPlayer<Player>()
                    
                    val worldUid = player.world.uid
                    val key = BlockKey(worldUid, pos.x, pos.y, pos.z)
                    
                    val trap = traps[key]
                    if (trap != null) {
                        event.isCancelled = true
                        
                        
                        player.scheduler.run(plugin, java.util.function.Consumer {
                            
                            val trapLoc = Location(player.world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                            trap.onTrigger(player, trapLoc)
                        }, null)
                    }
                }
            }
        }
        PacketEvents.getAPI().eventManager.registerListener(packetListener!!)
    }
}
