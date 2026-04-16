package liric.mistaken.game.managers.audio

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.TerrorPacketFactory
import liric.mistaken.game.enums.GameState
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
 * AmbientManager: Atmósfera optimizada para Folia (Reloj asíncrono interno).
 */
class AmbientManager(private val plugin: Mistaken) {

    private val packetFactory = TerrorPacketFactory(plugin)
    private val trackedSurvivors = ConcurrentHashMap.newKeySet<UUID>()
    private val darknessEffect = PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false)

    // Reloj interno para sincronizar la música/latidos de forma segura sin Bukkit.getCurrentTick()
    private var internalTick = 0

    init {
        startGlobalTask()
    }

    private fun startGlobalTask() {
        // La tarea se ejecuta cada 100ms (lo que equivale a 2 ticks en Minecraft)
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate
            internalTick += 2

            val sessionManager = plugin.sessionManager ?: return@runAtFixedRate

            for (session in sessionManager.activeSessions.values) {
                if (session.currentState != GameState.INGAME) continue

                val killer = session.getCurrentAsesino() ?: continue
                if (!killer.isOnline) continue

                trackedSurvivors.forEach { uuid ->
                    val survivor = plugin.server.getPlayer(uuid)

                    if (survivor != null && survivor.isOnline && sessionManager.getSession(survivor) == session) {
                        survivor.scheduler.run(plugin, { _ ->
                            processSurvivorLogic(survivor, killer, session)
                        }, null)
                    } else if (survivor == null) {
                        trackedSurvivors.remove(uuid)
                    }
                }
            }
        }, 1L, 100L, TimeUnit.MILLISECONDS)
    }

    private fun processSurvivorLogic(survivor: Player, killer: Player, session: GameSession) {
        if (session.esAsesino(survivor.uniqueId)) {
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

        if (distSq < 576.0) { // 24 bloques
            val rate = when {
                distSq < 49.0 -> 4
                distSq < 144.0 -> 10
                else -> 20
            }

            if (internalTick % rate == 0) {
                val isVeryClose = distSq < 64.0
                val volume = if (isVeryClose) 1.2f else 0.6f
                val pitch = if (isVeryClose) 1.1f else 0.7f
                survivor.playSound(survivorLoc, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, volume, pitch)
            }

            if (distSq < 100.0) { // 10 bloques
                survivor.addPotionEffect(darknessEffect)
            }
        }

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
            packetFactory.spawnShadowEntity(survivor, shadowLoc, 15)
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

    fun playSurvivorAmbience(survivor: Player) { trackedSurvivors.add(survivor.uniqueId) }
    fun stopAmbience(p: Player) { trackedSurvivors.remove(p.uniqueId) }
    fun stopAll() { trackedSurvivors.clear() }
}