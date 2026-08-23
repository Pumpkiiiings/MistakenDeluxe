package liric.mistaken.utils.hooks.economy

import org.bukkit.entity.Player

interface IEconomyHook {
    fun getBalance(player: Player): Double
    fun withdraw(player: Player, amount: Double): Boolean
    fun deposit(player: Player, amount: Double): Boolean
}
