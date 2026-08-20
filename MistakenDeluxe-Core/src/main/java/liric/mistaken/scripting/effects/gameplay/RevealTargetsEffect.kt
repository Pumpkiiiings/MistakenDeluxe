package liric.mistaken.scripting.effects.gameplay

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Revela a todos los jugadores cercanos aplicándoles glowing a través de PacketEvents,
 * usando el color especificado. El color requiere un Team.
 * Generaliza AdminVision de Romeo.
 */
class RevealTargetsEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val player: Player,
    private val radius: Double,
    private val maxTicks: Int,
    private val colorName: String
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null
    private val teamName = "reveal_$ownerUuid"
    private val revealedPlayers = mutableListOf<Player>()

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        val targets = GameplayFunctions.nearbyValidTargets(player, radius)
        if (targets.isEmpty()) {
            cleanup()
            return
        }

        val color = NamedTextColor.NAMES.value(colorName.lowercase()) ?: NamedTextColor.WHITE

        val teamInfo = ScoreBoardTeamInfo(
            Component.text("RevealTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            color, WrapperPlayServerTeams.OptionData.NONE
        )

        val targetNames = targets.map { it.name }
        val createTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, targetNames)
        PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)

        targets.forEach { victim ->
            val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte())) // Glowing flag
            PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(victim.entityId, metadata))
            revealedPlayers.add(victim)
        }

        scheduledTask = player.scheduler.runDelayed(plugin, Consumer {
            cleanup()
        }, null, maxTicks.toLong())
    }

    override fun stop() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            scheduledTask = null
            
            if (player.isOnline) {
                // Remove team
                val removeTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
                
                // Remove glowing metadata for the player
                revealedPlayers.forEach { victim ->
                    val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x00.toByte())) // Remove glowing
                    PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(victim.entityId, metadata))
                }
            }
            revealedPlayers.clear()
        }
    }
}
