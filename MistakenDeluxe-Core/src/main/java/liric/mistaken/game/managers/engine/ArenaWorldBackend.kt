package liric.mistaken.game.managers.engine

enum class ArenaWorldBackend {
    SLIME,
    ARENA_API;

    companion object {
        fun parse(value: String?): ArenaWorldBackend = entries.firstOrNull {
            it.name.equals(value?.replace('-', '_'), ignoreCase = true)
        } ?: ARENA_API
    }
}
