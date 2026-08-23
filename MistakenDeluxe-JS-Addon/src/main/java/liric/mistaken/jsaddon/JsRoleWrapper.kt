package liric.mistaken.jsaddon

import liric.mistaken.scripting.api.ScriptContext
import liric.mistaken.scripting.api.ScriptEvent
import liric.mistaken.scripting.api.ScriptRole
import liric.mistaken.scripting.api.ScriptPlayer
import org.bukkit.Bukkit
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

class JsRoleWrapper(
    private val id: String,
    private val jsObject: Value,
    private val context: Context
) : ScriptRole {

    override fun id(): String = id

    override fun model_id(): String? {
        val modelField = jsObject.getMember("model")
        return if (modelField != null && modelField.isString) modelField.asString() else null
    }

    override fun on_load(scriptContext: ScriptContext) {
        callFunction("on_load", scriptContext)
    }

    override fun on_equip(player: ScriptPlayer) {
        callFunction("on_equip", player)
    }

    override fun on_unequip(player: ScriptPlayer) {
        callFunction("on_unequip", player)
    }

    override fun on_tick() {
        callFunction("on_tick")
    }

    override fun on_disable() {
        callFunction("on_disable")
    }

    override fun dispatch_event(event: ScriptEvent) {
        callFunction("on_${event.event_name()}", event)
    }

    override fun has_trigger(): Boolean {
        var func = context.getBindings("js").getMember("on_trigger")
        if (func == null || !func.canExecute()) {
            func = jsObject.getMember("on_trigger")
        }
        return func != null && func.canExecute()
    }

    override fun on_trigger(player: ScriptPlayer, triggerId: String) {
        var func = context.getBindings("js").getMember("on_trigger")
        if (func == null || !func.canExecute()) {
            func = jsObject.getMember("on_trigger")
        }
        if (func != null && func.canExecute()) {
            try {
                func.execute(player, triggerId)
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[Mistaken JS Addon] Error executing on_trigger in $id: ${e.message}")
            }
        }
    }

    override fun on_intercept_chat(player: ScriptPlayer, message: String): String? {
        val callback = liric.mistaken.scripting.effects.gameplay.ChatInterceptorRegistry.getCallback(id) ?: return null
        val bukkitPlayer = org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(player.uuid())) ?: return null
        return callback(bukkitPlayer, message)
    }

    override fun on_kill(killer: ScriptPlayer, victim: ScriptPlayer) {
        callFunction("on_kill", killer, victim)
    }

    override fun on_melee_attack(attacker: ScriptPlayer, victim: ScriptPlayer, slot: Int) {
        callFunction("on_melee_attack", attacker, victim, slot)
    }

    private fun callFunction(funcName: String, vararg args: Any) {
        var func = context.getBindings("js").getMember(funcName)
        if (func == null || !func.canExecute()) {
            func = jsObject.getMember(funcName)
        }
        if (func != null && func.canExecute()) {
            try {
                func.execute(*args)
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[Mistaken JS Addon] Error executing $funcName in $id: ${e.message}")
            }
        }
    }
}
