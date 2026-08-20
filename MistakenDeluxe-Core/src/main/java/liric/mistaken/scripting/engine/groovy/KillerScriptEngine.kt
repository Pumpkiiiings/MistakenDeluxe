package liric.mistaken.scripting.engine.groovy

import liric.mistaken.roles.killers.Killer
import org.bukkit.Bukkit
import pumpking.lib.color.ColorTranslator
import java.io.File
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import org.codehaus.groovy.control.CompilerConfiguration
import liric.mistaken.scripting.security.groovy.ScriptSecurityScanner

object KillerScriptEngine {

    // Mantenemos una referencia a los classloaders activos para poder cerrarlos
    private val activeLoaders = mutableMapOf<String, groovy.lang.GroovyClassLoader>()

    /**
     * Lee, valida y compila un archivo .groovy.
     * Retorna una instancia de Killer si todo sale bien.
     */
    fun loadKillerScript(file: File): Killer? {
        if (!file.exists() || !file.name.endsWith(".groovy")) return null

        val content = file.readText(Charsets.UTF_8)
        
        // 1. EscÃ¡ner de seguridad
        if (!ScriptSecurityScanner.isSafe(content, file.name)) {
            return null
        }

        // 2. ClassLoader Aislado con ConfiguraciÃ³n Personalizada
        val config = CompilerConfiguration()
        config.addCompilationCustomizers(GroovyBukkitCompatibilityCustomizer())
        val loader = groovy.lang.GroovyClassLoader(KillerScriptEngine::class.java.classLoader, config)
        return try {
            val scriptClass = loader.parseClass(content)
            val scriptInstance = scriptClass.getDeclaredConstructor().newInstance() as groovy.lang.Script
            val result = scriptInstance.run()
            
            if (result is Killer) {
                // Registrar el classloader
                activeLoaders[result.id] = loader
                Bukkit.getLogger().info("[Mistaken] Cargado exitosamente asesino por script: ${result.id} (Clase: ${scriptClass.name})")
                result
            } else {
                Bukkit.getLogger().severe("[Mistaken] Error: El script ${file.name} no termina devolviendo un objeto Killer.")
                loader.close()
                null
            }
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[Mistaken] Error inesperado al ejecutar ${file.name}: ${e.message}")
            e.printStackTrace()
            loader.close()
            null
        }
    }
    
    /**
     * Limpia el ClassLoader asociado a un Killer especÃ­fico.
     */
    fun unloadKillerScript(id: String) {
        val loader = activeLoaders.remove(id)
        if (loader != null) {
            try {
                loader.clearCache()
                loader.close()
                Bukkit.getLogger().info("[Mistaken] ClassLoader descargado para el script: $id")
            } catch (e: Exception) {
                Bukkit.getLogger().warning("[Mistaken] Error al cerrar ClassLoader de $id: ${e.message}")
            }
        }
    }
}

