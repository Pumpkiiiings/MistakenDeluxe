package liric.mistaken.packet.fake

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Quaternion4f
import com.github.retrooper.packetevents.util.Vector3f
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import com.github.retrooper.packetevents.protocol.entity.type.EntityType
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.entity.Display

open class VirtualBaseDisplay(
    location: Location,
    viewers: List<Player>,
    entityType: EntityType
) : VirtualDisplay(location, viewers, entityType) {
    var interpolationDuration: Int = 0
    var teleportDuration: Int = 0
    var transformation: Transformation? = null
    var isGlowing: Boolean = false
    var billboard: Display.Billboard? = null
    var brightness: Display.Brightness? = null

    override fun buildMetadata(): List<EntityData<*>> {
        val list = mutableListOf<EntityData<*>>()
        
        var flags = 0.toByte()
        if (isGlowing) flags = (flags.toInt() or 0x40).toByte()
        if (flags > 0) list.add(EntityData(0, EntityDataTypes.BYTE, flags))

        if (interpolationDuration > 0) list.add(EntityData(9, EntityDataTypes.INT, interpolationDuration))
        // Index 10 = teleport_interpolation_duration (added in 1.21, must always be sent as int)
        list.add(EntityData(10, EntityDataTypes.INT, teleportDuration))
        
        transformation?.let {
            list.add(EntityData(11, EntityDataTypes.VECTOR3F, Vector3f(it.translation.x, it.translation.y, it.translation.z)))
            list.add(EntityData(12, EntityDataTypes.VECTOR3F, Vector3f(it.scale.x, it.scale.y, it.scale.z)))
            list.add(EntityData(13, EntityDataTypes.QUATERNION, Quaternion4f(it.leftRotation.x, it.leftRotation.y, it.leftRotation.z, it.leftRotation.w)))
            list.add(EntityData(14, EntityDataTypes.QUATERNION, Quaternion4f(it.rightRotation.x, it.rightRotation.y, it.rightRotation.z, it.rightRotation.w)))
        }
        
        billboard?.let {
            val typeId = when (it) {
                Display.Billboard.FIXED -> 0
                Display.Billboard.VERTICAL -> 1
                Display.Billboard.HORIZONTAL -> 2
                Display.Billboard.CENTER -> 3
            }.toByte()
            list.add(EntityData(15, EntityDataTypes.BYTE, typeId))
        }

        brightness?.let {
            // Brightness is packed into a single int: blockLight | (skyLight << 16)
            val packed = it.blockLight or (it.skyLight shl 16)
            list.add(EntityData(16, EntityDataTypes.INT, packed))
        }

        return list
    }
}

class VirtualItemDisplay(location: Location, viewers: List<Player>) : VirtualBaseDisplay(location, viewers, EntityTypes.ITEM_DISPLAY) {
    private var itemStack: ItemStack? = null

    fun setItemStack(item: ItemStack) {
        this.itemStack = item
    }

    override fun buildMetadata(): List<EntityData<*>> {
        val list = super.buildMetadata().toMutableList()
        itemStack?.let {
            val peItem = SpigotConversionUtil.fromBukkitItemStack(it)
            list.add(EntityData(23, EntityDataTypes.ITEMSTACK, peItem))
        }
        return list
    }
}

class VirtualBlockDisplay(location: Location, viewers: List<Player>) : VirtualBaseDisplay(location, viewers, EntityTypes.BLOCK_DISPLAY) {
    var block: BlockData? = null
    var glowColorOverride: Color? = null

    override fun buildMetadata(): List<EntityData<*>> {
        val list = super.buildMetadata().toMutableList()
        block?.let {
            val stateId = SpigotConversionUtil.fromBukkitBlockData(it)
            list.add(EntityData(23, EntityDataTypes.BLOCK_STATE, stateId.globalId))
        }
        return list
    }
}

class VirtualTextDisplay(location: Location, viewers: List<Player>) : VirtualBaseDisplay(location, viewers, EntityTypes.TEXT_DISPLAY) {
    var text: Component? = null
    var backgroundColor: Color? = null
    var isShadowed: Boolean = false

    override fun buildMetadata(): List<EntityData<*>> {
        val list = super.buildMetadata().toMutableList()
        text?.let {
            list.add(EntityData(23, EntityDataTypes.ADV_COMPONENT, it))
        }
        backgroundColor?.let {
            // ARGB
            val packed = (it.alpha shl 24) or (it.red shl 16) or (it.green shl 8) or it.blue
            list.add(EntityData(25, EntityDataTypes.INT, packed))
        }
        
        var textFlags = 0.toByte()
        if (isShadowed) textFlags = (textFlags.toInt() or 0x01).toByte()
        if (textFlags > 0) {
            list.add(EntityData(27, EntityDataTypes.BYTE, textFlags))
        }
        
        return list
    }
}
