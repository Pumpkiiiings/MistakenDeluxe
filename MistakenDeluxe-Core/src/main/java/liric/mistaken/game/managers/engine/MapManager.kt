package liric.mistaken.game.managers.engine

import liric.mistaken.Mistaken
import liric.mistaken.game.Arena
import org.bukkit.Bukkit
import org.bukkit.World
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.utils.color.ColorTranslator


class MapManager(private val plugin: Mistaken) {

    private val backend: ArenaWorldBackend
    private val provider: ArenaWorldProvider?
    private val schematicsFolder: Path
    private val managedInstances = ConcurrentHashMap<UUID, ManagedArenaWorld>()

    init {
        backend = ArenaWorldBackend.parse(plugin.config.getString("settings.arena-worlds.backend", "arena_api"))
        schematicsFolder = configuredPath(
            plugin.config.getString("settings.arena-worlds.schematics-path"),
            "schematics"
        )
        provider = runCatching {
            when (backend) {
                ArenaWorldBackend.SLIME -> SlimeArenaWorldProvider(plugin)
                ArenaWorldBackend.ARENA_API -> {
                    check(plugin.server.pluginManager.isPluginEnabled("ArenaAPI")) { "ArenaAPI plugin is not enabled" }
                    ArenaApiWorldProvider()
                }
            }
        }.onFailure { error ->
            plugin.componentLogger.error(ColorTranslator.translate(
                "<red>[ERROR]</red> <gray>${backend.name} backend is unavailable: ${error.message}</gray>"
            ))
        }.getOrNull()
    }

    fun loadArenaWorld(arena: Arena, sessionId: String): CompletableFuture<World?> {
        val selectedProvider = provider
        if (selectedProvider == null) {
            plugin.componentLogger.error(ColorTranslator.translate(
                "<red>[ERROR]</red> <gray>${backend.name} backend is unavailable.</gray>"
            ))
            return CompletableFuture.completedFuture(null)
        }

        val templateName = arena.name
        val available = if (selectedProvider is ArenaApiWorldProvider) {
            selectedProvider.ensureTemplate(templateName, schematicsFolder.resolve("$templateName.schem"))
        } else {
            selectedProvider.hasTemplate(templateName)
        }
        if (!available) {
            val extension = if (backend == ArenaWorldBackend.ARENA_API) ".schem" else ".slime"
            plugin.componentLogger.error(ColorTranslator.translate(
                "<red>[ERROR]</red> <gray>Missing arena file: $templateName$extension</gray>"
            ))
            return CompletableFuture.completedFuture(null)
        }

        val metadata = mapOf("consumer" to "MistakenDeluxe", "session" to sessionId, "map" to arena.name)
        return selectedProvider.create(templateName, metadata)
            .thenApply<World?> { managed ->
                managedInstances[managed.world.uid] = managed
                plugin.componentLogger.info(ColorTranslator.translate(
                    "<green>[SUCCESS]</green> <gray>World ${managed.world.name} instantiated with ${backend.name}.</gray>"
                ))
                managed.world
            }
            .exceptionally { error ->
                plugin.componentLogger.error(ColorTranslator.translate(
                    "<red>[ERROR]</red> <gray>${backend.name} failed for '$templateName': ${error.cause?.message ?: error.message}</gray>"
                ))
                null
            }
    }

    /**
     * Compatibility overload for callers that only have the arena id.
     */
    fun loadArenaWorld(templateName: String): CompletableFuture<World?> {
        return loadArenaWorld(Arena(templateName), "unknown")
    }

    /**
     * Descarga un world sin guardar cambios de forma segura.
     */
    fun unloadWorld(world: World?) {
        if (world == null) return

        val managed = managedInstances.remove(world.uid)
        if (managed != null) {
            managed.release().whenComplete { _, error ->
                if (error != null) {
                    plugin.componentLogger.error(ColorTranslator.translate(
                        "<red>[ERROR]</red> <gray>Failed to destroy ArenaAPI world ${world.name}: ${error.message}</gray>"
                    ))
                }
            }
            return
        }

        
        plugin.server.globalRegionScheduler.execute(plugin) {
            Bukkit.unloadWorld(world, false)
            plugin.componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>World ${world.name} unloaded.</gray>"))
        }
    }

    fun shutdown() {
        managedInstances.values.toList().forEach { managed -> runCatching { managed.release() } }
        managedInstances.clear()
    }

    private fun configuredPath(value: String?, fallback: String): Path {
        val configured = if (value.isNullOrBlank()) Path.of(fallback) else Path.of(value)
        return if (configured.isAbsolute) configured.normalize()
        else plugin.dataFolder.toPath().resolve(configured).toAbsolutePath().normalize()
    }
}
