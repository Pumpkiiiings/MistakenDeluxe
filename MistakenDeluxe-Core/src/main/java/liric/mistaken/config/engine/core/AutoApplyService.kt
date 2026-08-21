package liric.mistaken.config.engine.core

import org.bukkit.Bukkit
import liric.mistaken.MistakenLib
import java.lang.Runnable

object AutoApplyService {

    fun onFileChanged(fileName: String) {
        
        
        
        Bukkit.getScheduler().runTask(MistakenLib.plugin, Runnable {
            if (fileName == "asesinos.yml" || fileName == "supervivientes.yml" || fileName.startsWith("menus")) {
                ConfigManager.loadAllConfigs()
                if (fileName.startsWith("menus")) {
                    ConfigManager.reloadMenus()
                }
            }
            val event = ConfigReloadEvent(fileName)
            Bukkit.getPluginManager().callEvent(event)
            MistakenLib.log(MistakenLib.LogCategory.CONFIG, "ConfigReloadEvent fired for $fileName")
        })
    }
}
