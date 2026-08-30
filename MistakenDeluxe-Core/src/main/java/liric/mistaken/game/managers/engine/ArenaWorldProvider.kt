package liric.mistaken.game.managers.engine

import java.util.concurrent.CompletableFuture

internal interface ArenaWorldProvider {
    val backend: ArenaWorldBackend

    fun hasTemplate(templateName: String): Boolean

    fun create(templateName: String, metadata: Map<String, String>): CompletableFuture<ManagedArenaWorld>
}
