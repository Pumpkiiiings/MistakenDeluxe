package liric.mistaken.game.managers.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArenaWorldBackendTest {
    @Test
    fun `parses every supported configuration spelling`() {
        assertEquals(ArenaWorldBackend.SLIME, ArenaWorldBackend.parse("SLIME"))
        assertEquals(ArenaWorldBackend.ARENA_API, ArenaWorldBackend.parse("arena_api"))
        assertEquals(ArenaWorldBackend.ARENA_API, ArenaWorldBackend.parse("arena-api"))
    }

    @Test
    fun `unknown or missing backend uses ArenaAPI`() {
        assertEquals(ArenaWorldBackend.ARENA_API, ArenaWorldBackend.parse(null))
        assertEquals(ArenaWorldBackend.ARENA_API, ArenaWorldBackend.parse("unknown"))
    }
}
