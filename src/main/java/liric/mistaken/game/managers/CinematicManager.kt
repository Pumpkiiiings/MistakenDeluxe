package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.Particle

/**
 * [LIRIC-MISTAKEN 2.0]
 * CinematicManager: Motor de presentaciones con BossBar dinámica.
 * FIX: Reemplazo de Corrutinas por GlobalRegionScheduler para animaciones por ticks.
 */
class CinematicManager(private val plugin: Mistaken) {

    /**
     * Lanza la presentación del asesino.
     */
    fun playKillerIntro(asesino: Asesino, killerPlayer: Player, viewers: Collection<Player>, spawnLoc: Location) {
        // 1. CREAR LA BOSSBAR
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

        // 2. PREPARACIÓN INICIAL (Síncrona en el hilo global)
        // Usamos GlobalRegionScheduler porque afecta a múltiples jugadores y coordenadas
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            // Cálculo de cámara: 6 bloques frente al asesino, mirando hacia él
            val camLoc = spawnLoc.clone().add(spawnLoc.direction.multiply(-6)).add(0.0, 2.5, 0.0)
            val vectorToKiller = spawnLoc.toVector().subtract(camLoc.toVector())
            camLoc.direction = vectorToKiller

            viewers.forEach { p ->
                p.showBossBar(bossBar)
                p.gameMode = GameMode.SPECTATOR
                // Teleport asíncrono para suavidad
                p.teleportAsync(camLoc)
            }

            // 3. EFECTOS
            playSpecificEffects(asesino, spawnLoc)

            // 4. ANIMACIÓN DE LA BARRA (Scheduler por Ticks)
            startCinematicLoop(bossBar, viewers, killerPlayer, spawnLoc)
        }
    }

    private fun startCinematicLoop(bossBar: BossBar, viewers: Collection<Player>, killerPlayer: Player, spawnLoc: Location) {
        var ticks = 0
        val totalTicks = 100 // 5 segundos (20 ticks * 5)

        // Ejecutamos cada tick (50ms)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            ticks++

            // Actualizar progreso
            val progress = (1.0f - (ticks.toFloat() / totalTicks.toFloat())).coerceIn(0f, 1f)
            bossBar.progress(progress)

            // Si termina el tiempo
            if (ticks >= totalTicks) {
                task.cancel()

                // 5. LIMPIEZA FINAL
                viewers.forEach { p ->
                    p.hideBossBar(bossBar)
                    p.gameMode = GameMode.SURVIVAL

                    if (p.uniqueId == killerPlayer.uniqueId) {
                        p.teleportAsync(spawnLoc)
                    } else {
                        // Aquí podrías disparar el TP de supervivientes si no lo hace el setupPlayers
                        // O simplemente dejarlos listos para que el GameManager maneje su lógica
                    }
                }

                // Notificar al GameManager
                plugin.gameManager.broadcastLocalized("game.hunt-start")
                // Aquí podrías llamar a un método en GameManager para indicar que la intro terminó
                // plugin.gameManager.onCinematicFinished()
            }
        }, 1L, 1L)
    }

    private fun playSpecificEffects(asesino: Asesino, loc: Location) {
        // Al estar dentro del scheduler global, esto es Thread-Safe
        when (asesino.id.lowercase()) {
            "herobrine" -> {
                loc.world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)
                loc.world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1)
            }
            "slasher" -> {
                loc.world.playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
            }
            "null", "the_null" -> {
                loc.world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.1f)
                loc.world.spawnParticle(Particle.FLASH, loc, 5)
            }
            // Puedes agregar más casos aquí
        }
    }
}
