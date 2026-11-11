package liric.mistaken.roles.shared

import liric.mistaken.Mistaken
import liric.mistaken.api.roles.GameRole
import liric.mistaken.scripting.api.ScriptRole
import liric.mistaken.scripting.engine.lua.LuaScriptEngine
import liric.mistaken.utils.color.ColorTranslator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Clase base para manejar los roles (Killers y Survivors).
 * Centraliza la inicializacion, limpieza e iteracion segura.
 */
abstract class AbstractRoleManager<T : GameRole>(protected val plugin: Mistaken) {

    protected val activeRoles = ConcurrentHashMap<UUID, T>()
    protected val availableClasses = ConcurrentHashMap<String, T>()

    val catalogo: Map<String, T> get() = availableClasses

    open fun getClassById(id: String?): T? {
        return id?.lowercase()?.let { availableClasses[it] }
    }

    abstract fun registerClass(role: T)

    /** Copies bundled default scripts if missing and loads all `.lua` (and optional extra) roles from `scripts/<subFolder>`. */
    protected fun loadScriptsFromFolder(
        subFolder: String,
        defaultLuaScripts: List<String>,
        supportsGroovy: Boolean = false,
        luaRoleFactory: (id: String, name: String, scriptRole: ScriptRole) -> T,
        extraLoader: ((File) -> T?)? = null
    ): Int {
        val scriptsFolder = File(plugin.dataFolder, "scripts/$subFolder")
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs()
        }

        try {
            for (script in defaultLuaScripts) {
                val luaExists = File(scriptsFolder, "$script.lua").exists()
                val groovyExists = supportsGroovy && File(scriptsFolder, "$script.groovy").exists()
                if (!luaExists && !groovyExists) {
                    plugin.saveResource("scripts/$subFolder/$script.lua", false)
                }
            }
        } catch (e: Exception) {
            plugin.componentLogger.warn(
                ColorTranslator.translate("<yellow>[WARN]</yellow> <gray>Failed to copy default $subFolder scripts.</gray>")
            )
        }

        val files = scriptsFolder.listFiles() ?: return 0
        var loadedCount = 0
        for (file in files) {
            val loaded: T? = when {
                extraLoader != null && file.name.endsWith(".groovy") -> extraLoader(file)
                file.name.endsWith(".lua") -> {
                    val roleId = file.nameWithoutExtension.lowercase()
                    LuaScriptEngine.loadScript(file, roleId)?.let { scriptRole ->
                        luaRoleFactory(roleId, roleId.replaceFirstChar { it.uppercase() }, scriptRole)
                    }
                }
                else -> null
            }
            if (loaded != null) {
                registerClass(loaded)
                loadedCount++
            }
        }
        return loadedCount
    }

    /**
     * Limpia la data base de un rol del player sin destruir instancias singleton si el player ya no existe en memoria.
     */
    protected open fun removeRoleLogic(uuid: UUID, player: Player?) {
        val role = activeRoles.remove(uuid) ?: return

        if (player == null) return

        if (player.isOnline) {
            player.scheduler.run(plugin, { _ ->
                role.cleanup(player)
                onRoleRemoved(player)
            }, null)
        } else {
            role.cleanup(player)
        }
    }

    /**
     * Hook para logicas adicionales luego de limpiar el rol base.
     */
    protected open fun onRoleRemoved(player: Player) {}

    open fun cleanAll() {
        for (uuid in activeRoles.keys.toList()) {
            val player = Bukkit.getPlayer(uuid)
            removeRoleLogic(uuid, player)
        }
    }

    open fun shutdown() {
        cleanAll()
    }
}
