package liric.mistaken.test
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.util.Quaternion4f
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
fun test() {
    val a = Vector3f(1f, 1f, 1f)
    val b = Quaternion4f(1f, 0f, 0f, 0f)
    val c = EntityDataTypes.VECTOR3F
    val d = EntityDataTypes.QUATERNION
    val e = EntityDataTypes.ADV_COMPONENT
    val f = EntityDataTypes.ITEMSTACK
}
