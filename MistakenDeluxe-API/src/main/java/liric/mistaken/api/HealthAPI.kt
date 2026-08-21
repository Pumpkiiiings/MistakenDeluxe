package liric.mistaken.api

import org.bukkit.entity.Player


interface HealthAPI {

    /**
     * Obtiene la cantidad de vidas actuales del jugador.
     */
    fun getHealth(player: Player): Int

    /**
     * Establece manualmente la cantidad de vidas de un jugador.
     * @param health Cantidad de vidas (usualmente entre 0 y 6).
     */
    fun setHealth(player: Player, health: Int)

    /**
     * Aplica la lógica de daño personalizado (resta una vida, aplica sangre, sonidos, etc.).
     */
    fun takeDamage(victim: Player, amount: Double = 3.0, sourceName: String? = null)

    /**
     * Verifica si el jugador se encuentra congelado (Modo Freeze Tag).
     */
    fun isFrozen(player: Player): Boolean

    /**
     * Ejecuta el proceso de rescate para un jugador congelado.
     * @param victim Jugador a ser descongelado.
     * @param rescuer Jugador que realiza la acción de rescate.
     */
    fun unfreeze(victim: Player, rescuer: Player)

    /**
     * Resetea completamente el estado del jugador (Vida máxima y limpieza de estados).
     */
    fun resetPlayer(player: Player)
}
