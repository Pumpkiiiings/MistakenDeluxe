package liric.mistaken.scripting.effects.gameplay

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.player.GameMode
import com.github.retrooper.packetevents.protocol.player.TextureProperty
import com.github.retrooper.packetevents.protocol.player.UserProfile
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.*
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.scripting.effects.EffectHandle
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.Optional

class FakePlayerEffect(
    override val scriptId: String,
    override val ownerUuid: UUID,
    private val location: Location,
    private val copyAppearanceFrom: Player?,
    private val copyEquipmentFrom: Player?,
    private val durationTicks: Int,
    private val enableAi: Boolean,
    private val fleeRadius: Double?,
    private val fleeCatchRadius: Double?,
    private val onCaughtCallback: ((Location) -> Unit)?,
    private val onExpireCallback: ((Location) -> Unit)?
) : EffectHandle {

    private val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    private val alive = AtomicBoolean(true)
    private var scheduledTask: ScheduledTask? = null
    
    private val fakeId = ThreadLocalRandom.current().nextInt(200000, 300000)
    private val fakeUUID = UUID.randomUUID()
    private val pm = PacketEvents.getAPI().playerManager
    
    private var tickCount = 0
    private var ai: FakePlayerAI? = null

    override val isAlive: Boolean get() = alive.get()

    fun start() {
        val profile = UserProfile(fakeUUID, copyAppearanceFrom?.name ?: "Unknown")
        copyAppearanceFrom?.playerProfile?.properties?.forEach { prop ->
            profile.textureProperties.add(TextureProperty(prop.name, prop.value, prop.signature))
        }

        val infoData = WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            profile, true, 1, GameMode.SURVIVAL,
            Component.text(profile.name), null
        )

        val infoPacket = WrapperPlayServerPlayerInfoUpdate(
            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
            infoData
        )

        val spawnPacket = WrapperPlayServerSpawnEntity(
            fakeId, Optional.of(fakeUUID), EntityTypes.PLAYER,
            Vector3d(location.x, location.y, location.z),
            location.pitch, location.yaw, location.yaw, 0, Optional.empty()
        )

        val equipmentList = mutableListOf<com.github.retrooper.packetevents.protocol.player.Equipment>()
        val fromBukkit = { item: ItemStack? -> io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack(item) }
        
        copyEquipmentFrom?.let { p ->
            p.inventory.helmet?.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET, fromBukkit(it))) }
            p.inventory.chestplate?.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE, fromBukkit(it))) }
            p.inventory.leggings?.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS, fromBukkit(it))) }
            p.inventory.boots?.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS, fromBukkit(it))) }
            p.inventory.itemInMainHand.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND, fromBukkit(it))) }
            p.inventory.itemInOffHand.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND, fromBukkit(it))) }
        }

        val equipPacket = WrapperPlayServerEntityEquipment(fakeId, equipmentList)

        plugin.server.onlinePlayers.forEach { viewer ->
            pm.sendPacket(viewer, infoPacket)
            pm.sendPacket(viewer, spawnPacket)
            if (equipmentList.isNotEmpty()) pm.sendPacket(viewer, equipPacket)
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
            val removeInfo = WrapperPlayServerPlayerInfoRemove(profile.uuid)
            plugin.server.onlinePlayers.forEach { pm.sendPacket(it, removeInfo) }
        }, 5L)

        if (enableAi) {
            ai = FakePlayerAI(
                ownerUuid = ownerUuid,
                initialLoc = location,
                fleeRadiusSq = fleeRadius?.let { it * it },
                catchRadiusSq = fleeCatchRadius?.let { it * it },
                onCaught = {
                    onCaughtCallback?.invoke(ai!!.currentLoc.clone())
                    cleanup()
                }
            )
        }

        scheduledTask = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!alive.get()) {
                task.cancel()
                return@Consumer
            }

            if (tickCount >= durationTicks) {
                onExpireCallback?.invoke((ai?.currentLoc ?: location).clone())
                cleanup()
                task.cancel()
                return@Consumer
            }

            if (enableAi && ai != null) {
                ai!!.tick(tickCount)
                if (!alive.get()) return@Consumer

                val loc = ai!!.currentLoc
                val tpPacket = WrapperPlayServerEntityTeleport(fakeId, Vector3d(loc.x, loc.y, loc.z), ai!!.currentYaw, loc.pitch, ai!!.isOnGround)
                val headPacket = WrapperPlayServerEntityHeadLook(fakeId, ai!!.currentYaw)
                val metadataPacket = WrapperPlayServerEntityMetadata(fakeId, listOf(
                    EntityData(0, EntityDataTypes.BYTE, 0x08.toByte())
                ))

                plugin.server.onlinePlayers.forEach { viewer ->
                    pm.sendPacket(viewer, tpPacket)
                    pm.sendPacket(viewer, headPacket)
                    pm.sendPacket(viewer, metadataPacket)
                }

                if (ai!!.isOnGround && tickCount % (if (ai!!.isFleeing) 5 else 6) == 0) {
                    plugin.server.regionScheduler.execute(plugin, loc) {
                        loc.world.playSound(loc, Sound.BLOCK_STONE_STEP, 0.3f, 1f)
                    }
                }
            }

            tickCount++
        }, 1L, 1L)
    }

    override fun stop() { cleanup() }
    override fun remove() { cleanup() }

    private fun cleanup() {
        if (alive.compareAndSet(true, false)) {
            scheduledTask?.cancel()
            scheduledTask = null
            
            val destroyPacket = WrapperPlayServerDestroyEntities(fakeId)
            plugin.server.onlinePlayers.forEach { pm.sendPacket(it, destroyPacket) }
        }
    }
}
