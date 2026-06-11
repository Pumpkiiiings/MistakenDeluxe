package liric.mistaken.game.managers.engine

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
 *
 * ACTUALIZACIONES:
 * - Agregado GameRule.FALL_DAMAGE = false nativo.
 * - Threading 100% Paper-Safe (Cero crashes al cargar el mundo).
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

        // --- FASE 1: DISCO (Hilo Asíncrono de Paper para no laguear el server) ---
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                if (!fileLoader.worldExists(templateName)) {
                    plugin.componentLogger.error(plugin.mm.deserialize("[ERROR] [MapManager] Slime file '$templateName' does not exist."))
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

                // --- FASE 2: REGISTRO (Hilo Principal Global de Paper) ---
                plugin.server.globalRegionScheduler.execute(plugin) {
                    try {
                        val instance = asp.loadWorld(worldInstance, false)
                        val bukkitWorld = instance.bukkitWorld

                        if (bukkitWorld == null) {
                            plugin.componentLogger.error(plugin.mm.deserialize("[ERROR] [MapManager] Bukkit returned a null world."))
                            future.complete(null)
                            return@execute
                        }

                        // --- CONFIGURACIÓN DE AMBIENTE (Tu lógica intacta) ---
                        bukkitWorld.apply {
                            isAutoSave = false
                            time = 18000L // Noche

                            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                            setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                            setGameRule(GameRule.DO_MOB_SPAWNING, false)
                            setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                            setGameRule(GameRule.DO_FIRE_TICK, false)

                            // 🔥 TU CAMBIO: Anulamos el daño de caída globalmente
                            setGameRule(GameRule.FALL_DAMAGE, false)

                            setStorm(false)
                            isThundering = false
                        }

                        plugin.componentLogger.info(plugin.mm.deserialize("[SUCCESS] [MapManager] World instantiated: ${bukkitWorld.name}"))
                        future.complete(bukkitWorld)

                    } catch (e: Exception) {
                        plugin.componentLogger.error(plugin.mm.deserialize("[ERROR] [MapManager] Failed to register world in Bukkit: ${e.message}"))
                        future.complete(null)
                    }
                }

            } catch (e: Exception) {
                plugin.componentLogger.error(plugin.mm.deserialize("[ERROR] [MapManager] Critical failure loading $templateName: ${e.message}"))
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

        // La descarga SIEMPRE debe ocurrir en el hilo principal
        plugin.server.globalRegionScheduler.execute(plugin) {
            Bukkit.unloadWorld(world, false)
            plugin.componentLogger.info(plugin.mm.deserialize("[INFO] [MapManager] World ${world.name} unloaded."))
        }
    }

    fun shutdown() {
        // En Paper 1.21.4 ya no ocupamos cancelar corrutinas porque los schedulers
        // del servidor se limpian solos al apagar el plugin. ¡Menos RAM gastada!
    }
}