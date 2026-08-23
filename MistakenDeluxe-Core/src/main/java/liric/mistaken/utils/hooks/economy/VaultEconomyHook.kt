package liric.mistaken.utils.hooks.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.entity.Player

class VaultEconomyHook(private val economy: Economy) : IEconomyHook {
    override fun getBalance(player: Player): Double = economy.getBalance(player)
    
    override fun withdraw(player: Player, amount: Double): Boolean {
        return economy.withdrawPlayer(player, amount).transactionSuccess()
    }
    
    override fun deposit(player: Player, amount: Double): Boolean {
        return economy.depositPlayer(player, amount).transactionSuccess()
    }
}
