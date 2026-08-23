package liric.mistaken.jsaddon

import liric.mistaken.scripting.api.ScriptRole
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import java.io.File
import java.util.logging.Logger
import liric.mistaken.scripting.effects.EffectRegistry

object JsScriptEngine {

    fun init(logger: Logger, scriptsDir: File) {
        // Load JS scripts from the killers and survivors subdirectories
        val killersDir = File(scriptsDir, "killers")
        val survivorsDir = File(scriptsDir, "survivors")
        
        loadScriptsFromDir(killersDir, logger, true)
        loadScriptsFromDir(survivorsDir, logger, false)
    }

    private fun loadScriptsFromDir(dir: File, logger: Logger, isKiller: Boolean) {
        if (!dir.exists() || !dir.isDirectory) return

        dir.listFiles { file -> file.name.endsWith(".js") }?.forEach { file ->
            val scriptId = file.nameWithoutExtension.lowercase()
            val role = loadScript(file, scriptId, logger)
            if (role != null) {
                if (isKiller) {
                    val adapter = liric.mistaken.scripting.adapter.LuaKillerAdapter(
                        id = scriptId,
                        nombre = scriptId.replaceFirstChar { it.uppercase() },
                        scriptRole = role
                    )
                    liric.mistaken.api.MistakenProvider.get().killerManager.registerClass(adapter)
                } else {
                    // TODO: Implement Survivor registration if needed
                }
                logger.info("Loaded JS script: ${file.name}")
            }
        }
    }

    private fun loadScript(file: File, scriptId: String, logger: Logger): ScriptRole? {
        try {
            val context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup { false } // Sandbox: don't allow arbitrary Java classes
                .build()

            // Bind global functions from JsEffectBindings
            JsEffectBindings.bindGlobals(context, scriptId)

            // Evaluate the script
            context.eval("js", file.readText())

            // Get the main export object (killer/survivor)
            // In JS, it could be a global variable matching the file name or a default export
            // Assuming they assign it to a global variable like `killer` or we just grab the last evaluated value
            var result = context.eval("js", "killer")
            if (result == null || result.isNull) {
                 result = context.eval("js", "survivor")
            }

            if (result == null || result.isNull || !result.hasMembers()) {
                logger.severe("Script ${file.name} must declare a global 'killer' or 'survivor' object.")
                return null
            }

            return JsRoleWrapper(scriptId, result, context)

        } catch (e: Exception) {
            logger.severe("Error loading JS script ${file.name}: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
