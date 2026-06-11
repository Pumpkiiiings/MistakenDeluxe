package liric.mistaken.level.api

import liric.mistaken.api.level.ExperienceProvider
import liric.mistaken.level.LevelAddonPlugin
import java.util.UUID

class ExperienceProviderImpl(private val plugin: LevelAddonPlugin) : ExperienceProvider {

    override fun getExperience(uuid: UUID): Long {
        return plugin.manager.getExperience(uuid)
    }

    override fun addExperience(uuid: UUID, amount: Long) {
        plugin.manager.addExperience(uuid, amount, liric.mistaken.api.level.event.PlayerExperienceGainEvent.GainReason.PLUGIN_API)
    }

    override fun setExperience(uuid: UUID, amount: Long) {
        plugin.manager.setExperience(uuid, amount)
    }

    override fun getTotalExperience(uuid: UUID): Long {
        return plugin.manager.getTotalExperience(uuid)
    }
}
