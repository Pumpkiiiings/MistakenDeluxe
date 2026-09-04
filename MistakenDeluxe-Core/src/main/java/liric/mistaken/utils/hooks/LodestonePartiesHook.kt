package liric.mistaken.utils.hooks

import liric.mistaken.Mistaken
import liric.mistaken.api.MistakenProvider
import liric.mistaken.api.events.MistakenPlayerJoinSessionEvent
import liric.mistaken.api.events.MistakenPlayerLeaveSessionEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import java.util.UUID
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class LodestonePartiesHook(private val plugin: Mistaken) : Listener {

    private val recentlyPulled = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler
    fun onSessionJoin(event: MistakenPlayerJoinSessionEvent) {
        val player = event.player
        val sessionId = event.session.id

        if (recentlyPulled.contains(player.uniqueId)) return

        try {
            val party = findParty(player.uniqueId) ?: return

            party.memberIds.forEach { memberId ->
                    if (memberId != player.uniqueId) {
                        val memberPlayer = Bukkit.getPlayer(memberId)
                        if (memberPlayer != null && memberPlayer.isOnline) {
                            recentlyPulled.add(memberId)
                            
                            Bukkit.getGlobalRegionScheduler().run(plugin, {
                                MistakenProvider.get().sessionManager.joinSession(memberPlayer, sessionId)
                                liric.mistaken.config.engine.core.MessageService.send(memberPlayer, liric.mistaken.config.Messages.HOOK_PARTY_ENTER)
                                
                                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, {
                                    recentlyPulled.remove(memberId)
                                }, 40L)
                            })
                        }
                    }
                }
        } catch (e: Exception) {
            plugin.logger.warning("Error en LodestonePartiesHook: ${e.message}")
        }
    }

    @EventHandler
    fun onSessionLeave(event: MistakenPlayerLeaveSessionEvent) {
        val player = event.player

        if (recentlyPulled.contains(player.uniqueId)) return

        try {
            val party = findParty(player.uniqueId) ?: return

            party.memberIds.forEach { memberId ->
                    if (memberId != player.uniqueId) {
                        val memberPlayer = Bukkit.getPlayer(memberId)
                        if (memberPlayer != null && memberPlayer.isOnline) {
                            recentlyPulled.add(memberId)
                            
                            Bukkit.getGlobalRegionScheduler().run(plugin, {
                                MistakenProvider.get().sessionManager.leaveSession(memberPlayer)
                                liric.mistaken.config.engine.core.MessageService.send(memberPlayer, liric.mistaken.config.Messages.HOOK_PARTY_LEAVE)
                                
                                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, {
                                    recentlyPulled.remove(memberId)
                                }, 40L)
                            })
                        }
                    }
                }
        } catch (e: Exception) {
             plugin.logger.warning("Error en LodestonePartiesHook: ${e.message}")
        }
    }

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val message = event.message.lowercase()
        
        if (message == "/party private" || message == "/p private") {
            event.isCancelled = true
            
            try {
                val party = findParty(player.uniqueId)
                if (party != null) {
                    if (party.leaderId == player.uniqueId) {
                        
                        val arenas = plugin.arenaManager.getArenas()
                        if (arenas.isEmpty()) {
                            liric.mistaken.config.engine.core.MessageService.send(player, liric.mistaken.config.Messages.HOOK_PARTY_NO_MAPS)
                            return
                        }
                        
                        val session = plugin.sessionManager.createSession(arenas.first().name, true)
                        
                        party.memberIds.forEach { memberId ->
                            val memberPlayer = Bukkit.getPlayer(memberId)
                            if (memberPlayer != null && memberPlayer.isOnline) {
                                recentlyPulled.add(memberId)
                                
                                Bukkit.getGlobalRegionScheduler().run(plugin, {
                                    MistakenProvider.get().sessionManager.joinSession(memberPlayer, session.id)
                                    liric.mistaken.config.engine.core.MessageService.send(memberPlayer, liric.mistaken.config.Messages.HOOK_PARTY_ENTER_PRIVATE)
                                    
                                    if (memberId == player.uniqueId) {
                                        val panelItem = org.bukkit.inventory.ItemStack(org.bukkit.Material.COMMAND_BLOCK)
                                        val meta = panelItem.itemMeta
                                        meta.setDisplayName("§6§lPanel de Control")
                                        panelItem.itemMeta = meta
                                        memberPlayer.inventory.setItem(4, panelItem)
                                    }
                                    
                                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, {
                                        recentlyPulled.remove(memberId)
                                    }, 40L)
                                })
                            }
                        }
                        
                        liric.mistaken.config.engine.core.MessageService.send(player, liric.mistaken.config.Messages.HOOK_PARTY_CREATED_PRIVATE)
                        
                    } else {
                        liric.mistaken.config.engine.core.MessageService.send(player, liric.mistaken.config.Messages.HOOK_PARTY_ONLY_LEADER)
                    }
                } else {
                    liric.mistaken.config.engine.core.MessageService.send(player, liric.mistaken.config.Messages.HOOK_PARTY_NOT_IN)
                }
            } catch (e: Exception) {
                 plugin.logger.warning("Error al procesar /party private: ${e.message}")
            }
        }
    }

    private fun findParty(playerId: UUID): PartySnapshot? {
        val lodestonePlugin = Bukkit.getPluginManager().getPlugin("LodestoneParties") ?: return null
        val partyService = lodestonePlugin.javaClass.getMethod("getPartyService").invoke(lodestonePlugin)
        val result = partyService.javaClass
            .getMethod("getPartyByPlayer", UUID::class.java)
            .invoke(partyService, playerId) as? Optional<*> ?: return null
        val party = result.orElse(null) ?: return null
        val leader = party.javaClass.getMethod("getLeader").invoke(party)
        val leaderId = leader.javaClass.getMethod("getUniqueId").invoke(leader) as UUID
        val members = party.javaClass.getMethod("getMembers").invoke(party) as? Collection<*> ?: return null
        val memberIds = members.mapNotNull { member ->
            member?.javaClass?.getMethod("getUniqueId")?.invoke(member) as? UUID
        }
        return PartySnapshot(leaderId, memberIds)
    }

    private data class PartySnapshot(val leaderId: UUID, val memberIds: List<UUID>)
}
