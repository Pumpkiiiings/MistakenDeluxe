package liric.mistaken.game.managers.engine

import dev.arenaapi.api.ArenaApi
import dev.arenaapi.api.ArenaProvider
import dev.arenaapi.api.ArenaRequest
import dev.arenaapi.api.ArenaTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

internal class ArenaApiWorldProvider : ArenaWorldProvider {
    override val backend = ArenaWorldBackend.ARENA_API
    private val api: ArenaApi = ArenaProvider.get()

    override fun hasTemplate(templateName: String): Boolean = api.template(templateName).isPresent

    fun ensureTemplate(templateName: String, schematic: Path): Boolean {
        if (hasTemplate(templateName)) return true
        val absolute = schematic.toAbsolutePath().normalize()
        if (!Files.isRegularFile(absolute)) return false
        api.registerTemplate(ArenaTemplate.builder(templateName, absolute).build())
        return true
    }

    override fun create(
        templateName: String,
        metadata: Map<String, String>
    ): CompletableFuture<ManagedArenaWorld> = api.create(ArenaRequest(templateName, metadata))
        .thenApply { instance ->
            val world = instance.world().orElseThrow {
                IllegalStateException("ArenaAPI instance ${instance.id()} is ready without a Bukkit world")
            }
            ManagedArenaWorld(world, backend) { instance.destroy().toCompletableFuture() }
        }
        .toCompletableFuture()
}
