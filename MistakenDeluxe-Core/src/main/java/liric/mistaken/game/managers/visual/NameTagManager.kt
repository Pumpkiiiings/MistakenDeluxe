package liric.mistaken.game.managers.visual

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.*
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator
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

    private companion object {
        const val TEAM_NAME = "mist_hide_nt"
    }

    // ── Setup / Teardown ──

    fun setupPlayer(player: Player) {
        removePlayer(player)

        // Hide the vanilla nametag for this player on ALL viewers
        Bukkit.getOnlinePlayers().forEach { viewer ->
            sendTeamHidePacket(viewer, player.name, isAdd = true)
        }

        // Also ensure new player sees all existing players' vanilla tags hidden
        nametags.keys.forEach { ownerUUID ->
            val owner = Bukkit.getPlayer(ownerUUID) ?: return@forEach
            sendTeamHidePacket(player, owner.name, isAdd = true)
        }

        val entityId = entityIdCounter.decrementAndGet()
        val tag = VirtualNametag(entityId)
        nametags[player.uniqueId] = tag

        // Spawn the virtual TextDisplay to all viewers
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer.uniqueId != player.uniqueId) {
                spawnForViewer(player, viewer, tag)
            }
        }
    }

    fun removePlayer(player: Player) {
        val tag = nametags.remove(player.uniqueId) ?: return

        // Destroy the virtual entity for all viewers
        val destroyPacket = WrapperPlayServerDestroyEntities(tag.entityId)
        Bukkit.getOnlinePlayers().forEach { viewer ->
            PacketEvents.getAPI().playerManager.sendPacket(viewer, destroyPacket)
        }

        // Unhide vanilla nametag
        Bukkit.getOnlinePlayers().forEach { viewer ->
            sendTeamHidePacket(viewer, player.name, isAdd = false)
        }
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

    // ── Update (called every tick by VisualUpdateService) ──

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

        val textComponent: Component = if (isHidden) {
            Component.empty()
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
            }.joinToString("<br>")

            ColorTranslator.translate(processedLines)
        }

        // Build metadata update
        val metadata = mutableListOf<EntityData<*>>()

        // Index 23: Text (ADV_COMPONENT)
        metadata.add(EntityData(23, EntityDataTypes.ADV_COMPONENT, textComponent))
        // Index 25: Background color (INT) — ARGB packed
        metadata.add(EntityData(25, EntityDataTypes.INT, bgColorInt))
        // Index 27: Text opacity/flags — bit 0x08 = shadow
        val textFlags: Byte = if (shadow) 0x01.toByte() else 0x00.toByte()
        metadata.add(EntityData(27, EntityDataTypes.BYTE, textFlags))
        // Index 11: Translation (VECTOR3F) — Y offset above head
        metadata.add(EntityData(11, EntityDataTypes.VECTOR3F, Vector3f(0f, 0.3f, 0f)))
        // Index 12: Scale (VECTOR3F)
        metadata.add(EntityData(12, EntityDataTypes.VECTOR3F, Vector3f(size, size, size)))

        val metadataPacket = WrapperPlayServerEntityMetadata(tag.entityId, metadata)

        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer.uniqueId != player.uniqueId) {
                // Ensure passenger chain is intact
                if (!tag.confirmedViewers.contains(viewer.uniqueId)) {
                    spawnForViewer(player, viewer, tag)
                }
                PacketEvents.getAPI().playerManager.sendPacket(viewer, metadataPacket)
            }
        }
    }

    // ── Internals ──

    private fun spawnForViewer(owner: Player, viewer: Player, tag: VirtualNametag) {
        val loc = owner.location
        val spawnPacket = WrapperPlayServerSpawnEntity(
            tag.entityId,
            UUID.randomUUID(),
            EntityTypes.TEXT_DISPLAY,
            com.github.retrooper.packetevents.protocol.world.Location(loc.x, loc.y + 1.8, loc.z, 0f, 0f),
            0f,
            0,
            Vector3d(0.0, 0.0, 0.0)
        )

        // Initial metadata: billboard CENTER, empty text
        val metadata = mutableListOf<EntityData<*>>()
        // Index 15: Display entity flags — 0x03 = Billboard CENTER
        metadata.add(EntityData(15, EntityDataTypes.BYTE, 0x03.toByte()))
        // Index 23: Text
        metadata.add(EntityData(23, EntityDataTypes.ADV_COMPONENT, Component.empty()))
        // Index 25: Background transparent
        metadata.add(EntityData(25, EntityDataTypes.INT, 0))
        // Index 11: Translation
        metadata.add(EntityData(11, EntityDataTypes.VECTOR3F, Vector3f(0f, 0.3f, 0f)))

        val metadataPacket = WrapperPlayServerEntityMetadata(tag.entityId, metadata)

        // Mount as passenger of the owner
        val passengerPacket = WrapperPlayServerSetPassengers(owner.entityId, intArrayOf(tag.entityId))

        PacketEvents.getAPI().playerManager.sendPacket(viewer, spawnPacket)
        PacketEvents.getAPI().playerManager.sendPacket(viewer, metadataPacket)
        PacketEvents.getAPI().playerManager.sendPacket(viewer, passengerPacket)

        tag.confirmedViewers.add(viewer.uniqueId)
    }

    /**
     * Send a packet-level team to a viewer that hides the vanilla nametag for [targetName].
     * This bypasses all Bukkit scoreboard conflicts.
     */
    private fun sendTeamHidePacket(viewer: Player, targetName: String, isAdd: Boolean) {
        if (isAdd) {
            // Create team + add member in one packet
            val teamInfo = ScoreBoardTeamInfo(
                Component.empty(),         // displayName
                null,                       // prefix
                null,                       // suffix
                WrapperPlayServerTeams.NameTagVisibility.NEVER,
                WrapperPlayServerTeams.CollisionRule.ALWAYS,
                null,                       // color
                WrapperPlayServerTeams.OptionData.NONE
            )
            // First send CREATE with the member
            val createPacket = WrapperPlayServerTeams(
                TEAM_NAME,
                WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                null as ScoreBoardTeamInfo?,
                targetName
            )
            // Also send the team info if the viewer doesn't have it yet
            val infoPacket = WrapperPlayServerTeams(
                TEAM_NAME,
                WrapperPlayServerTeams.TeamMode.CREATE,
                teamInfo,
                targetName
            )
            // Send CREATE (idempotent for the team, adds member)
            try {
                PacketEvents.getAPI().playerManager.sendPacket(viewer, infoPacket)
            } catch (_: Exception) {
                // Team already exists for this viewer, just add the entity
                PacketEvents.getAPI().playerManager.sendPacket(viewer, createPacket)
            }
        } else {
            val removePacket = WrapperPlayServerTeams(
                TEAM_NAME,
                WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
                null as ScoreBoardTeamInfo?,
                targetName
            )
            PacketEvents.getAPI().playerManager.sendPacket(viewer, removePacket)
        }
    }

    private fun parseBackgroundColor(str: String): Int {
        if (str == "transparent") return 0
        if (!str.startsWith("#")) return 0
        return try {
            val hex = str.removePrefix("#")
            when (hex.length) {
                6 -> (0xFF shl 24) or hex.toInt(16) // Opaque
                8 -> hex.toLong(16).toInt()           // ARGB
                else -> 0
            }
        } catch (_: Exception) { 0 }
    }

    data class VirtualNametag(
        val entityId: Int,
        val confirmedViewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    )
}
