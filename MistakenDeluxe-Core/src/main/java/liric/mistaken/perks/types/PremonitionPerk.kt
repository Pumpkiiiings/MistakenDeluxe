package liric.mistaken.perks.types

import liric.mistaken.perks.Perk
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class PremonitionPerk : Perk(
    id = "premonition",
    icon = ItemStack(Material.ENDER_EYE)
) {
    
    private val cooldowns = mutableMapOf<Player, Long>()
    
    override fun onLookedAtByKiller(player: Player) {
        val now = System.currentTimeMillis()
        val last = cooldowns[player] ?: 0L
        if (now - last > 5000) { // 5s cooldown
            player.playSound(player.location, Sound.ENTITY_ENDERMAN_STARE, 1.0f, 1.0f)
            cooldowns[player] = now
        }
    }
}
