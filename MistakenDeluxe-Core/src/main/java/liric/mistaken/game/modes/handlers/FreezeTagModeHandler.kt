package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.modes.ModeHandler
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.utils.color.ColorTranslator
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.Locale
import java.util.function.Consumer

class FreezeTagModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {

    override val enableCriticalSwimming = false
    override val enableLastManStanding = false
    override val enableRescueInteraction = true

    override fun onLethalHit(victim: Player): Boolean {
        freezePlayer(victim)
        return true
    }

    override fun onPlayerHit(attacker: Player, victim: Player, event: EntityDamageByEntityEvent) {
        val isAttackerKiller = session.isKiller(attacker.uniqueId)
        val isVictimKiller = session.isKiller(victim.uniqueId)
        
        // Survivor salva a Survivor congelado
        if (!isAttackerKiller && !isVictimKiller) {
            if (plugin.combatManager.isFrozen(victim)) {
                plugin.combatManager.resetPlayer(victim)
                victim.sendMessage(ColorTranslator.translate("<green>¡${attacker.name} te ha descongelado!"))
                attacker.sendMessage(ColorTranslator.translate("<green>¡Has descongelado a ${victim.name}!"))
            } else {
                event.isCancelled = true // Fuego amigo no hace daño si no está congelado
            }
        }
    }

    // === Lógica de congelamiento (antes en CombatManager) ===

    private fun freezePlayer(victim: Player) {
        if (!plugin.combatManager.addFrozen(victim.uniqueId)) return
        runOnMain {
            victim.inventory.helmet = ItemStack(Material.ICE)
            victim.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.0
            victim.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.0
            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false))
            victim.world.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)

            startFreezeTimer(victim)

            session.broadcastLocalized("game.player-frozen", Placeholder.parsed("player", victim.name))
            session.playerController.checkWinCondition()
        }
    }

    private fun startFreezeTimer(victim: Player) {
        var timeLeft = 60
        victim.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!plugin.combatManager.isFrozen(victim) || !victim.isOnline) {
                task.cancel()
                return@Consumer
            }
            
            victim.world.spawnParticle(Particle.SOUL_FIRE_FLAME, victim.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.02)
            
            val timeFormatted = String.format(Locale.US, "%d:%02d", timeLeft / 60, timeLeft % 60)
            victim.showTitle(
                Title.title(
                    MessageService.getComponent(victim, "game.freeze-title"),
                    MessageService.getComponent(
                        victim,
                        "game.freeze-subtitle",
                        Placeholder.parsed("time", timeFormatted)
                    )
                )
            )
            timeLeft--
            if (timeLeft <= 0) {
                task.cancel()
                runOnMain {
                    session.playerController.handlePlayerDeath(victim)
                }
            }
        }, null, 0L, 20L)
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) block()
        else plugin.server.globalRegionScheduler.run(plugin) { _ -> block() }
    }
}
