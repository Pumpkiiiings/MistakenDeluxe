package liric.mistaken.game.modes

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.data.PlayerDataManager.MistakenUser
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent

abstract class ModeHandler(val plugin: Mistaken, val session: GameSession) {
    /**
     * Devuelve el máximo de estamina para este modo (ej. 500.0 para ONE_BOUNCE)
     */
    open fun getMaxStamina(player: Player): Double = 100.0

    /**
     * Calcula cuántos asesinos debe haber dado el número de jugadores online.
     */
    open fun calculateKillersCount(onlineCount: Int): Int = 1

    /**
     * Evento al iniciar el juego (INGAME).
     */
    open fun onGameStart() {}

    /**
     * Evento al spawnear un jugador. Útil para buffos iniciales.
     */
    open fun onPlayerSpawn(player: Player, isKiller: Boolean) {}

    /**
     * Modificador de daño al ser atacado por otro jugador.
     * Si retorna null o cancela el evento, el daño es ignorado (o puedes aplicar lógicas custom).
     */
    open fun onPlayerHit(attacker: Player, victim: Player, event: EntityDamageByEntityEvent) {}

    /**
     * Evento ejecutado cada tick de estamina (ej. para congelar el consumo).
     * @return El nuevo valor de estamina, por defecto el mismo si no se altera.
     */
    open fun onStaminaTick(user: MistakenUser, currentStamina: Double): Double = currentStamina

    /**
     * Evento al finalizar el juego.
     */
    open fun onGameEnd(killerWon: Boolean) {}

    /**
     * Lógica cuando un jugador muere dentro del controlador (GamePlayerController).
     * @return true si el modo maneja la muerte y cancela la lógica por defecto (espectador).
     */
    open fun onPlayerDeath(player: Player): Boolean = false

    /**
     * Hook para el evento de Bukkit PlayerDeathEvent.
     */
    open fun onPlayerDeathEvent(event: PlayerDeathEvent) {}

    /**
     * Hook para el evento de Bukkit PlayerRespawnEvent.
     */
    open fun onPlayerRespawnEvent(event: PlayerRespawnEvent) {}
}
