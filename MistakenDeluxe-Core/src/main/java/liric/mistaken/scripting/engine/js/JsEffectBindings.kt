package liric.mistaken.scripting.engine.js

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import liric.mistaken.scripting.effects.lua.LuaEffectBindings

object JsEffectBindings {

    fun bindGlobals(context: Context, scriptId: String) {
        val bindings = context.getBindings("js")
        
        // 1. Initialize a dummy Lua Environment just to get the bindings
        val luaGlobals = JsePlatform.standardGlobals()
        LuaEffectBindings.install(luaGlobals, scriptId)

        // 2. Iterate through all exported Lua globals and proxy them to JS
        var k = LuaValue.NIL
        while (true) {
            val next: org.luaj.vm2.Varargs = luaGlobals.next(k)
            k = next.arg1()
            if (k.isnil()) break
            
            val keyStr = k.tojstring()
            val luaVal = next.arg(2)
            
            // Only export functions (like orbit, trail, dash, etc.)
            if (luaVal.isfunction()) {
                bindings.putMember(keyStr, LuaValueProxy(luaVal, luaGlobals))
            }
        }
    }
}

class LuaValueProxy(val luaValue: LuaValue, private val globals: Globals) : ProxyObject, ProxyExecutable {

    override fun getMember(key: String): Any? {
        val member = luaValue.get(key)
        if (member.isnil()) return null
        return LuaValueProxy(member, globals)
    }

    override fun getMemberKeys(): Any {
        if (!luaValue.istable()) return emptyArray<String>()
        val keys = mutableListOf<String>()
        var k = LuaValue.NIL
        while (true) {
            val next: org.luaj.vm2.Varargs = luaValue.next(k)
            k = next.arg1()
            if (k.isnil()) break
            keys.add(k.tojstring())
        }
        return keys.toTypedArray()
    }

    override fun hasMember(key: String): Boolean {
        return !luaValue.get(key).isnil()
    }

    override fun putMember(key: String, value: Value) {
        luaValue.set(key, jsToLua(value))
    }

    override fun execute(vararg arguments: Value): Any? {
        val luaArgs = arguments.map { jsToLua(it) }.toTypedArray()
        val result = try {
            luaValue.invoke(luaArgs)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        
        return luaToJs(result.arg1())
    }

    private fun jsToLua(value: Value): LuaValue {
        if (value.isNull) return LuaValue.NIL
        if (value.isBoolean) return LuaValue.valueOf(value.asBoolean())
        if (value.isNumber) return LuaValue.valueOf(value.asDouble())
        if (value.isString) return LuaValue.valueOf(value.asString())
        
        // If JS passed a ProxyObject (e.g. chaining builders), unwrap it
        if (value.isProxyObject) {
            val proxy = value.asProxyObject<ProxyObject>()
            if (proxy is LuaValueProxy) {
                return proxy.luaValue
            }
        }
        
        // Coerce Java objects (like ScriptPlayer, Location) to Lua
        if (value.isHostObject) {
            val obj = value.asHostObject<Any>()
            return CoerceJavaToLua.coerce(obj)
        }

        // If JS passed a function callback
        if (value.canExecute()) {
            return object : org.luaj.vm2.lib.VarArgFunction() {
                override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                    val jsArgs = (1..args.narg()).map { luaToJs(args.arg(it)) }.toTypedArray()
                    val res = value.execute(*jsArgs)
                    return jsToLua(res)
                }
            }
        }

        return LuaValue.NIL
    }

    private fun luaToJs(luaVal: LuaValue): Any? {
        if (luaVal.isnil()) return null
        if (luaVal.isboolean()) return luaVal.toboolean()
        if (luaVal.isint()) return luaVal.toint()
        if (luaVal.isnumber()) return luaVal.todouble()
        if (luaVal.isstring()) return luaVal.tojstring()
        if (luaVal.isuserdata()) return luaVal.checkuserdata()
        
        // If it's a table or function, wrap back in proxy
        return LuaValueProxy(luaVal, globals)
    }
}
