package liric.mistaken.utils.hooks.economy

import liric.mistaken.Mistaken
import org.bukkit.entity.Player

class MistakenEconomyHook(private val plugin: Mistaken) : IEconomyHook {
    override fun getBalance(player: Player): Double {
        return plugin.statsManager.getStats(player.uniqueId).coins.get().toDouble()
    }

    override fun withdraw(player: Player, amount: Double): Boolean {
        val stats = plugin.statsManager.getStats(player.uniqueId)
        val current = stats.coins.get()
        val intAmount = amount.toInt()
        
        if (current >= intAmount) {
            stats.coins.addAndGet(-intAmount)
            return true
        }
        return false
    }

    override fun deposit(player: Player, amount: Double): Boolean {
        val stats = plugin.statsManager.getStats(player.uniqueId)
        stats.coins.addAndGet(amount.toInt())
        return true
    }
}
