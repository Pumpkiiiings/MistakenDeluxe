package liric.mistaken.roles.survivors

import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.classes.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService

import liric.mistaken.roles.shared.AbstractRoleManager


class SurvivorManager(plugin: Mistaken) : AbstractRoleManager<liric.mistaken.api.roles.ISurvivor>(plugin), liric.mistaken.api.managers.ISurvivorManager {

    init {
        
        
        listOf(
            DeliveryMan(),
            Jesse(),
            Villager()
        ).forEach { registerClass(it) }

        loadScripts()
    }

    fun reloadAll() {
        cleanAll()
        availableClasses.clear()
        listOf(
            DeliveryMan(),
            Jesse(),
            Villager()
        ).forEach { registerClass(it) }
        loadScripts()
    }

    fun loadScripts() {
        val scriptsFolder = java.io.File(plugin.dataFolder, "scripts/survivors")
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs()
        }

        try {
            val defaults = listOf("civil", "minty", "notch", "raincoatkid", "troll")
            for (script in defaults) {
                if (!java.io.File(scriptsFolder, "$script.lua").exists()) {
                    plugin.saveResource("scripts/survivors/$script.lua", false)
                }
            }
        } catch (e: Exception) {
            plugin.componentLogger.warn(liric.mistaken.utils.color.ColorTranslator.translate("<yellow>[WARN]</yellow> <gray>Failed to copy default survivor scripts.</gray>"))
        }

        val files = scriptsFolder.listFiles() ?: return
        var loadedCount = 0
        for (file in files) {
            if (file.name.endsWith(".lua")) {
                val survivorId = file.nameWithoutExtension.lowercase()
                val scriptRole = liric.mistaken.scripting.engine.lua.LuaScriptEngine.loadScript(file, survivorId)
                if (scriptRole != null) {
                    val luaAdapter = liric.mistaken.scripting.adapter.LuaSurvivorAdapter(
                        id = survivorId,
                        nombre = survivorId.replaceFirstChar { it.uppercase() },
                        scriptRole = scriptRole
                    )
                    registerClass(luaAdapter)
                    loadedCount++
                }
            }
        }
        if (loadedCount > 0) {
            plugin.componentLogger.info(ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Loaded $loadedCount survivors from scripts.</gray>"))
        }
    }

    override fun registerClass(role: liric.mistaken.api.roles.ISurvivor) {
        if (role is Survivor) {
            val config = plugin.configManager.getSurvivorConfig(role.id)
            if (config.getBoolean("enabled", true)) {
                availableClasses[role.id.lowercase()] = role
            }
        }
    }

    /**
     * ?? REGISTRO OPTIMIZADO (Paper 1.21.4+):
     * Usamos 'player.scheduler'. Si el player se desconecta antes de los 5 ticks,
     * la tarea se cancela sola autom�ticamente.
     */
    fun registrarSurvivor(player: Player, clase: Survivor) {
        val uuid = player.uniqueId

        
        activeRoles[uuid] = clase

        
        
        player.scheduler.runDelayed(plugin, { task ->
            
            if (activeRoles[uuid] == clase) {
                clase.equip(player)

                
                val config = plugin.configManager.getSurvivorConfig(clase.id)
                val maxHealth = config.getDouble("stats.health", 20.0)
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = maxHealth
                player.health = maxHealth

                player.updateInventory()

                plugin.componentLogger.info(ColorTranslator.translate(
                    "<gray>[Survivor]</gray> <white>${player.name}</white> <green>equipado como ${clase.nombre}</green>"
                ))

                
                player.sendMessage(MessageService.getComponent(player, "game.class-selected",
                    Placeholder.component("class", ColorTranslator.translate(clase.nombre))))
            }
        }, null, 5L)
    }

    /**
     * Remueve al survivor.
     */
    fun removerSurvivor(player: Player) {
        removeRoleLogic(player.uniqueId, player)
    }

    fun removerSurvivor(uuid: UUID) {
        val player = Bukkit.getPlayer(uuid)
        removeRoleLogic(uuid, player)
    }

    override fun removeRoleLogic(uuid: UUID, player: Player?) {
        val clase = activeRoles.remove(uuid) ?: return

        if (player != null && player.isOnline) {
            
            player.scheduler.run(plugin, { _ ->
                
                clase.cleanup(player)

                
                player.inventory.clear()
                player.inventory.armorContents = arrayOfNulls(4)
                
                
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 20.0

                
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }

                player.isSwimming = false
                player.walkSpeed = 0.2f
            }, null)
        } else {
            
            clase.cleanup(null)
        }
    }

    override fun cleanAll() {
        super.cleanAll()
        plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Survivor cleanup completed.</gray>"))
    }

    
    fun esSurvivorActivo(player: Player?): Boolean = player?.let { activeRoles.containsKey(it.uniqueId) } ?: false
    fun getSurvivorClass(player: Player?): Survivor? = player?.let { activeRoles[it.uniqueId] as? Survivor }
    fun getAvailableClasses(): Map<String, Survivor> = availableClasses.mapValues { it.value as Survivor }
}
