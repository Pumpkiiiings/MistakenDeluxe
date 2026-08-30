package liric.mistaken.game.managers.engine

import org.bukkit.World
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

internal class ManagedArenaWorld(
    val world: World,
    val backend: ArenaWorldBackend,
    private val releaseAction: () -> CompletableFuture<Void>
) {
    private val released = AtomicBoolean(false)

    fun release(): CompletableFuture<Void> =
        if (released.compareAndSet(false, true)) releaseAction()
        else CompletableFuture.completedFuture(null)
}
