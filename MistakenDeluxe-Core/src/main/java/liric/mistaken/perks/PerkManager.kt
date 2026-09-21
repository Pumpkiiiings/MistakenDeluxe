package liric.mistaken.perks

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * PerkManager — manages in-session perk assignments.
 * Perks are purely per-game, stored only in memory.
 * Nothing is saved to the database.
 */
class PerkManager(private val plugin: Mistaken) {

    private val registry = mutableMapOf<String, Perk>()
    
    /** UUID -> active perk for THIS game session only */
    private val activePerks = ConcurrentHashMap<UUID, Perk>()

    fun registerPerk(perk: Perk) {
        registry[perk.id.lowercase()] = perk
    }

    fun getPerk(id: String): Perk? = registry[id.lowercase()]

    fun getAllPerks(): Collection<Perk> = registry.values

    /** Returns 3 unique random perks for the player to choose from */
    fun getDraftOptions(count: Int = 3): List<Perk> {
        return registry.values.shuffled().take(count)
    }

    /** Called when player picks their perk in the draft menu */
    fun assignPerk(uuid: UUID, perk: Perk) {
        activePerks[uuid] = perk
        plugin.server.getPlayer(uuid)?.let { perk.onEquip(it) }
    }

    /** Returns the active perk for a player this session */
    fun getActivePerk(uuid: UUID): Perk? = activePerks[uuid]
    fun getActivePerk(player: Player): Perk? = activePerks[player.uniqueId]

    /** Clears a single player's active perk (used by debug commands) */
    fun clearPerkForPlayer(uuid: UUID) {
        val perk = activePerks.remove(uuid)
        plugin.server.getPlayer(uuid)?.let { perk?.onUnequip(it) }
    }

    /** Called at the end of a game session to clean up all perks */
    fun clearSession(session: GameSession) {
        session.players.forEach { uuid ->
            val perk = activePerks.remove(uuid)
            session.plugin.server.getPlayer(uuid)?.let { perk?.onUnequip(it) }
        }
    }

    // --- Trigger helpers (called by game hooks) ---

    fun triggerGeneratorRepair(player: Player) {
        getActivePerk(player)?.onGeneratorRepair(player)
    }

    fun triggerMove(player: Player) {
        getActivePerk(player)?.onMove(player)
    }

    fun triggerDamaged(player: Player) {
        getActivePerk(player)?.onDamaged(player)
    }

    fun triggerLookedAtByKiller(player: Player) {
        getActivePerk(player)?.onLookedAtByKiller(player)
    }

    init {
        // Periodic Premonition check: runs every second
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            plugin.server.onlinePlayers.forEach { survivor ->
                val perk = getActivePerk(survivor) ?: return@forEach
                if (perk.id != "premonition") return@forEach
                val session = plugin.sessionManager.getSession(survivor) as? liric.mistaken.game.GameSession ?: return@forEach
                if (session.isKiller(survivor.uniqueId)) return@forEach

                session.killersUUIDs.forEach { killerUUID ->
                    val killer = plugin.server.getPlayer(killerUUID) ?: return@forEach
                    val killerEyeLoc = killer.eyeLocation
                    val toSurvivor = survivor.eyeLocation.toVector().subtract(killerEyeLoc.toVector()).normalize()
                    val killerDir = killerEyeLoc.direction.normalize()
                    if (toSurvivor.dot(killerDir) > 0.85) {
                        val dist = killerEyeLoc.distance(survivor.eyeLocation)
                        if (dist < 40.0 && killer.hasLineOfSight(survivor)) {
                            triggerLookedAtByKiller(survivor)
                        }
                    }
                }
            }
        }, 20L, 20L)
    }
}
