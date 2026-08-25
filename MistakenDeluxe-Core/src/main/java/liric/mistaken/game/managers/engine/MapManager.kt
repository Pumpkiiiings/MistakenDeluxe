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
import liric.mistaken.utils.color.ColorTranslator


class MapManager(private val plugin: Mistaken) {

    private val asp = AdvancedSlimePaperAPI.instance()
    private val fileLoader: SlimeLoader

    init {
        val customPath = plugin.config.getString("settings.slime-worlds-path")
        val slimeFolder = if (!customPath.isNullOrEmpty()) File(customPath) else File(plugin.dataFolder, "slime_worlds")
        if (!slimeFolder.exists()) slimeFolder.mkdirs()
        this.fileLoader = FileLoader(slimeFolder)
    }

    /**
     * Carga un world de arena desde una plantilla .slime.
     */
    fun loadArenaWorld(templateName: String): CompletableFuture<World?> {
        val future = CompletableFuture<World?>()
        val instanceName = "${templateName}_${System.currentTimeMillis()}"

        
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                if (!fileLoader.worldExists(templateName)) {
                    plugin.componentLogger.error(liric.mistaken.utils.color.ColorTranslator.translate("<red>[ERROR]</red> <gray>Slime file '$templateName' does not exist.</gray>"))
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

                
                plugin.server.globalRegionScheduler.execute(plugin) {
                    try {
                        val instance = asp.loadWorld(worldInstance, false)
                        val bukkitWorld = instance.bukkitWorld

                        if (bukkitWorld == null) {
                            plugin.componentLogger.error(liric.mistaken.utils.color.ColorTranslator.translate("<red>[ERROR]</red> <gray>Bukkit returned a null world.</gray>"))
                            future.complete(null)
                            return@execute
                        }

                        
                        bukkitWorld.apply {
                            isAutoSave = false
                            time = 18000L 

                            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                            setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                            setGameRule(GameRule.DO_MOB_SPAWNING, false)
                            setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                            setGameRule(GameRule.DO_FIRE_TICK, false)

                            
                            setGameRule(GameRule.FALL_DAMAGE, false)

                            setStorm(false)
                            isThundering = false
                        }

                        plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>World instantiated: ${bukkitWorld.name}</gray>"))
                        future.complete(bukkitWorld)

                    } catch (e: Exception) {
                        plugin.componentLogger.error(liric.mistaken.utils.color.ColorTranslator.translate("<red>[ERROR]</red> <gray>Failed to register world in Bukkit: ${e.message}</gray>"))
                        future.complete(null)
                    }
                }

            } catch (e: Exception) {
                plugin.componentLogger.error(liric.mistaken.utils.color.ColorTranslator.translate("<red>[ERROR]</red> <gray>Critical failure loading $templateName: ${e.message}</gray>"))
                e.printStackTrace()
                future.complete(null)
            }
        }

        return future
    }

    /**
     * Descarga un world sin guardar cambios de forma segura.
     */
    fun unloadWorld(world: World?) {
        if (world == null) return

        
        plugin.server.globalRegionScheduler.execute(plugin) {
            Bukkit.unloadWorld(world, false)
            plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>World ${world.name} unloaded.</gray>"))
        }
    }

    fun shutdown() {
        
        
    }
}
