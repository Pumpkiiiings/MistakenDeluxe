package liric.mistaken.game.managers

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI
import com.infernalsuite.asp.api.loaders.SlimeLoader
import com.infernalsuite.asp.api.world.properties.SlimeProperties
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap
import com.infernalsuite.asp.loaders.file.FileLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * [LIRIC-MISTAKEN 2.0]
 * MapManager: Gestión de mundos dinámicos con AdvancedSlimePaper (ASP).
 * Optimizado para carga asíncrona y configuración de ambiente tétrico.
 */
class MapManager(private val plugin: Mistaken) {

    private val asp = AdvancedSlimePaperAPI.instance()
    private val fileLoader: SlimeLoader

    // Scope dedicado para no interferir con el ciclo de vida del plugin
    private val mapScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val slimeFolder = File(plugin.dataFolder, "slime_worlds")
        if (!slimeFolder.exists()) slimeFolder.mkdirs()
        this.fileLoader = FileLoader(slimeFolder)
    }

    /**
     * Carga un mundo de arena desde una plantilla .slime.
     * Retorna un CompletableFuture para mantener compatibilidad con el GameManager.
     */
    fun loadArenaWorld(templateName: String): CompletableFuture<World?> {
        // Usamos async para manejar la tarea asíncronamente y convertirlo a CompletableFuture
        return mapScope.async {
            val instanceName = "${templateName}_${System.currentTimeMillis()}"

            try {
                // 1. Verificación y lectura del disco (Hilo IO)
                if (!fileLoader.worldExists(templateName)) {
                    plugin.logger.severe("No existe el archivo .slime para: $templateName")
                    return@async null
                }

                val props = SlimePropertyMap().apply {
                    setValue(SlimeProperties.ALLOW_ANIMALS, false)
                    setValue(SlimeProperties.ALLOW_MONSTERS, false)
                    setValue(SlimeProperties.PVP, true)
                }

                // Leer mundo desde el cargador
                val template = asp.readWorld(fileLoader, templateName, true, props)
                val worldInstance = template.clone(instanceName)

                // 2. Registro en Bukkit (Debe ser en el Hilo Principal)
                return@async withContext(Dispatchers.Main) {
                    try {
                        val instance = asp.loadWorld(worldInstance, false)
                        val bukkitWorld = instance.bukkitWorld ?: return@withContext null

                        // --- 🌙 CONFIGURACIÓN DE NOCHE ETERNA & OPTIMIZACIÓN ---
                        bukkitWorld.apply {
                            isAutoSave = false // No queremos basura en el disco
                            time = 18000L      // Medianoche cerrada

                            // Reglas de juego para evitar cambios de ambiente
                            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                            setGameRule(GameRule.DO_MOB_SPAWNING, false)
                            setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)

                            setStorm(false)
                            isThundering = false
                        }

                        plugin.logger.info("Mundo instanciado correctamente: ${bukkitWorld.name}")
                        bukkitWorld
                    } catch (e: Exception) {
                        plugin.logger.severe("Error al cargar la instancia de Bukkit para $templateName: ${e.message}")
                        null
                    }
                }

            } catch (e: Exception) {
                plugin.logger.severe("Fallo crítico en MapManager para $templateName: ${e.message}")
                e.printStackTrace()
                null
            }
        }.asCompletableFuture()
    }

    /**
     * Descarga un mundo sin guardar cambios, ideal para arenas temporales.
     */
    fun unloadWorld(world: World?) {
        if (world == null) return

        // ASP recomienda descargar los mundos de Bukkit normalmente si no se requiere persistencia
        Bukkit.unloadWorld(world, false)
    }

    /**
     * Cancela todas las tareas de carga pendientes si el plugin se apaga.
     */
    fun shutdown() {
        mapScope.cancel()
    }
}
