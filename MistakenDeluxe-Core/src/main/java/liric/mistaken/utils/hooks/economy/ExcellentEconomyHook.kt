package liric.mistaken.utils.hooks.economy

import org.bukkit.entity.Player
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI

class ExcellentEconomyHook(private val api: ExcellentEconomyAPI) : IEconomyHook {
    
    private val defaultCurrency = api.currencies.firstOrNull()

    override fun getBalance(player: Player): Double {
        if (defaultCurrency == null) return 0.0
        return api.getBalance(player, defaultCurrency)
    }

    override fun withdraw(player: Player, amount: Double): Boolean {
        if (defaultCurrency == null) return false
        return api.withdraw(player, defaultCurrency, amount)
    }

    override fun deposit(player: Player, amount: Double): Boolean {
        if (defaultCurrency == null) return false
        return api.deposit(player, defaultCurrency, amount)
    }
}
