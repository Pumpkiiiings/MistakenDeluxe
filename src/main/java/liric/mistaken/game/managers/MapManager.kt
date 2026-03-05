package liric.mistaken.game.managers

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI
import com.infernalsuite.asp.api.loaders.SlimeLoader
import com.infernalsuite.asp.api.world.properties.SlimeProperties
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap
import com.infernalsuite.asp.loaders.file.FileLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import liric.mistaken.Mistaken
import liric.mistaken.utils.mainThread
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * [LIRIC-MISTAKEN 2.0]
 * MapManager: Gestión de mundos dinámicos con AdvancedSlimePaper (ASP).
 *
 * Actualización:
 * - Agregado GameRule.FALL_DAMAGE = false para anular daño de caída nativamente.
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

                val template = asp.readWorld(fileLoader, templateName, true, props)
                val worldInstance = template.clone(instanceName)

                // --- FASE 2: REGISTRO (Hilo Principal de Bukkit) ---
                return@async withContext(plugin.mainThread) {
                    try {
                        val instance = asp.loadWorld(worldInstance, false)
                        val bukkitWorld = instance.bukkitWorld ?: return@withContext null

                        // --- CONFIGURACIÓN DE AMBIENTE (1.21.4) ---
                        bukkitWorld.apply {
                            isAutoSave = false
                            time = 18000L // Noche

                            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                            setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                            setGameRule(GameRule.DO_MOB_SPAWNING, false)
                            setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                            setGameRule(GameRule.DO_FIRE_TICK, false)

                            // 🔥 AQUÍ ESTÁ EL CAMBIO: Cancelamos daño de caída globalmente en este mundo
                            setGameRule(GameRule.FALL_DAMAGE, false)

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

        mapScope.launch {
            withContext(plugin.mainThread) {
                Bukkit.unloadWorld(world, false)
                plugin.componentLogger.info(plugin.mm.deserialize("<gray>Mundo ${world.name} descargado.</gray>"))
            }
        }
    }

    fun shutdown() {
        mapScope.cancel()
    }
}
