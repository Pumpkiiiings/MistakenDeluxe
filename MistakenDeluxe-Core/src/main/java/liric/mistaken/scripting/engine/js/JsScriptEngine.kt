package liric.mistaken.scripting.engine.js

import liric.mistaken.scripting.api.ScriptRole
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import java.io.File
import java.util.logging.Logger

object JsScriptEngine {

    fun loadScript(file: File, scriptId: String, logger: Logger): ScriptRole? {
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
