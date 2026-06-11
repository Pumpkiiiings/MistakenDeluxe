package liric.mistaken.level.api

import liric.mistaken.api.level.LevelProvider
import liric.mistaken.level.LevelAddonPlugin
import java.util.UUID

class LevelProviderImpl(private val plugin: LevelAddonPlugin) : LevelProvider {

    override fun getLevel(uuid: UUID): Int {
        return plugin.manager.getLevel(uuid)
    }

    override fun setLevel(uuid: UUID, level: Int) {
        plugin.manager.setLevel(uuid, level)
    }

    override fun addLevel(uuid: UUID, amount: Int) {
        val current = plugin.manager.getLevel(uuid)
        plugin.manager.setLevel(uuid, current + amount)
    }
}
