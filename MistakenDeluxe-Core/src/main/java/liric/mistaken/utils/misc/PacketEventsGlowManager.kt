package liric.mistaken.utils.misc

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.ChatColor
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PacketEventsGlowManager(val plugin: Plugin) : PacketListenerAbstract(PacketListenerPriority.HIGH) {

    
    private val activeGlows = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, NamedTextColor>>()
    private val createdTeams = ConcurrentHashMap<UUID, MutableSet<NamedTextColor>>()

    init {
        PacketEvents.getAPI().eventManager.registerListener(this)
    }

    private fun chatColorToNamed(color: ChatColor): NamedTextColor {
        return NamedTextColor.NAMES.value(color.name.lowercase()) ?: NamedTextColor.WHITE
    }

    private fun getEntityName(target: Entity): String {
        return if (target is Player) target.name else target.uniqueId.toString()
    }

    fun setGlowing(target: Entity, viewer: Player, color: ChatColor) {
        val namedColor = chatColorToNamed(color)
        val viewerMap = activeGlows.computeIfAbsent(viewer.uniqueId) { ConcurrentHashMap() }
        val currentColor = viewerMap[target.entityId]

        if (currentColor != namedColor) {
            if (!viewer.isOnline || !target.isValid) return
            
            // Si ya estaba en un equipo de otro color, sacarlo
            if (currentColor != null) {
                removeFromTeam(target, viewer, currentColor)
            }
            
            viewerMap[target.entityId] = namedColor

            // 1. Añadir al equipo de ese color
            val teamName = "mglow_${namedColor.toString()}"
            val viewerTeams = createdTeams.computeIfAbsent(viewer.uniqueId) { ConcurrentHashMap.newKeySet() }
            
            if (viewerTeams.add(namedColor)) {
                // Crear el equipo fantasma para el espectador
                val createTeam = WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.CREATE,
                    java.util.Optional.of(WrapperPlayServerTeams.ScoreBoardTeamInfo(
                        Component.empty(),
                        Component.empty(),
                        Component.empty(),
                        WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                        WrapperPlayServerTeams.CollisionRule.ALWAYS,
                        namedColor,
                        WrapperPlayServerTeams.OptionData.NONE
                    )),
                    emptyList<String>()
                )
                PacketEvents.getAPI().playerManager.sendPacket(viewer, createTeam)
            }

            // Añadir entidad al equipo
            val addEntity = WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                java.util.Optional.empty(),
                listOf(getEntityName(target))
            )
            PacketEvents.getAPI().playerManager.sendPacket(viewer, addEntity)

            // 2. Enviar metadata de brillar
            sendMetadataUpdate(target, viewer, true)
        }
    }

    private fun removeFromTeam(target: Entity, viewer: Player, color: NamedTextColor) {
        val teamName = "mglow_${color.toString()}"
        val removeEntity = WrapperPlayServerTeams(
            teamName,
            WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
            java.util.Optional.empty(),
            listOf(getEntityName(target))
        )
        PacketEvents.getAPI().playerManager.sendPacket(viewer, removeEntity)
    }

    private fun sendMetadataUpdate(target: Entity, viewer: Player, glowing: Boolean) {
        var bitmask: Byte = 0
        if (target is Player) {
            if (target.isSneaking) bitmask = (bitmask.toInt() or 0x02).toByte()
            if (target.isSprinting) bitmask = (bitmask.toInt() or 0x08).toByte()
            if (target.isSwimming) bitmask = (bitmask.toInt() or 0x10).toByte()
        }
        if (target is org.bukkit.entity.LivingEntity) {
            if (target.isGliding) bitmask = (bitmask.toInt() or 0x80).toByte()
        }
        if (target.isInsideVehicle) bitmask = (bitmask.toInt() or 0x04).toByte()
        if (target.isVisualFire) bitmask = (bitmask.toInt() or 0x01).toByte()
        
        if (glowing) {
            bitmask = (bitmask.toInt() or 0x40).toByte()
        }

        val metadata = WrapperPlayServerEntityMetadata(target.entityId, listOf(
            EntityData<Byte>(0, EntityDataTypes.BYTE, bitmask)
        ))
        PacketEvents.getAPI().playerManager.sendPacket(viewer, metadata)
    }

    fun unsetGlowing(target: Entity, viewer: Player) {
        unsetGlowing(target.entityId, viewer)
    }

    fun unsetGlowing(targetEntityId: Int, viewer: Player) {
        val viewerMap = activeGlows[viewer.uniqueId] ?: return
        val removedColor = viewerMap.remove(targetEntityId)
        
        if (removedColor != null && viewer.isOnline) {
            // Eliminar del equipo
            val entity = plugin.server.worlds.firstNotNullOfOrNull { it.entities.find { e -> e.entityId == targetEntityId } }
            if (entity != null) {
                removeFromTeam(entity, viewer, removedColor)
                sendMetadataUpdate(entity, viewer, false)
            }
        }
        if (viewerMap.isEmpty()) {
            activeGlows.remove(viewer.uniqueId)
        }
    }

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType == PacketType.Play.Server.ENTITY_METADATA) {
            val viewerUuid = event.user.uuid ?: return
            val viewerMap = activeGlows[viewerUuid] ?: return
            if (viewerMap.isEmpty()) return

            val packet = WrapperPlayServerEntityMetadata(event)
            if (viewerMap.containsKey(packet.entityId)) {
                var modified = false
                val dataList = packet.entityMetadata
                for (data in dataList) {
                    if (data.index == 0) {
                        val value = data.value
                        if (value is Byte) {
                            @Suppress("UNCHECKED_CAST")
                            val byteData = data as EntityData<Byte>
                            byteData.value = (value.toInt() or 0x40).toByte()
                            modified = true
                        }
                    }
                }
                
                if (modified) {
                    packet.entityMetadata = dataList
                }
            }
        }
    }

    fun disable() {
        PacketEvents.getAPI().eventManager.unregisterListener(this)
        activeGlows.clear()
        createdTeams.clear()
    }
}
