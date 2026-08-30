package liric.mistaken.game.managers.engine

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI
import com.infernalsuite.asp.api.loaders.SlimeLoader
import com.infernalsuite.asp.api.world.properties.SlimeProperties
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap
import com.infernalsuite.asp.loaders.file.FileLoader
import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.GameRules
import org.bukkit.World
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class SlimeArenaWorldProvider(private val plugin: Mistaken) : ArenaWorldProvider {
    override val backend = ArenaWorldBackend.SLIME
    private val asp = AdvancedSlimePaperAPI.instance()
    private val fileLoader: SlimeLoader

    init {
        val customPath = plugin.config.getString("settings.arena-worlds.slime-worlds-path")
        val folder = if (!customPath.isNullOrBlank()) File(customPath) else File(plugin.dataFolder, "slime_worlds")
        if (!folder.exists() && !folder.mkdirs()) {
            throw IllegalStateException("Could not create slime worlds directory: ${folder.absolutePath}")
        }
        fileLoader = FileLoader(folder)
    }

    override fun hasTemplate(templateName: String): Boolean =
        runCatching { fileLoader.worldExists(templateName) }.getOrDefault(false)

    override fun create(
        templateName: String,
        metadata: Map<String, String>
    ): CompletableFuture<ManagedArenaWorld> {
        val result = CompletableFuture<ManagedArenaWorld>()
        val instanceName = "${templateName}_${UUID.randomUUID().toString().replace("-", "")}"

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                check(fileLoader.worldExists(templateName)) { "Slime template '$templateName' does not exist" }
                val properties = SlimePropertyMap().apply {
                    setValue(SlimeProperties.ALLOW_ANIMALS, false)
                    setValue(SlimeProperties.ALLOW_MONSTERS, false)
                    setValue(SlimeProperties.PVP, true)
                }
                val template = asp.readWorld(fileLoader, templateName, true, properties)
                val clone = template.clone(instanceName)

                plugin.server.globalRegionScheduler.execute(plugin) {
                    try {
                        val world = asp.loadWorld(clone, false).bukkitWorld
                        configure(world)
                        result.complete(ManagedArenaWorld(world, backend) { unload(world) })
                    } catch (error: Throwable) {
                        result.completeExceptionally(error)
                    }
                }
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
        return result
    }

    private fun configure(world: World) {
        world.isAutoSave = false
        world.time = 18000L
        world.setGameRule(GameRules.PVP, true)
        world.setGameRule(GameRules.ADVANCE_TIME, false)
        world.setGameRule(GameRules.ADVANCE_WEATHER, false)
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true)
        world.setGameRule(GameRules.SPAWN_MOBS, false)
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)
        world.setGameRule(GameRules.FALL_DAMAGE, false)
        world.setStorm(false)
        world.isThundering = false
    }

    private fun unload(world: World): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        plugin.server.globalRegionScheduler.execute(plugin) {
            try {
                movePlayersOut(world)
                check(Bukkit.unloadWorld(world, false)) { "Paper refused to unload world ${world.name}" }
                result.complete(null)
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
        return result
    }

    private fun movePlayersOut(world: World) {
        val fallback = plugin.lobbyLocation?.takeIf { it.world != null && it.world != world }
            ?: Bukkit.getWorlds().firstOrNull { it != world }?.spawnLocation
            ?: throw IllegalStateException("Cannot unload the only loaded world")
        world.players.toList().forEach { it.teleport(fallback) }
    }
}
