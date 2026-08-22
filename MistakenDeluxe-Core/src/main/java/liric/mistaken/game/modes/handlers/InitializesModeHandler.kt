package liric.mistaken.game.modes.handlers

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.modes.ModeHandler

import java.time.Duration
import liric.mistaken.utils.color.ColorTranslator
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.Particle
import liric.mistaken.game.entities.GeoffreyEXE

class InitializesModeHandler(plugin: Mistaken, session: GameSession) : ModeHandler(plugin, session) {
    override fun checkSpecialSpawn(timer: Int): Boolean {
        if (timer == 290) {
            val title = ColorTranslator.translate("<dark_red><bold><obfuscated>||</obfuscated> ¡GEOFFREY ESTÁ AQUÍ! <obfuscated>||</obfuscated>")
            val subtitle = ColorTranslator.translate("<dark_gray>Nadie sobrevivirá...")
            val times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(500))

            plugin.server.onlinePlayers.forEach { p ->
                p.showTitle(Title.title(title, subtitle, times))
                p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f)
                p.playSound(p.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.5f)
                
                p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 1, false, false, false))
            }

            val spawnLoc = session.getCurrentKiller()?.location ?: plugin.server.onlinePlayers.firstOrNull()?.location

            if (spawnLoc != null) {
                val geoffreyLoc = spawnLoc.clone().add(0.0, 15.0, 0.0)
                geoffreyLoc.world.spawnParticle(Particle.EXPLOSION_EMITTER, geoffreyLoc, 2)
                geoffreyLoc.world.playSound(geoffreyLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.5f)

                val geoffreyEntity = GeoffreyEXE(plugin).apply { assignedSession = session }
                geoffreyEntity.spawn(geoffreyLoc)
                session.stateController.geoffreyEntity = geoffreyEntity
            }
            return true
        }
        return false
    }
}
