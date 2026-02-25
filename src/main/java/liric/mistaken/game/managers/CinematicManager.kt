package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.*

/**
 * [LIRIC-MISTAKEN 2.0]
 * CinematicManager: Motor de presentaciones con BossBar dinámica.
 */
class CinematicManager(private val plugin: Mistaken) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Lanza la presentación del asesino.
     */
    fun playKillerIntro(asesino: Asesino, killerPlayer: Player, viewers: Collection<Player>, spawnLoc: Location) {
        scope.launch {
            // 1. CREAR LA BOSSBAR (Configurable en el YAML de mensajes)
            // Ruta: cinematic.intro-bar: "El asesino <assassin> estará en esta ronda. Jugador <player>"
            val barComponent = plugin.messageConfig.getMessage(
                null,
                "cinematic.intro-bar",
                Placeholder.parsed("assassin", asesino.nombre),
                Placeholder.parsed("player", killerPlayer.name)
            )

            val bossBar = BossBar.bossBar(
                barComponent,
                1.0f, // Llena al 100%
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
            )

            // 2. PREPARACIÓN (Main Thread)
            withContext(plugin.bukkitDispatcher) {
                viewers.forEach { p ->
                    p.showBossBar(bossBar)
                    p.gameMode = GameMode.SPECTATOR

                    // Cámara: 6 bloques frente al asesino, mirando hacia él
                    val camLoc = spawnLoc.clone().add(spawnLoc.direction.multiply(-6)).add(0.0, 2.5, 0.0)
                    camLoc.setDirection(spawnLoc.toVector().subtract(camLoc.toVector()))
                    p.teleport(camLoc)
                }
            }

            // 3. EFECTOS SEGÚN ASESINO
            playSpecificEffects(asesino, spawnLoc, viewers)

            // 4. EL CONTEO DE LA BARRA (Efecto de carga)
            for (i in 1..100) {
                delay(50) // 5 segundos en total (100 * 50ms)
                bossBar.progress(1.0f - (i / 100f)) // La barra se va vaciando
            }

            // 5. LIMPIEZA FINAL
            withContext(plugin.bukkitDispatcher) {
                viewers.forEach { p ->
                    p.hideBossBar(bossBar)
                    p.gameMode = GameMode.SURVIVAL

                    // Teleport a sus posiciones reales de juego
                    if (p.uniqueId == killerPlayer.uniqueId) {
                        p.teleport(spawnLoc)
                    } else {
                        // El GameManager se encarga de los spawns de supervivientes después de esto
                    }
                }
                plugin.gameManager.broadcastLocalized("game.hunt-start")
                // Le avisamos al GameManager que ya puede soltar a los perros
            }
        }
    }

    private suspend fun playSpecificEffects(asesino: Asesino, loc: Location, viewers: Collection<Player>) {
        withContext(plugin.bukkitDispatcher) {
            when (asesino.id) {
                "herobrine" -> {
                    loc.world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)
                    loc.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc, 1)
                }
                "slasher" -> {
                    loc.world.playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                }
                "null" -> {
                    loc.world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.1f)
                    loc.world.spawnParticle(org.bukkit.Particle.FLASH, loc, 5)
                }
            }
        }
    }
}
