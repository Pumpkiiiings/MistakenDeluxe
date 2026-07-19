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

/**
 * LIRIC-MISTAKEN 2.0
 * ObserverHUDManager: Maneja el dibujado de componentes en la pantalla del jugador mediante ObserverAPI.
 */
class ObserverHUDManager(private val plugin: Mistaken) {

    private val configFile: File = File(plugin.dataFolder, "observer-hud.yml")
    private var config: YamlConfiguration = YamlConfiguration()

    // Estado actual pintado para no sobreescribir: Map<PlayerUUID, State>
    private val activeStates = ConcurrentHashMap<java.util.UUID, String>()
    // Componentes dinámicos que deben ser actualizados
    private val activeDynamicTexts = ConcurrentHashMap<java.util.UUID, MutableSet<DynamicTextComponent>>()
    // IDs de los componentes globales (nunca se borran mientras el jugador esté)
    private val globalComponentIds = ConcurrentHashMap<java.util.UUID, MutableSet<String>>()
    // IDs de los componentes de estado actual para borrado manual
    private val stateComponentIds = ConcurrentHashMap<java.util.UUID, MutableSet<String>>()

    private var updateTask: org.bukkit.scheduler.BukkitTask? = null
    private val loggedObserverPlayers = ConcurrentHashMap.newKeySet<java.util.UUID>()

    init {
        loadConfig()
        startUpdateTask()
    }

    fun loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("observer-hud.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    private fun startUpdateTask() {
        updateTask = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            for (player in plugin.server.onlinePlayers) {
                if (!ObserverHook.hasObserver(player)) continue
                
                if (loggedObserverPlayers.add(player.uniqueId)) {
                    plugin.componentLogger.info(pumpking.lib.color.ColorTranslator.translate("[<green>ObserverHook</green>] <white>¡El jugador <yellow>${player.name}</yellow> se ha detectado con el mod instalado!</white>"))
                }

                handlePlayerState(player)
                updateDynamicComponents(player)
            }
        }, 20L, 20L) // 1 segundo
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

        // 2. Transición de estados
        if (activeStates[player.uniqueId] != stateName) {
            activeStates[player.uniqueId] = stateName
            clearPlayerStateComponents(player) // Borra estado anterior sin tocar global
            
            if (stateName != "none") {
                drawComponents(player, stateName, false)
            }
            
            // Si entra a ingame, tratar de dibujar el rol
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
            // pero podemos dibujar encima o confiar en que se llame después de un clearHUD().
            drawComponents(player, "roles.$role", false)
        }
    }

    private fun clearPlayerStateComponents(player: Player) {
        if (ObserverHook.hasObserver(player)) {
            val ids = stateComponentIds.remove(player.uniqueId)
            ids?.forEach { ObserverHook.removeComponent(player, it) }
            
            // Removemos los textos dinámicos asociados al estado
            val dynamic = activeDynamicTexts[player.uniqueId]
            dynamic?.removeIf { ids?.contains(it.id) == true }
        }
    }

    fun clearPlayer(player: Player) {
        if (ObserverHook.hasObserver(player)) {
            ObserverHook.clearHUD(player) // Borra todo físicamente al salir
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
