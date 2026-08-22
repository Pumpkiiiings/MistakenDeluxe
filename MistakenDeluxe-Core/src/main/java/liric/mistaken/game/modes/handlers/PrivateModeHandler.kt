package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.PrivateGameSettings
import liric.mistaken.game.modes.ModeHandler
import liric.mistaken.data.PlayerDataManager.MistakenUser
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent

/**
 * Wrapper que envuelve al handler del modo real y aplica overrides de juegos privados.
 * Composición: delega todo al innerHandler salvo lo que el host customizó via PrivateGameSettings.
 */
class PrivateModeHandler(
    plugin: Mistaken,
    session: GameSession,
    private val innerHandler: ModeHandler,
    private val settings: PrivateGameSettings
) : ModeHandler(plugin, session) {

    // === Propiedades: delegar al inner salvo overrides del host ===

    override val enableHeartbeat: Boolean
        get() = settings.heartbeatsEnabled && innerHandler.enableHeartbeat

    override val enableCriticalSwimming: Boolean
        get() = innerHandler.enableCriticalSwimming

    override val enableLastManStanding: Boolean
        get() = innerHandler.enableLastManStanding

    override val enableRescueInteraction: Boolean
        get() = innerHandler.enableRescueInteraction

    // === Funciones de valor ===

    override fun getKillerBaseDamage(): Double = innerHandler.getKillerBaseDamage()

    override fun getMaxStamina(player: Player): Double = innerHandler.getMaxStamina(player)

    override fun calculateKillersCount(onlineCount: Int): Int = innerHandler.calculateKillersCount(onlineCount)

    // === Hooks de evento: delegar al inner ===

    override fun onLethalHit(victim: Player): Boolean = innerHandler.onLethalHit(victim)

    override fun checkSpecialSpawn(timer: Int): Boolean = innerHandler.checkSpecialSpawn(timer)

    override fun onGameStart() = innerHandler.onGameStart()

    override fun onPlayerSpawn(player: Player, isKiller: Boolean) = innerHandler.onPlayerSpawn(player, isKiller)

    override fun onPlayerHit(attacker: Player, victim: Player, event: EntityDamageByEntityEvent) =
        innerHandler.onPlayerHit(attacker, victim, event)

    override fun onStaminaTick(user: MistakenUser, currentStamina: Double): Double =
        innerHandler.onStaminaTick(user, currentStamina)

    override fun onGameEnd(killerWon: Boolean) = innerHandler.onGameEnd(killerWon)

    override fun onPlayerDeath(player: Player): Boolean = innerHandler.onPlayerDeath(player)

    override fun onPlayerDeathEvent(event: PlayerDeathEvent) = innerHandler.onPlayerDeathEvent(event)

    override fun onPlayerRespawnEvent(event: PlayerRespawnEvent) = innerHandler.onPlayerRespawnEvent(event)
}
