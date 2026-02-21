package liric.mistaken.utils

import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * [LIRIC-MISTAKEN 2.0]
 * RespawnUtils: Gestión de reaparición instantánea.
 * Optimizado para Paper 1.21.4.
 */
object RespawnUtils {

    /**
     * Activa el respawn instantáneo nativo de Minecraft en un mundo.
     * Esta es la forma más optimizada (0.00% CPU) porque lo maneja el motor del juego.
     */
    fun enableImmediateRespawn(world: World) {
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
    }

    /**
     * Fuerza el respawn manual de un jugador.
     * Útil como fallback si falla la GameRule.
     */
    fun forceRespawn(player: Player) {
        if (player.isDead) {
            player.spigot().respawn()
        }
    }
}
