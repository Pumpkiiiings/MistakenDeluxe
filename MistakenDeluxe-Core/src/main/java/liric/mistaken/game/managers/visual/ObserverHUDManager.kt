package liric.mistaken.game.managers.visual

import com.observer.api.model.ComponentAlignment
import com.observer.api.model.TextAlignment
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.hooks.ObserverHook
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import org.bukkit.scheduler.BukkitTask
import liric.mistaken.utils.color.ColorTranslator


class ObserverHUDManager(private val plugin: Mistaken) {

    private val configFile: File = File(plugin.dataFolder, "observer-hud.yml")
    private var config: YamlConfiguration = YamlConfiguration()

    // Estado actual pintado para no sobreescribir: Map<PlayerUUID, State>
    private val activeStates = ConcurrentHashMap<UUID, String>()
    // Componentes din�micos que deben ser actualizados
    private val activeDynamicTexts = ConcurrentHashMap<UUID, MutableSet<DynamicTextComponent>>()
    // IDs de los componentes globales (nunca se borran mientras el player est�)
    private val globalComponentIds = ConcurrentHashMap<UUID, MutableSet<String>>()
    // IDs de los componentes de estado actual para borrado manual
    private val stateComponentIds = ConcurrentHashMap<UUID, MutableSet<String>>()

    private var updateTask: BukkitTask? = null
    private val loggedObserverPlayers = ConcurrentHashMap.newKeySet<UUID>()

    init {
        loadConfig()
    }

    fun loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("observer-hud.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    fun updatePlayer(player: Player) {
        val hasObs = ObserverHook.hasObserver(player)
        
        if (hasObs && loggedObserverPlayers.add(player.uniqueId)) {
            plugin.componentLogger.info(ColorTranslator.translate("[<green>ObserverHook</green>] <white>Player <yellow>${player.name}</yellow> has been detected by ObserverHook!</white>"))
        }

        handlePlayerState(player)
        updateDynamicComponents(player)
    }

    fun handlePlayerState(player: Player) {
        if (!ObserverHook.hasObserver(player)) return

        val gm = plugin.sessionManager.getSession(player)
        val stateName = if (gm == null) "none" else {
            if (gm.currentState == GameState.LOBBY) "prelobby"
            else "ingame"
        }

        // 1. Asegurar HUD global siempre dibujado
        if (!globalComponentIds.containsKey(player.uniqueId)) {
            drawComponents(player, "global", true)
        }

        // 2. Transici�n de estados
        if (activeStates[player.uniqueId] != stateName) {
            activeStates[player.uniqueId] = stateName
            clearPlayerStateComponents(player) // Borra estado anterior sin tocar global
            
            if (stateName != "none") {
                drawComponents(player, stateName, false)
            }
            
            
            if (stateName == "ingame" && gm != null) {
                val role = if (gm.isKiller(player.uniqueId)) "killer" else "survivor"
                drawComponents(player, "roles.$role", false)
            }
        }
    }

    fun updatePlayerRole(player: Player) {
        if (!ObserverHook.hasObserver(player)) return
        val gm = plugin.sessionManager.getSession(player) ?: return
        if (activeStates[player.uniqueId] == "ingame") {
            val role = if (gm.isKiller(player.uniqueId)) "killer" else "survivor"
            // Remover componentes de roles previos no es tan trivial porque no guardamos la key exacta,
            // pero podemos draw encima o confiar en que se llame despu�s de un clearHUD().
            drawComponents(player, "roles.$role", false)
        }
    }

    private fun clearPlayerStateComponents(player: Player) {
        if (ObserverHook.hasObserver(player)) {
            val ids = stateComponentIds.remove(player.uniqueId)
            ids?.forEach { ObserverHook.removeComponent(player, it) }
            
            // Removemos los textos din�micos asociados al estado
            val dynamic = activeDynamicTexts[player.uniqueId]
            dynamic?.removeIf { ids?.contains(it.id) == true }
        }
    }

    fun clearPlayer(player: Player) {
        if (ObserverHook.hasObserver(player)) {
            ObserverHook.clearHUD(player) // Borra todo f�sicamente al salir
        }
        activeStates.remove(player.uniqueId)
        globalComponentIds.remove(player.uniqueId)
        stateComponentIds.remove(player.uniqueId)
        activeDynamicTexts.remove(player.uniqueId)
        loggedObserverPlayers.remove(player.uniqueId)
    }

    private fun drawComponents(player: Player, sectionName: String, isGlobal: Boolean) {
        val section = config.getConfigurationSection("$sectionName.components") ?: return

        for (key in section.getKeys(false)) {
            val comp = section.getConfigurationSection(key) ?: continue
            val id = "mistaken:$key"
            val type = comp.getString("type", "TEXT")?.uppercase() ?: "TEXT"
            val alignment = getAlignment(comp.getString("alignment", "TOP_CENTER"))
            val textAlignment = getTextAlignment(comp.getString("text_alignment", "LEFT"))
            val offsetX = comp.getInt("offset_x", 0)
            val offsetY = comp.getInt("offset_y", 0)
            val scale = comp.getDouble("scale", 1.0).toFloat()

            if (type == "TEXT") {
                val rawContent = comp.getString("content", "") ?: ""
                val parsedContent = parseContent(player, rawContent)
                
                ObserverHook.createText(player, id, parsedContent, alignment, offsetX, offsetY, scale, textAlignment)

                if (rawContent.contains("%")) {
                    val texts = activeDynamicTexts.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }
                    texts.add(DynamicTextComponent(id, rawContent))
                }
                val targetSet = if (isGlobal) globalComponentIds else stateComponentIds
                targetSet.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }.add(id)
            } else if (type == "ITEM") {
                val material = comp.getString("material", "minecraft:stone") ?: "minecraft:stone"
                val amount = comp.getInt("amount", 1)
                ObserverHook.createItem(player, id, material, amount, alignment, offsetX, offsetY, scale, textAlignment)
                val targetSet = if (isGlobal) globalComponentIds else stateComponentIds
                targetSet.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }.add(id)
            }
        }
    }

    private fun updateDynamicComponents(player: Player) {
        val dynamicTexts = activeDynamicTexts[player.uniqueId] ?: return
        for (comp in dynamicTexts) {
            val parsedContent = parseContent(player, comp.rawContent)
            if (comp.lastRenderedContent != parsedContent) {
                ObserverHook.updateText(player, comp.id, parsedContent)
                comp.lastRenderedContent = parsedContent
            }
        }
    }

    private fun parseContent(player: Player, text: String): String {
        var parsed = text
            .replace("%player_name%", player.name)

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            parsed = PlaceholderAPI.setPlaceholders(player, parsed)
        }
        return parsed
    }

    private fun getAlignment(name: String?): ComponentAlignment {
        return try {
            if (name == null) ComponentAlignment.TOP_CENTER else ComponentAlignment.valueOf(name.uppercase())
        } catch (e: Exception) {
            ComponentAlignment.TOP_CENTER
        }
    }

    private fun getTextAlignment(name: String?): TextAlignment {
        return try {
            if (name == null) TextAlignment.LEFT else TextAlignment.valueOf(name.uppercase())
        } catch (e: Exception) {
            TextAlignment.LEFT
        }
    }

    data class DynamicTextComponent(
        val id: String,
        val rawContent: String,
        var lastRenderedContent: String? = null
    )

    fun shutdown() {
        updateTask?.cancel()
        plugin.server.onlinePlayers.forEach {
            if (ObserverHook.hasObserver(it)) {
                ObserverHook.clearHUD(it)
            }
        }
        activeStates.clear()
        globalComponentIds.clear()
        stateComponentIds.clear()
        activeDynamicTexts.clear()
        loggedObserverPlayers.clear()
    }
}
