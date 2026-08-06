package liric.mistaken.packet.fake

import org.bukkit.Location
import org.bukkit.entity.Player

class VirtualDisplayAPI {

    fun buildTextDisplay(viewers: Collection<Player>, location: Location, builder: (VirtualTextDisplay) -> Unit): VirtualTextDisplay {
        val display = VirtualTextDisplay(location.clone(), viewers)
        builder(display)
        display.spawn()
        return display
    }

    fun buildBlockDisplay(viewers: Collection<Player>, location: Location, builder: (VirtualBlockDisplay) -> Unit): VirtualBlockDisplay {
        val display = VirtualBlockDisplay(location.clone(), viewers)
        builder(display)
        display.spawn()
        return display
    }

    fun buildItemDisplay(viewers: Collection<Player>, location: Location, builder: (VirtualItemDisplay) -> Unit): VirtualItemDisplay {
        val display = VirtualItemDisplay(location.clone(), viewers)
        builder(display)
        display.spawn()
        return display
    }
}
