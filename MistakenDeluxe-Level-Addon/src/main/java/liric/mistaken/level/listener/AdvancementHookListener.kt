package liric.mistaken.level.listener

import liric.mistaken.api.level.event.PlayerLevelUpEvent
import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.Bukkit

class AdvancementHookListener(private val plugin: LevelAddonPlugin) : Listener {

    @EventHandler
    fun onLevelUp(event: PlayerLevelUpEvent) {
        val milestone = when (event.newLevel) {
            10 -> "level_10"
            25 -> "level_25"
            50 -> "level_50"
            75 -> "level_75"
            100 -> "level_100"
            else -> null
        }

        if (milestone != null) {
            try {
                // Soft dependency via Reflection to avoid build failures with nexus.frengor.com
                val uapiPlugin = Bukkit.getPluginManager().getPlugin("UltimateAdvancementAPI")
                if (uapiPlugin != null && uapiPlugin.isEnabled) {
                    val uapiClass = Class.forName("com.frengor.ultimateadvancementapi.UltimateAdvancementAPI")
                    val getInstanceMethod = uapiClass.getMethod("getInstance", org.bukkit.plugin.Plugin::class.java)
                    val apiInstance = getInstanceMethod.invoke(null, plugin)

                    if (apiInstance != null) {
                        val getTabMethod = uapiClass.getMethod("getAdvancementTab", String::class.java)
                        val tab = getTabMethod.invoke(apiInstance, "mistaken_levels")

                        if (tab != null) {
                            val getAdvancementMethod = tab.javaClass.getMethod("getAdvancement", String::class.java)
                            val advancement = getAdvancementMethod.invoke(tab, milestone)

                            if (advancement != null) {
                                val hasAdvMethod = uapiClass.getMethod("hasAdvancement", org.bukkit.entity.Player::class.java, Class.forName("com.frengor.ultimateadvancementapi.advancement.Advancement"))
                                val hasAdv = hasAdvMethod.invoke(apiInstance, event.player, advancement) as Boolean

                                if (!hasAdv) {
                                    val grantMethod = uapiClass.getMethod("grantAdvancement", org.bukkit.entity.Player::class.java, Class.forName("com.frengor.ultimateadvancementapi.advancement.Advancement"))
                                    grantMethod.invoke(apiInstance, event.player, advancement)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to grant advancement $milestone: ${e.message}")
            }
        }
    }
}
