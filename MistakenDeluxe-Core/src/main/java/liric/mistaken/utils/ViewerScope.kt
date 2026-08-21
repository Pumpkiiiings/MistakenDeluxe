package liric.mistaken.utils

import liric.mistaken.Mistaken
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * Alcance de espectadores para efectos client-side.
 *
 * Los displays virtuales se envían por paquete, así que ignoran por completo el
 * IsolationManager: si el alcance es `Bukkit.getOnlinePlayers()`, el efecto se cuela
 * en las demás arenas. Estos helpers dan el alcance correcto en cada caso.
 */

/** Players de la sesión del player. Si no está en ninguna, solo él. */
fun Player.sessionViewers(): List<Player> {
    val plugin = JavaPlugin.getPlugin(Mistaken::class.java)
    return plugin.sessionManager.getSession(this)?.getPlayers() ?: listOf(this)
}

/**
 * Players del world de la ubicación. En multiarena cada arena tiene su propio
 * world, así que esto aísla por partida sin necesitar una sesión en contexto
 * (hologramas de generador, cinemáticas).
 */
fun Location.worldViewers(): List<Player> = world?.players ?: emptyList()
