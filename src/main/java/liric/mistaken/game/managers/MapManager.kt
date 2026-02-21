package liric.mistaken.game.managers

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI
import com.infernalsuite.asp.api.loaders.SlimeLoader
import com.infernalsuite.asp.api.world.properties.SlimeProperties
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap
import com.infernalsuite.asp.loaders.file.FileLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import liric.mistaken.Mistaken
import liric.mistaken.utils.mainThread // 1. IMPORTANTE: Usamos nuestro dispatcher
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * [LIRIC-MISTAKEN 2.0]
 * MapManager: Gestión de mundos dinámicos con AdvancedSlimePaper (ASP).
 *
 * Optimización:
 * - Reemplazado Dispatchers.Main por plugin.mainThread (Fix crash).
 * - Carga de archivos en Dispatchers.IO.
 * - Registro de mundo en el Hilo Principal de Bukkit.
 */
class MapManager(private val plugin: Mistaken) {

    private val asp = AdvancedSlimePaperAPI.instance()
    private val fileLoader: SlimeLoader

    // Scope dedicado para operaciones de mapas (IO)
    private val mapScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val slimeFolder = File(plugin.dataFolder, "slime_worlds")
        if (!slimeFolder.exists()) slimeFolder.mkdirs()
        this.fileLoader = FileLoader(slimeFolder)
    }

    /**
     * Carga un mundo de arena desde una plantilla .slime.
     */
    fun loadArenaWorld(templateName: String): CompletableFuture<World?> {
        // Ejecutamos todo dentro de un bloque async en el hilo de IO
        return mapScope.async {
            val instanceName = "${templateName}_${System.currentTimeMillis()}"

            try {
                // --- FASE 1: DISCO (Hilo IO) ---
                if (!fileLoader.worldExists(templateName)) {
                    plugin.componentLogger.error(plugin.mm.deserialize("<red>[MapManager] El archivo .slime '$templateName' no existe.</red>"))
                    return@async null
                }

                val props = SlimePropertyMap().apply {
                    setValue(SlimeProperties.ALLOW_ANIMALS, false)
                    setValue(SlimeProperties.ALLOW_MONSTERS, false)
                    setValue(SlimeProperties.PVP, true)
                }

                // Leer mundo desde el archivo (Esto es pesado, se queda en IO)
                val template = asp.readWorld(fileLoader, templateName, true, props)
                val worldInstance = template.clone(instanceName)

                // --- FASE 2: REGISTRO (Hilo Principal de Bukkit) ---
                // AQUÍ USAMOS plugin.mainThread PARA EVITAR EL CRASH
                return@async withContext(plugin.mainThread) {
                    try {
                        // Cargar la instancia en el servidor de Bukkit
                        val instance = asp.loadWorld(worldInstance, false)
                        val bukkitWorld = instance.bukkitWorld ?: return@withContext null

                        // --- CONFIGURACIÓN DE AMBIENTE (1.21.4) ---
                        bukkitWorld.apply {
                            isAutoSave = false
                            time = 18000L // Noche

                            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                            setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true)
                            setGameRule(GameRule.DO_MOB_SPAWNING, false)
                            setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                            setGameRule(GameRule.DO_FIRE_TICK, false)

                            setStorm(false)
                            isThundering = false
                        }

                        plugin.componentLogger.info(plugin.mm.deserialize("<green>[MapManager] Mundo instanciado: ${bukkitWorld.name}</green>"))
                        bukkitWorld
                    } catch (e: Exception) {
                        plugin.componentLogger.error(plugin.mm.deserialize("<red>[MapManager] Error al registrar mundo en Bukkit: ${e.message}</red>"))
                        null
                    }
                }

            } catch (e: Exception) {
                plugin.componentLogger.error(plugin.mm.deserialize("<red>[MapManager] Fallo crítico cargando $templateName: ${e.message}</red>"))
                e.printStackTrace()
                null
            }
        }.asCompletableFuture()
    }

    /**
     * Descarga un mundo sin guardar cambios de forma segura.
     */
    fun unloadWorld(world: World?) {
        if (world == null) return

        // La descarga debe ocurrir en el hilo principal
        mapScope.launch {
            withContext(plugin.mainThread) {
                Bukkit.unloadWorld(world, false)
                plugin.componentLogger.info(plugin.mm.deserialize("<gray>Mundo ${world.name} descargado.</gray>"))
            }
        }
    }

    /**
     * Cancela todas las cargas pendientes al apagar el plugin.
     */
    fun shutdown() {
        mapScope.cancel()
    }
}
