package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.modes.ModeHandler
import liric.mistaken.utils.color.ColorTranslator
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.time.Duration
import java.util.Locale

class LegacyModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {

    override val enableAbilities: Boolean = false

    // Distancia al cuadrado para activar el drenado (2.5 bloques -> 2.5 * 2.5 = 6.25)
    private val drainDistanceSq: Double = 6.25
    // Daño aplicado en cada pulso de drenado (cada 10 ticks = 0.5s)
    private val drainDamage: Double = 1.5

    override fun onGameStart() {
        val title = ColorTranslator.translate("<bold><#FF3344>MODO LEGACY</#FF3344></bold>")
        val subtitle = ColorTranslator.translate("<gray>¡Sin habilidades! El asesino drena tu vida al acercarse.</gray>")
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        val titleObj = Title.title(title, subtitle, times)

        session.getPlayers().forEach { player ->
            if (!plugin.spectatorManager.isSpectator(player)) {
                player.showTitle(titleObj)
                player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.6f)
                clearHotbar(player)
            }
        }
    }

    override fun onPlayerSpawn(player: Player, isKiller: Boolean) {
        // Delay para asegurar que equipKiller / registrarSurvivor hayan finalizado
        player.scheduler.runDelayed(plugin, { _ ->
            if (player.isOnline && session.currentState == GameState.INGAME) {
                clearHotbar(player)
            }
        }, null, 20L)
    }

    private fun clearHotbar(player: Player) {
        for (i in 0..8) {
            player.inventory.setItem(i, null)
        }
        player.inventory.setItemInOffHand(null)
        player.updateInventory()
    }

    override fun onPlayerHit(attacker: Player, victim: Player, event: EntityDamageByEntityEvent) {
        // En modo Legacy ningún ataque físico directo con clicks funciona: solo la proximidad del asesino
        event.isCancelled = true
    }

    override fun onTick(ticks: Int) {
        if (session.currentState != GameState.INGAME) return

        // Drenado de vida cada 10 ticks (0.5 segundos)
        if (ticks % 10 != 0) return

        val killers = session.killersUUIDs.mapNotNull { plugin.server.getPlayer(it) }.filter {
            it.isOnline && it.gameMode == GameMode.SURVIVAL && !plugin.spectatorManager.isSpectator(it)
        }
        if (killers.isEmpty()) return

        val survivors = session.getPlayers().filter {
            !session.isKiller(it.uniqueId) &&
            it.isOnline &&
            it.gameMode == GameMode.SURVIVAL &&
            !plugin.spectatorManager.isSpectator(it) &&
            !plugin.isIgnored(it) &&
            !plugin.combatManager.isFrozen(it)
        }
        if (survivors.isEmpty()) return

        for (killer in killers) {
            for (survivor in survivors) {
                if (survivor.world != killer.world) continue

                val distSq = killer.location.distanceSquared(survivor.location)
                if (distSq <= drainDistanceSq) {
                    session.combatManager.takeDamage(survivor, drainDamage, "LegacyProximity")
                    survivor.playHurtAnimation(0.0f)
                    survivor.world.playSound(survivor.location, Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f)

                    val centerLoc = survivor.location.clone().add(0.0, 1.0, 0.0)
                    survivor.world.spawnParticle(Particle.DAMAGE_INDICATOR, centerLoc, 3, 0.2, 0.2, 0.2, 0.05)
                    survivor.world.spawnParticle(
                        Particle.DUST,
                        centerLoc,
                        6,
                        0.3,
                        0.3,
                        0.3,
                        Particle.DustOptions(Color.RED, 1.2f)
                    )

                    killer.playSound(killer.location, Sound.ENTITY_PHANTOM_BITE, 0.5f, 1.2f)

                    val remainingHP = String.format(Locale.US, "%.1f", survivor.health)
                    killer.sendActionBar(
                        ColorTranslator.translate(
                            "<red>⚔ Drenando vida a: <yellow>${survivor.name}</yellow> (<white>$remainingHP HP</white>)</red>"
                        )
                    )
                    survivor.sendActionBar(
                        ColorTranslator.translate(
                            "<red><bold>⚠ ¡EL ASESINO TE ESTÁ DRENANDO LA VIDA! ⚠</bold></red>"
                        )
                    )
                }
            }
        }
    }
}
