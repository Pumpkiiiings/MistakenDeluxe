package liric.mistaken.utils.hooks.economy

import net.milkbowl.vault2.economy.Economy
import org.bukkit.entity.Player

class VaultUnlockedEconomyHook(private val economy: Economy) : IEconomyHook {
    override fun getBalance(player: Player): Double {
        return economy.getBalance("Mistaken", player.uniqueId).toDouble()
    }
    
    override fun withdraw(player: Player, amount: Double): Boolean {
        return economy.withdraw("Mistaken", player.uniqueId, amount.toBigDecimal()).transactionSuccess()
    }
    
    override fun deposit(player: Player, amount: Double): Boolean {
        return economy.deposit("Mistaken", player.uniqueId, amount.toBigDecimal()).transactionSuccess()
    }
}
