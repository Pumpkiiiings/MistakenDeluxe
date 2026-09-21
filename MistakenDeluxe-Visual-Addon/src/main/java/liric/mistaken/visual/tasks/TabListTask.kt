package liric.mistaken.visual.tasks

import liric.mistaken.api.MistakenProvider
import liric.mistaken.visual.VisualAddon
import liric.mistaken.visual.utils.MiniPlaceholdersHook
import liric.mistaken.visual.utils.VisualFormatUtil
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import net.luckperms.api.LuckPermsProvider
import org.bukkit.scoreboard.Team

class TabListTask : Runnable {
    override fun run() {
        if (!MistakenProvider.isRegistered()) return

        val config = VisualAddon.instance.config
        val api = MistakenProvider.get()
        
        val isMiniPlaceholdersEnabled = Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")
        val isLuckPermsEnabled = Bukkit.getPluginManager().isPluginEnabled("LuckPerms")
        val globalPlaceholders = if (isMiniPlaceholdersEnabled) MiniPlaceholdersHook.getGlobalPlaceholders() else TagResolver.empty()

        val sortByWeight = config.getBoolean("tablist.sort-by-rank-weight", true) && isLuckPermsEnabled
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard

        for (player in Bukkit.getOnlinePlayers()) {
            val session = api.sessionManager.getSession(player)
            val stateName = session?.currentState?.name ?: "LOBBY"

            // Ensure state section exists, default to DEFAULT if not
            val stateSection = if (config.contains("tablist.states.$stateName")) {
                "tablist.states.$stateName"
            } else {
                "tablist.states.DEFAULT"
            }

            // Audience Placeholders
            val audiencePlaceholders = if (isMiniPlaceholdersEnabled) {
                MiniPlaceholdersHook.getAudiencePlaceholders(player)
            } else {
                TagResolver.empty()
            }

            // Player Name
            val nameFormat = config.getString("$stateSection.player-name")
            if (!nameFormat.isNullOrEmpty()) {
                val parsedName = VisualFormatUtil.parseFormat(player, nameFormat, isChat = false)
                val nameTags = TagResolver.resolver(
                    Placeholder.component("player", player.displayName()),
                    audiencePlaceholders,
                    globalPlaceholders
                )
                player.playerListName(MiniMessage.miniMessage().deserialize(parsedName, nameTags))
            }

            // Header and Footer
            val headerList = config.getStringList("$stateSection.header")
            val footerList = config.getStringList("$stateSection.footer")

            if (headerList.isNotEmpty() || footerList.isNotEmpty()) {
                val headerFormat = headerList.joinToString("\n")
                val footerFormat = footerList.joinToString("\n")

                val parsedHeader = VisualFormatUtil.parseFormat(player, headerFormat, isChat = false)
                val parsedFooter = VisualFormatUtil.parseFormat(player, footerFormat, isChat = false)

                val tags = TagResolver.resolver(audiencePlaceholders, globalPlaceholders)
                
                player.sendPlayerListHeaderAndFooter(
                    MiniMessage.miniMessage().deserialize(parsedHeader, tags),
                    MiniMessage.miniMessage().deserialize(parsedFooter, tags)
                )
            }
            
            // Sort by Weight using Scoreboard Teams
            if (sortByWeight) {
                try {
                    val lpUser = LuckPermsProvider.get().userManager.getUser(player.uniqueId)
                    if (lpUser != null) {
                        val groupName = lpUser.primaryGroup
                        val group = LuckPermsProvider.get().groupManager.getGroup(groupName)
                        
                        // group.weight is OptionalInt in Java, so we check if present
                        val weight = if (group != null && group.weight.isPresent) group.weight.asInt else 0
                        
                        // We format the team name so highest weight = lowest alphabetically (e.g. 00010 vs 00090)
                        // A simple way is to subtract weight from 99999 so weight 100 becomes 99899, weight 0 becomes 99999.
                        val sortOrder = String.format("%05d", 99999 - weight)
                        val teamName = "vw_$sortOrder" // vw_ = visual weight
                        
                        var team = scoreboard.getTeam(teamName)
                        if (team == null) {
                            team = scoreboard.registerNewTeam(teamName)
                        }
                        
                        if (!team.hasEntry(player.name)) {
                            team.addEntry(player.name)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore LuckPerms errors
                }
            }
        }
    }
}
