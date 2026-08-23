package liric.mistaken.utils.hooks.economy

import liric.mistaken.Mistaken
import liric.mistaken.utils.color.ColorTranslator
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.RegisteredServiceProvider
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI

object EconomyManager {

    var hook: IEconomyHook? = null
        private set

    fun init(plugin: Mistaken): Boolean {
        val server = plugin.server

        // First try ExcellentEconomy if we want native integration
        if (server.pluginManager.isPluginEnabled("ExcellentEconomy")) {
            val provider: RegisteredServiceProvider<ExcellentEconomyAPI>? = 
                server.servicesManager.getRegistration(ExcellentEconomyAPI::class.java)
            if (provider != null) {
                hook = ExcellentEconomyHook(provider.provider)
                plugin.componentLogger.info(ColorTranslator.translate("[INFO] ExcellentEconomy found! Hooked into native economy."))
                return true
            }
        }

        // Next try VaultUnlockedAPI
        if (server.pluginManager.isPluginEnabled("Vault")) {
            val rspUnlocked: RegisteredServiceProvider<net.milkbowl.vault2.economy.Economy>? = 
                server.servicesManager.getRegistration(net.milkbowl.vault2.economy.Economy::class.java)
            if (rspUnlocked != null) {
                hook = VaultUnlockedEconomyHook(rspUnlocked.provider)
                plugin.componentLogger.info(ColorTranslator.translate("[INFO] VaultUnlocked found! Hooked into modern economy system."))
                return true
            }

            // Fallback to traditional Vault API
            val rspClassic: RegisteredServiceProvider<net.milkbowl.vault.economy.Economy>? = 
                server.servicesManager.getRegistration(net.milkbowl.vault.economy.Economy::class.java)
            if (rspClassic != null) {
                hook = VaultEconomyHook(rspClassic.provider)
                plugin.componentLogger.info(ColorTranslator.translate("[INFO] Vault found! Hooked into classic Vault economy."))
                return true
            }
        }

        plugin.componentLogger.error(ColorTranslator.translate("[ERROR] No compatible economy plugin found (ExcellentEconomy or Vault)."))
        return false
    }
}
