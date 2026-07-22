package liric.mistaken.game.managers.gameplay

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.Vortex
import liric.mistaken.game.enums.GameState
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * [LIRIC-MISTAKEN 2.0]
 * AmbientManager: Motor de atmósfera de terror adaptado a Multiarena.
 */
class AmbientManager(private val plugin: Mistaken) {

    private val packetFactory = Vortex(plugin)
    private val trackedSurvivors = ConcurrentHashMap.newKeySet<UUID>()
    private val darknessEffect = PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false)

    init {
        startGlobalTask()
    }

    private fun startGlobalTask() {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate

            // 🔥 MULTIARENA: Evaluamos cada sesión independiente
            for (session in plugin.sessionManager.activeSessions.values) {
                if (session.currentState != GameState.INGAME) continue

                val killer = session.getCurrentAsesino() ?: continue
                if (!killer.isOnline) continue

                // Iteramos sobre todos los supervivientes trackeados globalmente
                trackedSurvivors.forEach { uuid ->
                    val survivor = Bukkit.getPlayer(uuid)

                    // Si el superviviente está online y pertenece a ESTA sesión
                    if (survivor != null && survivor.isOnline && plugin.sessionManager.getSession(survivor) == session) {
                        survivor.scheduler.execute(plugin, {
                            processSurvivorLogic(survivor, killer, session)
                        }, null, 0L)
                    } else if (survivor == null) {
                        trackedSurvivors.remove(uuid)
                    }
                }
            }
        }, 1L, 100L, TimeUnit.MILLISECONDS)
    }

    /**
     * Lógica individual por superviviente (Ejecutada en el hilo seguro del jugador)
     */
    private fun processSurvivorLogic(survivor: Player, killer: Player, session: GameSession) {

        // Si este "superviviente" en realidad fue elegido como el Killer en esta ronda
        if (session.isKiller(survivor.uniqueId)) {
            trackedSurvivors.remove(survivor.uniqueId)
            survivor.removePotionEffect(PotionEffectType.DARKNESS)
            return
        }

        if (survivor.gameMode != GameMode.SURVIVAL || survivor.isInvisible || plugin.isIgnored(survivor)) {
            return
        }

        if (survivor.world != killer.world) return

        val killerLoc = killer.location
        val survivorLoc = survivor.location
        val distSq = survivorLoc.distanceSquared(killerLoc)

        // 1. Heartbeat & Oscuridad
        // 24 bloques = 576.0
        if (distSq < 576.0 && session.settings?.heartbeatsEnabled != false) {
            val currentTick = Bukkit.getCurrentTick()

            val rate = when {
                distSq < 49.0 -> 4   // Muy cerca
                distSq < 144.0 -> 10 // Cerca
                else -> 20           // Lejos
            }

            if (currentTick % rate == 0) {
                val isVeryClose = distSq < 64.0
                val volume = if (isVeryClose) 1.2f else 0.6f
                val pitch = if (isVeryClose) 1.1f else 0.7f
                survivor.playSound(survivorLoc, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, volume, pitch)
            }

            // Aplicar oscuridad si está muy cerca (< 10 bloques)
            if (distSq < 100.0) {
                survivor.addPotionEffect(darknessEffect)
            }
        }

        // 2. Paranoia y Sonidos (Probabilidad aleatoria)
        val dice = ThreadLocalRandom.current().nextFloat()

        if (dice < 0.005f) { // 0.5%
            val dist = Math.sqrt(distSq)
            if (dist in 15.0..35.0) {
                triggerParanoia(survivor)
            }
        }

        if (dice > 0.998f) { // 0.2%
            playDistortedSound(survivor)
        }
    }

    private fun triggerParanoia(survivor: Player) {
        val dice = ThreadLocalRandom.current().nextFloat()

        if (dice < 0.4f) {
            val shadowLoc = getPeripheryLocation(survivor)
            packetFactory.spawnShadowEntity(survivor, shadowLoc, 15) // 15 ticks
            survivor.playSound(survivor.location, Sound.ENTITY_ENDERMAN_STARE, 0.4f, 0.1f)
        } else {
            packetFactory.sendFakeAir(survivor, survivor.location.subtract(0.0, 1.0, 0.0), 12)
            survivor.playSound(survivor.location, Sound.BLOCK_GLASS_BREAK, 0.3f, 0.5f)
        }
    }

    private fun getPeripheryLocation(p: Player): Location {
        val loc = p.location
        val dir = loc.direction
        val side = Vector(-dir.z, 0.0, dir.x).normalize()

        if (ThreadLocalRandom.current().nextBoolean()) side.multiply(-1.0)

        return loc.add(dir.multiply(7.0)).add(side.multiply(4.0))
    }

    private fun playDistortedSound(p: Player) {
        val sounds = arrayOf(
            Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR,
            Sound.BLOCK_CHEST_OPEN,
            Sound.ENTITY_ENDERMAN_SCREAM,
            Sound.AMBIENT_CAVE
        )
        val random = ThreadLocalRandom.current()
        val loc = p.location.add(
            random.nextDouble(-5.0, 5.0),
            0.0,
            random.nextDouble(-5.0, 5.0)
        )
        p.playSound(loc, sounds[random.nextInt(sounds.size)], 0.4f, 0.5f)
    }

    fun playSurvivorAmbience(survivor: Player) {
        trackedSurvivors.add(survivor.uniqueId)
    }

    fun stopAmbience(p: Player) {
        trackedSurvivors.remove(p.uniqueId)
    }

    fun stopAll() {
        trackedSurvivors.clear()
    }
}