package pumpking.lib.config

import org.bukkit.Bukkit
import pumpking.lib.core.PumpkingLib

object AutoApplyService {
    
    fun onFileChanged(fileName: String) {
        // Refresh specific configs in memory if needed
        if (fileName == "asesinos.yml" || fileName == "supervivientes.yml" || fileName.startsWith("menus")) {
            ConfigManager.loadAllConfigs()
            if (fileName.startsWith("menus")) {
                ConfigManager.reloadMenus()
            }
        }
        
        // Ensure event fires synchronously since most listeners interact with Bukkit API
        Bukkit.getScheduler().runTask(PumpkingLib.plugin, java.lang.Runnable {
            val event = ConfigReloadEvent(fileName)
            Bukkit.getPluginManager().callEvent(event)
            PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "ConfigReloadEvent fired for $fileName")
        })
    }
}
