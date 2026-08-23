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

        // Fallback to Vault
        if (server.pluginManager.isPluginEnabled("Vault")) {
            val rsp: RegisteredServiceProvider<Economy>? = server.servicesManager.getRegistration(Economy::class.java)
            if (rsp != null) {
                hook = VaultEconomyHook(rsp.provider)
                plugin.componentLogger.info(ColorTranslator.translate("[INFO] Vault found! Hooked into Vault economy."))
                return true
            }
        }

        plugin.componentLogger.error(ColorTranslator.translate("[ERROR] No compatible economy plugin found (ExcellentEconomy or Vault)."))
        return false
    }
}
