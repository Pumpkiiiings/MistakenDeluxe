package liric.mistaken.scripting.engine.lua

import liric.mistaken.scripting.api.ScriptContext
import liric.mistaken.scripting.api.ScriptEvent
import liric.mistaken.scripting.api.ScriptKiller
import liric.mistaken.scripting.api.ScriptPlayer
import org.bukkit.Bukkit
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.io.File
import liric.mistaken.scripting.security.lua.LuaEnvironment
import liric.mistaken.scripting.security.lua.LuaSandbox

object LuaScriptEngine {

    /**
     * Carga y aísla un script Lua desde un archivo.
     */
    fun loadScript(file: File, killerId: String): ScriptKiller? {
        if (!file.exists() || !file.name.endsWith(".lua")) return null

        try {
            val env = LuaSandbox.createEnvironment(killerId)
            val chunk = env.globals.loadfile(file.absolutePath)
            val result = chunk.call()
            
            if (!result.istable()) {
                Bukkit.getLogger().severe("[Mistaken Script Engine] Error: El script ${file.name} no retorna una tabla Lua.")
                return null
            }
            
            return LuaKillerWrapper(killerId, result, env)
            
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[Mistaken Script Engine] Error cargando Lua script ${file.name}: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}

class LuaKillerWrapper(
    private val id: String,
    private val luaTable: LuaValue,
    private val env: LuaEnvironment
) : ScriptKiller {

    override fun id(): String = id

    override fun model_id(): String? {
        val modelField = luaTable.get("model")
        return if (modelField.isstring()) modelField.tojstring() else null
    }

    override fun on_load(context: ScriptContext) {
        callFunction("on_load", CoerceJavaToLua.coerce(context))
    }

    override fun on_equip(player: ScriptPlayer) {
        callFunction("on_equip", CoerceJavaToLua.coerce(player))
    }

    override fun on_unequip(player: ScriptPlayer) {
        callFunction("on_unequip", CoerceJavaToLua.coerce(player))
    }

    override fun on_tick() {
        callFunction("on_tick")
    }

    override fun on_disable() {
        callFunction("on_disable")
    }

    override fun dispatch_event(event: ScriptEvent) {
        val eventName = event.event_name()
        callFunction("on_$eventName", CoerceJavaToLua.coerce(event))
    }

    override fun has_trigger(): Boolean {
        var func = env.globals.get("on_trigger")
        if (!func.isfunction()) {
            func = luaTable.get("on_trigger")
        }
        return func.isfunction()
    }

    override fun on_trigger(player: ScriptPlayer, triggerId: String) {
        var func = env.globals.get("on_trigger")
        if (!func.isfunction()) {
            func = luaTable.get("on_trigger")
        }
        if (func.isfunction()) {
            try {
                env.debugHook.resetCounter()
                func.invoke(CoerceJavaToLua.coerce(player), LuaValue.valueOf(triggerId))
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[Mistaken Script Engine] Error ejecutando on_trigger en $id: ${e.message}")
            }
        }
    }

    override fun on_intercept_chat(player: ScriptPlayer, message: String): String? {
        val callback = liric.mistaken.scripting.effects.gameplay.ChatInterceptorRegistry.getCallback(id) ?: return null
        val bukkitPlayer = org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(player.uuid())) ?: return null
        return callback(bukkitPlayer, message)
    }

    override fun on_kill(killer: ScriptPlayer, victim: ScriptPlayer) {
        val func = luaTable.get("on_kill")
        if (func.isfunction()) {
            try {
                env.debugHook.resetCounter()
                func.invoke(org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(killer), org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(victim))
            } catch (e: Exception) {
                org.bukkit.Bukkit.getLogger().severe("[Mistaken Script Engine] Error ejecutando on_kill en $id: ${e.message}")
            }
        }
    }

    private fun callFunction(funcName: String, vararg args: LuaValue) {
        var func = env.globals.get(funcName)
        if (!func.isfunction()) {
            func = luaTable.get(funcName)
        }
        if (func.isfunction()) {
            try {
                env.debugHook.resetCounter()
                func.invoke(args)
            } catch (e: Exception) {
                Bukkit.getLogger().severe("[Mistaken Script Engine] Error ejecutando $funcName en $id: ${e.message}")
            }
        }
    }
}


