package liric.mistaken.utils.hooks

import com.alessiodp.parties.api.Parties
import liric.mistaken.Mistaken
import liric.mistaken.api.MistakenProvider
import liric.mistaken.api.events.MistakenPlayerJoinSessionEvent
import liric.mistaken.api.events.MistakenPlayerLeaveSessionEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.UUID

class AlessioPartiesHook(private val plugin: Mistaken) : Listener {

    private val recentlyPulled = mutableSetOf<UUID>()

    @EventHandler
    fun onSessionJoin(event: MistakenPlayerJoinSessionEvent) {
        val player = event.player
        val sessionId = event.session.id

        if (recentlyPulled.contains(player.uniqueId)) return

        try {
            val api = Parties.getApi()
            val partyPlayer = api.getPartyPlayer(player.uniqueId)
            
            if (partyPlayer != null && partyPlayer.isInParty) {
                val partyId = partyPlayer.partyId ?: return
                val party = api.getParty(partyId) ?: return
                
                party.members.forEach { memberUuid ->
                    if (memberUuid != player.uniqueId) {
                        val memberPlayer = Bukkit.getPlayer(memberUuid)
                        if (memberPlayer != null && memberPlayer.isOnline) {
                            recentlyPulled.add(memberUuid)
                            
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                MistakenProvider.get().sessionManager.joinSession(memberPlayer, sessionId)
                                pumpking.lib.service.PumpkingServiceManager.messages.send(memberPlayer, liric.mistaken.config.Messages.HOOK_ALESSIO_ENTER)
                                
                                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                    recentlyPulled.remove(memberUuid)
                                }, 40L)
                            })
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    @EventHandler
    fun onSessionLeave(event: MistakenPlayerLeaveSessionEvent) {
        val player = event.player

        if (recentlyPulled.contains(player.uniqueId)) return

        try {
            val api = Parties.getApi()
            val partyPlayer = api.getPartyPlayer(player.uniqueId)

            if (partyPlayer != null && partyPlayer.isInParty) {
                val partyId = partyPlayer.partyId ?: return
                val party = api.getParty(partyId) ?: return
                
                party.members.forEach { memberUuid ->
                    if (memberUuid != player.uniqueId) {
                        val memberPlayer = Bukkit.getPlayer(memberUuid)
                        if (memberPlayer != null && memberPlayer.isOnline) {
                            recentlyPulled.add(memberUuid)
                            
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                MistakenProvider.get().sessionManager.leaveSession(memberPlayer)
                                pumpking.lib.service.PumpkingServiceManager.messages.send(memberPlayer, liric.mistaken.config.Messages.HOOK_ALESSIO_LEAVE)
                                
                                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                    recentlyPulled.remove(memberUuid)
                                }, 40L)
                            })
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }
}
