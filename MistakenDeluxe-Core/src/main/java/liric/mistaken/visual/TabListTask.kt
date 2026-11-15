package liric.mistaken.visual

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.luckperms.api.LuckPermsProvider
import org.bukkit.Bukkit
import java.util.function.Consumer

class TabListTask(private val plugin: Mistaken) : Runnable {

    private val miniMessage = MiniMessage.miniMessage()
    private var scheduledTask: ScheduledTask? = null

    fun isTabEnabled(): Boolean {
        val cfg = plugin.config
        return cfg.getBoolean("visuals.tab.enabled",
            cfg.getBoolean("visual.tab.enabled",
                cfg.getBoolean("visuals.tablist.enabled",
                    cfg.getBoolean("visual.tablist.enabled",
                        cfg.getBoolean("tablist.enabled", true)))))
    }

    fun start() {
        stop()
        if (!isTabEnabled()) return

        val cfg = plugin.config
        val interval = cfg.getLong("visuals.tab.update-interval",
            cfg.getLong("visual.tab.update-interval",
                cfg.getLong("visuals.tablist.update-interval",
                    cfg.getLong("tablist.update-interval", 20L)))).coerceAtLeast(1L)

        scheduledTask = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { _ ->
            run()
        }, 1L, interval)
    }

    fun stop() {
        scheduledTask?.cancel()
        scheduledTask = null
    }

    override fun run() {
        if (!isTabEnabled() || !plugin.isReady) return

        val config = plugin.config
        val isMiniPlaceholdersEnabled = Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")
        val isLuckPermsEnabled = Bukkit.getPluginManager().isPluginEnabled("LuckPerms")
        val globalPlaceholders = if (isMiniPlaceholdersEnabled) MiniPlaceholdersHook.getGlobalPlaceholders() else TagResolver.empty()

        val sortByWeight = config.getBoolean("visuals.tab.sort-by-rank-weight",
            config.getBoolean("visual.tab.sort-by-rank-weight",
                config.getBoolean("visuals.tablist.sort-by-rank-weight",
                    config.getBoolean("tablist.sort-by-rank-weight", true)))) && isLuckPermsEnabled

        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard

        for (player in Bukkit.getOnlinePlayers()) {
            if (!player.isOnline) continue

            val session = plugin.sessionManager.getSession(player)
            val stateName = session?.currentState?.name ?: "LOBBY"

            // State section resolution with multiple fallbacks
            val stateSection = when {
                config.contains("visuals.tab.states.$stateName") -> "visuals.tab.states.$stateName"
                config.contains("visual.tab.states.$stateName") -> "visual.tab.states.$stateName"
                config.contains("visuals.tablist.states.$stateName") -> "visuals.tablist.states.$stateName"
                config.contains("tablist.states.$stateName") -> "tablist.states.$stateName"
                config.contains("visuals.tab.states.DEFAULT") -> "visuals.tab.states.DEFAULT"
                config.contains("visual.tab.states.DEFAULT") -> "visual.tab.states.DEFAULT"
                config.contains("visuals.tablist.states.DEFAULT") -> "visuals.tablist.states.DEFAULT"
                else -> "tablist.states.DEFAULT"
            }

            // Audience Placeholders
            val audiencePlaceholders = if (isMiniPlaceholdersEnabled) {
                MiniPlaceholdersHook.getAudiencePlaceholders(player)
            } else {
                TagResolver.empty()
            }

            // Player Name in Tab
            val nameFormat = config.getString("$stateSection.player-name")
            if (!nameFormat.isNullOrEmpty()) {
                val parsedName = VisualFormatUtil.parseFormat(player, nameFormat, isChat = false, plugin = plugin)
                val nameTags = TagResolver.resolver(
                    Placeholder.component("player", player.displayName()),
                    audiencePlaceholders,
                    globalPlaceholders
                )
                player.playerListName(miniMessage.deserialize(parsedName, nameTags))
            }

            // Header and Footer
            val headerList = config.getStringList("$stateSection.header")
            val footerList = config.getStringList("$stateSection.footer")

            if (headerList.isNotEmpty() || footerList.isNotEmpty()) {
                val headerFormat = headerList.joinToString("\n")
                val footerFormat = footerList.joinToString("\n")

                val parsedHeader = VisualFormatUtil.parseFormat(player, headerFormat, isChat = false, plugin = plugin)
                val parsedFooter = VisualFormatUtil.parseFormat(player, footerFormat, isChat = false, plugin = plugin)

                val tags = TagResolver.resolver(audiencePlaceholders, globalPlaceholders)
                
                player.sendPlayerListHeaderAndFooter(
                    miniMessage.deserialize(parsedHeader, tags),
                    miniMessage.deserialize(parsedFooter, tags)
                )
            }
            
            // Sort by Weight using Scoreboard Teams
            if (sortByWeight) {
                try {
                    val lpUser = LuckPermsProvider.get().userManager.getUser(player.uniqueId)
                    if (lpUser != null) {
                        val groupName = lpUser.primaryGroup
                        val group = LuckPermsProvider.get().groupManager.getGroup(groupName)
                        val weight = if (group != null && group.weight.isPresent) group.weight.asInt else 0
                        val sortOrder = String.format("%05d", 99999 - weight)
                        val teamName = "vw_$sortOrder"
                        
                        var team = scoreboard.getTeam(teamName)
                        if (team == null) {
                            team = scoreboard.registerNewTeam(teamName)
                        }
                        
                        if (!team.hasEntry(player.name)) {
                            team.addEntry(player.name)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore LuckPerms errors
                }
            }
        }
    }
}
