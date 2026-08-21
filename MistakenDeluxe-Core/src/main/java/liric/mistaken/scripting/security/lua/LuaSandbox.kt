package liric.mistaken.scripting.security.lua

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.CoroutineLib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseMathLib

/**
 * Sandboxing estricto para LuaJ.
 * - Bloquea instanciaciÃ³n de classes Java (sin luajava).
 * - Bloquea acceso a IO y OS.
 * - LÃ­mite estricto de instrucciones para evitar cuelgues (Billion Laughs / while true).
 */
class LuaEnvironment(val globals: Globals, val debugHook: InstructionLimitDebugLib)

class LuaSandbox {

    companion object {
        const val MAX_INSTRUCTIONS_PER_TICK = 50000

        /**
         * Crea un entorno Globals completamente nuevo y aislado.
         */
        fun createEnvironment(scriptId: String = "unknown"): LuaEnvironment {
            val globals = Globals()
            
            // LuaJ libraries attempt to register themselves in package.loaded
            // Since we omit PackageLib for security, we must mock package.loaded to avoid NPEs
            val packageTable = org.luaj.vm2.LuaTable()
            packageTable.set("loaded", org.luaj.vm2.LuaTable())
            globals.set("package", packageTable)
            
            globals.load(JseBaseLib())
            globals.load(TableLib())
            globals.load(StringLib())
            globals.load(JseMathLib())
            globals.load(Bit32Lib())
            globals.load(CoroutineLib())
            
            globals.set("dofile", LuaValue.NIL)
            globals.set("loadfile", LuaValue.NIL)
            globals.set("load", LuaValue.NIL)
            
            org.luaj.vm2.LoadState.install(globals)
            LuaC.install(globals)
            
            val debugHook = InstructionLimitDebugLib(MAX_INSTRUCTIONS_PER_TICK)
            globals.load(debugHook)
            
            globals.set("debug", LuaValue.NIL)

            // Install effect DSL bindings (orbit, trail, projectile, dash, damage, etc.)
            liric.mistaken.scripting.effects.lua.LuaEffectBindings.install(globals, scriptId)

            return LuaEnvironment(globals, debugHook)
        }
    }
}

/**
 * Custom DebugLib para restringir el tiempo de ejecuciÃ³n (instrucciones CPU).
 */
class InstructionLimitDebugLib(private val instructionLimit: Int) : DebugLib() {
    private var instructionsExecuted = 0
    
    override fun onInstruction(pc: Int, v: org.luaj.vm2.Varargs?, top: Int) {
        instructionsExecuted++
        if (instructionsExecuted > instructionLimit) {
            throw LuaError("Instruction limit exceeded. Script may be stuck in an infinite loop.")
        }
        super.onInstruction(pc, v, top)
    }

    /**
     * Resetea el contador. Debe llamarse antes de ejecutar un evento de script.
     */
    fun resetCounter() {
        instructionsExecuted = 0
    }
}


