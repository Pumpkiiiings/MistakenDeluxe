package liric.mistaken.roles.killers

import liric.mistaken.Mistaken
import liric.mistaken.scripting.engine.groovy.KillerScriptEngine
import liric.mistaken.roles.killers.classes.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.UUID
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import liric.mistaken.api.managers.IKillerManager
import org.bukkit.Material
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService

import liric.mistaken.roles.shared.AbstractRoleManager


class KillerManager(plugin: Mistaken) : AbstractRoleManager<Killer>(plugin), IKillerManager {

    init {
        plugin.server.pluginManager.registerEvents(liric.mistaken.scripting.api.event.LuaKillerEventDispatcher(plugin), plugin)
        plugin.server.pluginManager.registerEvents(liric.mistaken.scripting.effects.EffectLifecycleListener(plugin), plugin)
        plugin.server.pluginManager.registerEvents(liric.mistaken.scripting.effects.gameplay.FinisherEngine, plugin)
        plugin.server.pluginManager.registerEvents(liric.mistaken.roles.killers.triggers.TriggerListener(plugin), plugin)
        liric.mistaken.roles.killers.triggers.traps.WorldTrapRegistry.init(plugin)
        reloadAll()
    }

    fun reloadAll() {
        cleanAll()
        availableClasses.clear()
        loadHardcodedKillers()
        loadScripts()
    }

    private fun loadHardcodedKillers() {
        listOf(
            CharlieInferno(), CharlieJazz(), Mariachi(),
            Sowoul(), StillLife(), WardenKiller(), SmilerKiller()
        ).forEach { registerClass(it) }
        plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Loaded native killers (Hardcoded).</gray>"))
    }

    fun loadScripts() {
        val scriptsFolder = java.io.File(plugin.dataFolder, "characters/scripts")
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs()
        }

        
        try {
            val defaults = listOf("slasher", "herobrine", "romeo", "entity303", "colorandelectricity", "null", "tinkywinky")
            for (script in defaults) {
                if (!java.io.File(scriptsFolder, "$script.lua").exists() && !java.io.File(scriptsFolder, "$script.groovy").exists()) {
                    plugin.saveResource("characters/scripts/$script.lua", false)
                }
            }
        } catch (e: Exception) {
            plugin.componentLogger.warn(liric.mistaken.utils.color.ColorTranslator.translate("<yellow>[WARN]</yellow> <gray>Failed to copy default scripts.</gray>"))
        }

        val files = scriptsFolder.listFiles() ?: return
        var loadedCount = 0
        for (file in files) {
            if (file.name.endsWith(".groovy")) {
                val killer = KillerScriptEngine.loadKillerScript(file)
                if (killer != null) {
                    registerClass(killer)
                    loadedCount++
                }
            } else if (file.name.endsWith(".lua")) {
                val killerId = file.nameWithoutExtension.lowercase()
                val scriptKiller = liric.mistaken.scripting.engine.lua.LuaScriptEngine.loadScript(file, killerId)
                if (scriptKiller != null) {
                    
                    val luaAdapter = liric.mistaken.scripting.adapter.LuaKillerAdapter(
                        id = killerId,
                        nombre = killerId.replaceFirstChar { it.uppercase() },
                        scriptKiller = scriptKiller
                    )
                    registerClass(luaAdapter)
                    loadedCount++
                }
            }
        }
        plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Loaded $loadedCount killers from scripts.</gray>"))
    }

    override fun registerClass(role: Killer) {
        val config = plugin.configManager.getKillerConfig(role.id)
        if (config.getBoolean("enabled", true)) {
            availableClasses[role.id.lowercase()] = role
        }
    }

    fun updateKiller(player: Player, claseId: String) {
        if (claseId.equals("none", ignoreCase = true)) {
            removeKiller(player)
            return
        }
        val clase = getClassById(claseId) ?: return

        
        player.scheduler.run(plugin, Consumer { _ ->
            clase.cleanup(player)
            plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>${player.name} synchronized with ${clase.nombre}</gray>"))
        }, null)
    }

    fun registerKiller(player: Player, killer: Killer) {
        val uuid = player.uniqueId

        
        if (activeRoles.containsKey(uuid)) {
            removeRoleLogic(uuid, player)
        }

        
        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)
        activeRoles[uuid] = killer

        
        player.sendMessage(MessageService.getComponent(player, "killer.transform",
            Placeholder.component("name", ColorTranslator.translate(killer.nombre))))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)

        
        player.scheduler.runDelayed(
            plugin,
            Consumer { _ ->
                if (!player.isOnline || !activeRoles.containsKey(uuid)) return@Consumer

                killer.equip(player)

                
                val config = plugin.configManager.getKillerConfig(killer.id)
                
                
                val maxHealth = config.getDouble("stats.health", 40.0)
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = maxHealth
                player.health = maxHealth

                val currentItems = (1..4).associateWith { player.inventory.getItem(it) }

                for (i in 1..4) {
                    val targetSlot = config.getInt("items.skill${i}_slot", i)
                    if (targetSlot != i) {
                        player.inventory.setItem(i, null)
                    }
                }
                for (i in 1..4) {
                    val targetSlot = config.getInt("items.skill${i}_slot", i)
                    val item = currentItems[i]
                    if (item != null && item.type != Material.AIR) {
                        player.inventory.setItem(targetSlot, item)
                    }
                }

                val weaponSlot = config.getInt("items.weapon_slot", 8)
                if (weaponSlot != 8) {
                    val weaponItem = player.inventory.getItem(8)
                    player.inventory.setItem(8, null)
                    player.inventory.setItem(weaponSlot, weaponItem)
                }

                killer.showTrail(player)
                player.inventory.heldItemSlot = weaponSlot
            },
            null,
            15L 
        )
    }

    fun equipKiller(player: Player, claseId: String) {
        val clase = getClassById(claseId) ?: getClassById("slasher")
        clase?.let { registerKiller(player, it) }
    }

    fun removeKiller(player: Player) {
        removeRoleLogic(player.uniqueId, player)
    }

    override fun removeRoleLogic(uuid: UUID, player: Player?) {
        val killer = activeRoles.remove(uuid) ?: return

        
        killer.cleanup(player)
        
        
        liric.mistaken.scripting.effects.EffectRegistry.stopAll(uuid)

        if (player != null && player.isOnline) {
            player.inventory.clear()
            player.inventory.armorContents = arrayOfNulls(4)
            
            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            player.health = 20.0
            player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
            player.isGlowing = false
            player.activePotionEffects.forEach { effect -> player.removePotionEffect(effect.type) }
            
            
            plugin.visibilityManager.removePlayer(player.uniqueId)
        }
    }

    fun removeAllKillers() {
        cleanAll()
        
        availableClasses.values.forEach { killer ->
            killer.dispose()
        }
    }

    /**
     * Hot-Reload individual para un killer (Especialmente Scripts).
     */
    fun reloadKiller(id: String) {
        val lowerId = id.lowercase()
        val oldKiller = availableClasses[lowerId]

        if (oldKiller != null) {
            oldKiller.dispose()
            KillerScriptEngine.unloadKillerScript(lowerId)
        }

        val scriptsFolder = java.io.File(plugin.dataFolder, "characters/scripts")
        val groovyScript = java.io.File(scriptsFolder, "$lowerId.groovy")
        val luaScript = java.io.File(scriptsFolder, "$lowerId.lua")
        
        val isLua = luaScript.exists()
        val scriptFile = if (isLua) luaScript else groovyScript
        
        if (scriptFile.exists()) {
            val newKiller = if (isLua) {
                val scriptKiller = liric.mistaken.scripting.engine.lua.LuaScriptEngine.loadScript(scriptFile, lowerId)
                if (scriptKiller != null) {
                    liric.mistaken.scripting.adapter.LuaKillerAdapter(
                        id = lowerId,
                        nombre = lowerId.replaceFirstChar { it.uppercase() },
                        scriptKiller = scriptKiller
                    )
                } else null
            } else {
                KillerScriptEngine.loadKillerScript(scriptFile)
            }

            if (newKiller != null) {
                registerClass(newKiller)
                
                
                val affectedPlayers = mutableListOf<Player>()
                activeRoles.forEach { (uuid, activeKiller) ->
                    if (activeKiller.id.equals(lowerId, ignoreCase = true)) {
                        val player = org.bukkit.Bukkit.getPlayer(uuid)
                        if (player != null && player.isOnline) {
                            affectedPlayers.add(player)
                        } else {
                            activeRoles.remove(uuid)
                        }
                    }
                }
                
                affectedPlayers.forEach { p ->
                    oldKiller?.cleanup(p)
                    equipKiller(p, lowerId)
                }
                
                plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Killer $lowerId reloaded successfully.</gray>"))
            } else {
                availableClasses.remove(lowerId)
                plugin.componentLogger.warn(liric.mistaken.utils.color.ColorTranslator.translate("<yellow>[WARN]</yellow> <gray>Error reloading $lowerId. The killer has been disabled.</gray>"))
            }
        } else {
            plugin.componentLogger.warn(liric.mistaken.utils.color.ColorTranslator.translate("<yellow>[WARN]</yellow> <gray>Could not find script $lowerId.groovy or .lua to reload.</gray>"))
        }
    }

    
    fun getKillerOfPlayer(player: Player?): Killer? = player?.let { activeRoles[it.uniqueId] }
    fun isKiller(player: Player?): Boolean = player?.let { activeRoles.containsKey(it.uniqueId) } ?: false
    fun getAvailableClasses(): Map<String, Killer> = availableClasses

    override fun shutdown() {
        removeAllKillers()
    }
}
