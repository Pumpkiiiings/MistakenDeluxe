package liric.mistaken.game.managers

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI
import com.infernalsuite.asp.api.loaders.SlimeLoader
import com.infernalsuite.asp.api.world.properties.SlimeProperties
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap
import com.infernalsuite.asp.loaders.file.FileLoader
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 *[LIRIC-MISTAKEN 2.0]
 * MapManager: Gestión de mundos dinámicos con AdvancedSlimePaper (ASP).
 * FIX: Paper-Safe Threading (GlobalRegionScheduler) para evitar crashes al cargar mundos.
 */
class MapManager(private val plugin: Mistaken) {

    private val asp = AdvancedSlimePaperAPI.instance()
    private val fileLoader: SlimeLoader

    init {
        val slimeFolder = File(plugin.dataFolder, "slime_worlds")
        if (!slimeFolder.exists()) slimeFolder.mkdirs()
        this.fileLoader = FileLoader(slimeFolder)
    }

    /**
     * Carga un mundo de arena desde una plantilla .slime.
     */
    fun loadArenaWorld(templateName: String): CompletableFuture<World?> {
        val future = CompletableFuture<World?>()
        val instanceName = "${templateName}_${System.currentTimeMillis()}"

        // --- FASE 1: DISCO (Hilo Asíncrono de Paper - Cero Lag) ---
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                if (!fileLoader.worldExists(templateName)) {
                    plugin.componentLogger.error(plugin.mm.deserialize("<red>[MapManager] El archivo .slime '$templateName' no existe.</red>"))
                    future.complete(null)
                    return@runNow
                }

                val props = SlimePropertyMap().apply {
                    setValue(SlimeProperties.ALLOW_ANIMALS, false)
                    setValue(SlimeProperties.ALLOW_MONSTERS, false)
                    setValue(SlimeProperties.PVP, true)
                }

                val template = asp.readWorld(fileLoader, templateName, true, props)
                val worldInstance = template.clone(instanceName)

                // --- FASE 2: REGISTRO EN BUKKIT (Hilo Principal Obligatorio) ---
                plugin.server.scheduler.runTask(plugin, Runnable {
                    try {
                        val instance = asp.loadWorld(worldInstance, false)
                        val bukkitWorld = instance.bukkitWorld

                        if (bukkitWorld == null) {
                            future.complete(null)
                            return@Runnable
                        }

                        // Configuración ambiental rápida
                        bukkitWorld.isAutoSave = false
                        bukkitWorld.time = 18000L // Noche
                        bukkitWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                        bukkitWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                        bukkitWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                        bukkitWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
                        bukkitWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                        bukkitWorld.setGameRule(GameRule.DO_FIRE_TICK, false)
                        bukkitWorld.setStorm(false)
                        bukkitWorld.isThundering = false

                        plugin.componentLogger.info(plugin.mm.deserialize("<green>[MapManager] Mundo instanciado: ${bukkitWorld.name}</green>"))
                        future.complete(bukkitWorld)

                    } catch (e: Exception) {
                        plugin.componentLogger.error(plugin.mm.deserialize("<red>[MapManager] Error al registrar mundo: ${e.message}</red>"))
                        future.complete(null)
                    }
                })

            } catch (e: Exception) {
                plugin.componentLogger.error(plugin.mm.deserialize("<red>[MapManager] Fallo crítico cargando $templateName</red>"))
                e.printStackTrace()
                future.complete(null)
            }
        }

        return future
    }

    /**
     * Descarga un mundo sin guardar cambios de forma segura.
     */
    fun unloadWorld(world: World?) {
        if (world == null) return

        // La descarga SIEMPRE debe ser síncrona en el hilo principal
        plugin.server.scheduler.runTask(plugin, Runnable {
            Bukkit.unloadWorld(world, false)
            plugin.componentLogger.info(plugin.mm.deserialize("<gray>Mundo ${world.name} descargado.</gray>"))
        })
    }

    fun shutdown() {
        // Nada que cancelar, Paper maneja los schedulers.
    }
}
