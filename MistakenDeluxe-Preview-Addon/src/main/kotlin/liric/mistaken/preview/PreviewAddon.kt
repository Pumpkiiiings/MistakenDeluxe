package liric.mistaken.preview

import liric.mistaken.api.MistakenAPI
import org.bukkit.plugin.java.JavaPlugin

class PreviewAddon : JavaPlugin() {
    
    companion object {
        lateinit var instance: PreviewAddon
            private set
    }

    val mistaken: MistakenAPI by lazy {
        liric.mistaken.api.MistakenProvider.get()
    }
    
    override fun onEnable() {
        instance = this
        server.consoleSender.sendMessage("§a[Mistaken Preview] Preview Addon loaded successfully.")
        
        saveDefaultConfig()

        val cmd = getCommand("preview")
        val executor = liric.mistaken.preview.commands.PreviewCommand()
        cmd?.setExecutor(executor)
        cmd?.setTabCompleter(executor)
        server.pluginManager.registerEvents(liric.mistaken.preview.menus.SelectorInteractionListener(), this)
    }

    override fun onDisable() {
        server.consoleSender.sendMessage("§c[Mistaken Preview] Preview Addon disabled.")
        
        liric.mistaken.preview.engine.PreviewCamera.activeCameras.values.forEach { it.remove() }
        liric.mistaken.preview.engine.PreviewCamera.activeCameras.clear()
        liric.mistaken.preview.menus.CharacterSelectorMenu.activeMenus.values.forEach { it.close() }
        liric.mistaken.preview.menus.CharacterSelectorMenu.activeMenus.clear()
    }
}
