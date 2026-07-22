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
                                memberPlayer.sendMessage("§aTu party te ha metido a la partida.")
                                
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
                                memberPlayer.sendMessage("§cTu party ha salido de la partida, sacando a todos...")
                                
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
                            player.sendMessage("§cNo hay mapas configurados para iniciar la partida.")
                            return
                        }
                        
                        val session = plugin.sessionManager.createSession(arenas.values.first().name, true)
                        
                        party.members.forEach { member ->
                            val memberPlayer = Bukkit.getPlayer(member.uniqueId)
                            if (memberPlayer != null && memberPlayer.isOnline) {
                                recentlyPulled.add(member.uniqueId)
                                
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    MistakenProvider.get().sessionManager.joinSession(memberPlayer, session.id)
                                    memberPlayer.sendMessage("§aTu party te ha metido a la partida privada.")
                                    
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
                        
                        player.sendMessage("§e¡Has creado una partida privada! Usa el panel para configurarla.")
                        
                    } else {
                        player.sendMessage("§cSolo el líder de la party puede crear partidas privadas.")
                    }
                } else {
                    player.sendMessage("§cNo estás en una party.")
                }
            } catch (e: Exception) {
                 plugin.logger.warning("Error al procesar /party private: \${e.message}")
            }
        }
    }
}
