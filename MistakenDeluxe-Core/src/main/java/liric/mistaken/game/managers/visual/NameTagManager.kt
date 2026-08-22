package liric.mistaken.game.managers.visual

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.*
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import liric.mistaken.utils.color.ColorTranslator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Packet-based nametag system.
 * Uses virtual TextDisplay entities via PacketEvents — no real Bukkit entities,
 * no scoreboard teams. The vanilla nametag is hidden via a packet-level team
 * with NAME_TAG_VISIBILITY = NEVER, sent directly to each client.
 */
class NameTagManager(private val plugin: Mistaken) {

    private val nametags = ConcurrentHashMap<UUID, VirtualNametag>()
    private val entityIdCounter = AtomicInteger(Int.MAX_VALUE / 2)
    private val hasPAPI by lazy { Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null }





    

    fun setupPlayer(player: Player) {
        removePlayer(player)



        val entityId = entityIdCounter.decrementAndGet()
        val tag = VirtualNametag(entityId)
        nametags[player.uniqueId] = tag

        
        Bukkit.getOnlinePlayers().forEach { viewer ->
            spawnForViewer(player, viewer, tag)
        }
    }

    fun removePlayer(player: Player) {
        val tag = nametags.remove(player.uniqueId) ?: return

        val destroyPacket = WrapperPlayServerDestroyEntities(tag.entityId)
        
        Bukkit.getOnlinePlayers().forEach { viewer ->
            PacketEvents.getAPI().playerManager.sendPacket(viewer, destroyPacket)
        }
    }

    fun resetViewers(player: Player) {
        val tag = nametags[player.uniqueId]
        if (tag != null) {
            val destroyPacket = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities(tag.entityId)
            Bukkit.getOnlinePlayers().forEach { viewer ->
                com.github.retrooper.packetevents.PacketEvents.getAPI().playerManager.sendPacket(viewer, destroyPacket)
            }
            tag.confirmedViewers.clear()
        }
        nametags.values.forEach { it.confirmedViewers.remove(player.uniqueId) }
    }

    fun removeAll() {
        nametags.keys.toList().forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                removePlayer(player)
            } else {
                nametags.remove(uuid)
            }
        }
    }

    

    fun updatePlayer(player: Player) {
        val tag = nametags[player.uniqueId] ?: return

        val isHidden = plugin.spectatorManager.isSpectator(player)
                || player.gameMode == org.bukkit.GameMode.SPECTATOR
                || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)
                || player.isSneaking

        val session = plugin.sessionManager.getSession(player)
        val isIngame = session != null
        val configPath = if (isIngame) "nametags.ingame" else "nametags.global"

        val lines = plugin.config.getStringList("$configPath.lines")
        val size = plugin.config.getDouble("$configPath.size", 1.0).toFloat()
        val shadow = plugin.config.getBoolean("$configPath.shadow", false)
        val bgColorStr = plugin.config.getString("$configPath.background-color", "transparent")?.lowercase() ?: "transparent"

        val bgColorInt = parseBackgroundColor(bgColorStr)

        val textComponent: net.kyori.adventure.text.Component = if (isHidden) {
            net.kyori.adventure.text.Component.empty()
        } else {
            val colorStr = if (isIngame) {
                if (session!!.isKiller(player.uniqueId)) "<red>" else "<green>"
            } else {
                "<gray>"
            }
            val health = String.format(java.util.Locale.US, "%.1f", player.health)

            val processedLines = lines.map { line ->
                var currentLine = line
                    .replace("%name%", player.name)
                    .replace("%color%", colorStr)
                    .replace("%health%", health)
                if (hasPAPI) {
                    currentLine = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, currentLine)
                }
                currentLine
            }.joinToString("\n")

            ColorTranslator.translate(processedLines)
        }

        
        val metadata = mutableListOf<EntityData<*>>()

        
        metadata.add(EntityData(23, EntityDataTypes.ADV_COMPONENT, textComponent))
        
        metadata.add(EntityData(25, EntityDataTypes.INT, bgColorInt))
        
        val textFlags: Byte = if (shadow) 0x01.toByte() else 0x00.toByte()
        metadata.add(EntityData(27, EntityDataTypes.BYTE, textFlags))
        
        metadata.add(EntityData(11, EntityDataTypes.VECTOR3F, Vector3f(0f, 0.3f, 0f)))
        
        metadata.add(EntityData(12, EntityDataTypes.VECTOR3F, Vector3f(size, size, size)))
        
        metadata.add(EntityData(17, EntityDataTypes.FLOAT, 1.0f))

        val metadataPacket = WrapperPlayServerEntityMetadata(tag.entityId, metadata)
        
        
        val passengerIds = player.passengers.map { it.entityId }.toIntArray() + tag.entityId
        val passengerPacket = WrapperPlayServerSetPassengers(player.entityId, passengerIds)

        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (!tag.confirmedViewers.contains(viewer.uniqueId)) {
                spawnForViewer(player, viewer, tag)
            }
            PacketEvents.getAPI().playerManager.sendPacket(viewer, metadataPacket)
            PacketEvents.getAPI().playerManager.sendPacket(viewer, passengerPacket)
        }
    }

    

    private fun spawnForViewer(owner: Player, viewer: Player, tag: VirtualNametag) {
        val loc = owner.location
        val spawnPacket = WrapperPlayServerSpawnEntity(
            tag.entityId,
            UUID.randomUUID(),
            EntityTypes.TEXT_DISPLAY,
            com.github.retrooper.packetevents.protocol.world.Location(loc.x, loc.y, loc.z, 0f, 0f),
            0f,
            0,
            Vector3d(0.0, 0.0, 0.0)
        )

        
        val metadata = mutableListOf<EntityData<*>>()
        
        metadata.add(EntityData(15, EntityDataTypes.BYTE, 0x03.toByte()))
        
        metadata.add(EntityData(23, EntityDataTypes.ADV_COMPONENT, net.kyori.adventure.text.Component.empty()))
        
        metadata.add(EntityData(25, EntityDataTypes.INT, 0))
        
        metadata.add(EntityData(11, EntityDataTypes.VECTOR3F, Vector3f(0f, 0.3f, 0f)))
        
        metadata.add(EntityData(17, EntityDataTypes.FLOAT, 1.0f))

        val metadataPacket = WrapperPlayServerEntityMetadata(tag.entityId, metadata)
        
        val passengerIds = owner.passengers.map { it.entityId }.toIntArray() + tag.entityId
        val passengerPacket = WrapperPlayServerSetPassengers(owner.entityId, passengerIds)

        PacketEvents.getAPI().playerManager.sendPacket(viewer, spawnPacket)
        PacketEvents.getAPI().playerManager.sendPacket(viewer, metadataPacket)
        PacketEvents.getAPI().playerManager.sendPacket(viewer, passengerPacket)

        tag.confirmedViewers.add(viewer.uniqueId)
    }



    private fun parseBackgroundColor(str: String): Int {
        if (str == "transparent") return 0
        if (!str.startsWith("#")) return 0
        return try {
            val hex = str.removePrefix("#")
            when (hex.length) {
                6 -> (0xFF shl 24) or hex.toInt(16) 
                8 -> hex.toLong(16).toInt()           
                else -> 0
            }
        } catch (_: Exception) { 0 }
    }

    data class VirtualNametag(
        val entityId: Int,
        val confirmedViewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    )
}
