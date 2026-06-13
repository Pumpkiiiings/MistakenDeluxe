package liric.mistaken.level.rewards.types

import liric.mistaken.api.MistakenProvider
import liric.mistaken.level.rewards.RewardExecutor
import liric.mistaken.level.integration.UltimateAdvancementHook
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Duration

class MessageReward : RewardExecutor {
    private val mm = MiniMessage.miniMessage()
    override fun execute(player: Player, value: String) {
        val parsedValue = value.replace("%player%", player.name)
        player.sendMessage(mm.deserialize(parsedValue))
    }
}

class ActionBarReward : RewardExecutor {
    private val mm = MiniMessage.miniMessage()
    override fun execute(player: Player, value: String) {
        val parsedValue = value.replace("%player%", player.name)
        player.sendActionBar(mm.deserialize(parsedValue))
    }
}

class TitleReward : RewardExecutor {
    private val mm = MiniMessage.miniMessage()
    override fun execute(player: Player, value: String) {
        val parts = value.split(";")
        val titleText = parts.getOrNull(0)?.replace("%player%", player.name) ?: ""
        val subtitleText = parts.getOrNull(1)?.replace("%player%", player.name) ?: ""
        
        val title = Title.title(
            mm.deserialize(titleText),
            mm.deserialize(subtitleText),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500))
        )
        player.showTitle(title)
    }
}

class CommandReward : RewardExecutor {
    override fun execute(player: Player, value: String) {
        val parsedValue = value.replace("%player%", player.name)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedValue)
    }
}

class KillerReward : RewardExecutor {
    override fun execute(player: Player, value: String) {
        val api = MistakenProvider.get()
        api.playerDataManager.comprarAsesino(player.uniqueId, value)
    }
}

class SurvivorReward : RewardExecutor {
    override fun execute(player: Player, value: String) {
        val api = MistakenProvider.get()
        api.playerDataManager.comprarSuperviviente(player.uniqueId, value)
    }
}

class CurrencyReward : RewardExecutor {
    override fun execute(player: Player, value: String) {
        val amount = value.toDoubleOrNull() ?: return
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            val rsp = Bukkit.getServer().servicesManager.getRegistration(net.milkbowl.vault.economy.Economy::class.java)
            val econ = rsp?.provider ?: return
            econ.depositPlayer(player, amount)
        }
    }
}

class PermissionReward : RewardExecutor {
    override fun execute(player: Player, value: String) {
        // We can execute lp command as permission plugin might vary, but luckperms API is better.
        // Easiest robust way is to dispatch command unless luckperms API is specifically requested.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user ${player.name} permission set $value true")
    }
}

class AdvancementReward(private val hook: UltimateAdvancementHook) : RewardExecutor {
    override fun execute(player: Player, value: String) {
        hook.grantAdvancement(player, value)
    }
}
