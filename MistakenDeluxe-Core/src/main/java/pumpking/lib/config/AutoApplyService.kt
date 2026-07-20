package pumpking.lib.config

import org.bukkit.Bukkit
import pumpking.lib.core.PumpkingLib
import java.lang.Runnable

object AutoApplyService {

    fun onFileChanged(fileName: String) {
        // FIX #9: All config loading and event firing must happen on the main thread.
        // Previously, loadAllConfigs() ran on the IO thread (from ConfigWatcher) while the
        // main thread could simultaneously read those same configs — a data race.
        Bukkit.getScheduler().runTask(PumpkingLib.plugin, Runnable {
            if (fileName == "asesinos.yml" || fileName == "supervivientes.yml" || fileName.startsWith("menus")) {
                ConfigManager.loadAllConfigs()
                if (fileName.startsWith("menus")) {
                    ConfigManager.reloadMenus()
                }
            }
            val event = ConfigReloadEvent(fileName)
            Bukkit.getPluginManager().callEvent(event)
            PumpkingLib.log(PumpkingLib.LogCategory.CONFIG, "ConfigReloadEvent fired for $fileName")
        })
    }
}
