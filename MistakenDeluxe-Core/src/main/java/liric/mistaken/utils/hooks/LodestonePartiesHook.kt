package liric.mistaken.utils.hooks

import liric.mistaken.Mistaken
import liric.lodestone.parties.api.models.Party
import liric.lodestone.parties.api.models.PartyMember
import liric.mistaken.api.MistakenProvider
import liric.mistaken.api.events.MistakenPlayerJoinSessionEvent
import liric.mistaken.api.events.MistakenPlayerLeaveSessionEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import java.util.UUID

class LodestonePartiesHook(private val plugin: Mistaken) : Listener {

    private val recentlyPulled = mutableSetOf<UUID>()

    @EventHandler
    fun onSessionJoin(event: MistakenPlayerJoinSessionEvent) {
        val player = event.player
        val sessionId = event.session.id

        if (recentlyPulled.contains(player.uniqueId)) return

        try {
            val lodestonePlugin = Bukkit.getPluginManager().getPlugin("LodestoneParties") ?: return
            val method = lodestonePlugin.javaClass.getMethod("getPartyService")
            val partyService = method.invoke(lodestonePlugin) as liric.lodestone.parties.api.services.PartyService

            val partyOpt = partyService.getPartyByPlayer(player.uniqueId)
            
            if (partyOpt.isPresent) {
                val party = partyOpt.get() as Party
                
                party.members.forEach { member ->
                    if (member.uniqueId != player.uniqueId) {
                        val memberPlayer = Bukkit.getPlayer(member.uniqueId)
                        if (memberPlayer != null && memberPlayer.isOnline) {
                            recentlyPulled.add(member.uniqueId)
                            
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                MistakenProvider.get().sessionManager.joinSession(memberPlayer, sessionId)
                                pumpking.lib.service.PumpkingServiceManager.messages.send(memberPlayer, liric.mistaken.config.Messages.HOOK_PARTY_ENTER)
                                
                                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                    recentlyPulled.remove(member.uniqueId)
                                }, 40L)
                            })
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error en LodestonePartiesHook: \${e.message}")
        }
    }

    @EventHandler
    fun onSessionLeave(event: MistakenPlayerLeaveSessionEvent) {
        val player = event.player

        if (recentlyPulled.contains(player.uniqueId)) return

        try {
            val lodestonePlugin = Bukkit.getPluginManager().getPlugin("LodestoneParties") ?: return
            val method = lodestonePlugin.javaClass.getMethod("getPartyService")
            val partyService = method.invoke(lodestonePlugin) as liric.lodestone.parties.api.services.PartyService

            val partyOpt = partyService.getPartyByPlayer(player.uniqueId)

            if (partyOpt.isPresent) {
                val party = partyOpt.get() as Party
                
                party.members.forEach { member ->
                    if (member.uniqueId != player.uniqueId) {
                        val memberPlayer = Bukkit.getPlayer(member.uniqueId)
                        if (memberPlayer != null && memberPlayer.isOnline) {
                            recentlyPulled.add(member.uniqueId)
                            
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                MistakenProvider.get().sessionManager.leaveSession(memberPlayer)
                                pumpking.lib.service.PumpkingServiceManager.messages.send(memberPlayer, liric.mistaken.config.Messages.HOOK_PARTY_LEAVE)
                                
                                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                    recentlyPulled.remove(member.uniqueId)
                                }, 40L)
                            })
                        }
                    }
                }
            }
        } catch (e: Exception) {
             plugin.logger.warning("Error en LodestonePartiesHook: \${e.message}")
        }
    }

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val message = event.message.lowercase()
        
        if (message == "/party private" || message == "/p private") {
            event.isCancelled = true
            
            try {
                val lodestonePlugin = Bukkit.getPluginManager().getPlugin("LodestoneParties") ?: return
                val method = lodestonePlugin.javaClass.getMethod("getPartyService")
                val partyService = method.invoke(lodestonePlugin) as liric.lodestone.parties.api.services.PartyService
    
                val partyOpt = partyService.getPartyByPlayer(player.uniqueId)
                if (partyOpt.isPresent) {
                    val party = partyOpt.get() as Party
                    
                    if (party.leader.uniqueId == player.uniqueId) {
                        // Create Private Session
                        val arenas = plugin.arenaManager.getArenas()
                        if (arenas.isEmpty()) {
                            pumpking.lib.service.PumpkingServiceManager.messages.send(player, liric.mistaken.config.Messages.HOOK_PARTY_NO_MAPS)
                            return
                        }
                        
                        val session = plugin.sessionManager.createSession(arenas.values.first().name, true)
                        
                        party.members.forEach { member ->
                            val memberPlayer = Bukkit.getPlayer(member.uniqueId)
                            if (memberPlayer != null && memberPlayer.isOnline) {
                                recentlyPulled.add(member.uniqueId)
                                
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    MistakenProvider.get().sessionManager.joinSession(memberPlayer, session.id)
                                    pumpking.lib.service.PumpkingServiceManager.messages.send(memberPlayer, liric.mistaken.config.Messages.HOOK_PARTY_ENTER_PRIVATE)
                                    
                                    if (member.uniqueId == player.uniqueId) {
                                        val panelItem = org.bukkit.inventory.ItemStack(org.bukkit.Material.COMMAND_BLOCK)
                                        val meta = panelItem.itemMeta
                                        meta.setDisplayName("§6§lPanel de Control")
                                        panelItem.itemMeta = meta
                                        memberPlayer.inventory.setItem(4, panelItem)
                                    }
                                    
                                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                        recentlyPulled.remove(member.uniqueId)
                                    }, 40L)
                                })
                            }
                        }
                        
                        pumpking.lib.service.PumpkingServiceManager.messages.send(player, liric.mistaken.config.Messages.HOOK_PARTY_CREATED_PRIVATE)
                        
                    } else {
                        pumpking.lib.service.PumpkingServiceManager.messages.send(player, liric.mistaken.config.Messages.HOOK_PARTY_ONLY_LEADER)
                    }
                } else {
                    pumpking.lib.service.PumpkingServiceManager.messages.send(player, liric.mistaken.config.Messages.HOOK_PARTY_NOT_IN)
                }
            } catch (e: Exception) {
                 plugin.logger.warning("Error al procesar /party private: \${e.message}")
            }
        }
    }
}
