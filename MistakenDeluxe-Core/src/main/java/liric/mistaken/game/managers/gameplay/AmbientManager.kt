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


class AmbientManager(private val plugin: Mistaken) {

    private val packetFactory = Vortex(plugin)
    private val trackedSurvivors = ConcurrentHashMap.newKeySet<UUID>()
    private val darknessEffect = PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false)

    /** Decide cuándo puede pasar algo. Ver [TensionDirector]. */
    val director = TensionDirector(plugin)

    init {
        startGlobalTask()
    }

    private fun startGlobalTask() {
        // globalRegionScheduler, no asyncScheduler: aquí se lee estado de Bukkit
        // (posición del killer, world, línea de visión) y esas llamadas no son
        // thread-safe fuera del hilo principal. Mismo criterio que el motor de
        // partículas en Mistaken.iniciarMotorDeParticles().
        // 2 ticks = 100 ms, el intervalo original.
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { _ ->
            if (!plugin.isReady) return@runAtFixedRate

            // 🔥 MULTIARENA: Evaluamos cada sesión independiente
            for (session in plugin.sessionManager.activeSessions.values) {
                if (session.currentState != GameState.INGAME) continue

                val killer = session.getCurrentKiller() ?: continue
                if (!killer.isOnline) continue

                // Foto inmutable de la sesión, una vez por tick: los survivors
                // no vuelven a tocar el objeto Player del killer, y los datos
                // compartidos no se recalculan por player.
                val world = killer.world
                val snapshot = TensionDirector.KillerSnapshot(
                    location = killer.location.clone(),
                    lookDirection = killer.location.direction,
                    worldUid = world.uid,
                    aliveSurvivors = director.countAliveSurvivors(session),
                    generatorsLeft = plugin.generatorManager.getTotalGeneratorsInWorld(world) -
                            plugin.generatorManager.getCompletedCountInWorld(world),
                    isDisguised = killer.isCustomNameVisible
                )

                trackedSurvivors.forEach { uuid ->
                    val survivor = Bukkit.getPlayer(uuid)
                    if (survivor == null) {
                        trackedSurvivors.remove(uuid)
                        director.clear(uuid)
                        return@forEach
                    }
                    if (survivor.isOnline && plugin.sessionManager.getSession(survivor) == session) {
                        processSurvivorLogic(survivor, snapshot, session)
                    }
                }
            }
        }, 1L, 2L)
    }

    /**
     * Lógica individual por survivor. El killer llega como [snapshot]:
     * nunca se lee su objeto Player desde aquí.
     */
    private fun processSurvivorLogic(survivor: Player, snapshot: TensionDirector.KillerSnapshot, session: GameSession) {

        
        if (session.isKiller(survivor.uniqueId)) {
            trackedSurvivors.remove(survivor.uniqueId)
            director.clear(survivor.uniqueId)
            survivor.removePotionEffect(PotionEffectType.DARKNESS)
            return
        }

        if (survivor.gameMode != GameMode.SURVIVAL || plugin.spectatorManager.isSpectator(survivor) || plugin.isIgnored(survivor)) {
            return
        }

        if (survivor.world.uid != snapshot.worldUid) return

        val state = director.evaluate(survivor, snapshot)
        val distSq = survivor.location.distanceSquared(snapshot.location)

        // 1. Latido y oscuridad — continuos, escalan con el estado
        applyHeartbeat(survivor, session, state, distSq)

        
        //    El presupuesto (silencio obligatorio tras cada evento) vive en requestEvent.
        if (director.requestEvent(survivor.uniqueId)) {
            fireEventFor(survivor, state)
        }
    }

    private fun applyHeartbeat(survivor: Player, session: GameSession, state: TensionDirector.State, distSq: Double) {
        if (session.settings?.heartbeatsEnabled == false) return
        if (survivor.hasPotionEffect(PotionEffectType.INVISIBILITY)) return
        if (state == TensionDirector.State.CALMA) return
        if (distSq >= 576.0) return // 24 bloques

        // OJO: esta tarea corre cada 2 ticks, así que currentTick siempre es par.
        // Un rate impar solo coincidiría en los múltiplos de 2*rate — el latido
        // saldría a un tercio de la velocidad prevista. Todos los valores pares.
        val rate = when (state) {
            TensionDirector.State.CAZA -> 4
            TensionDirector.State.ACECHO -> 6
            else -> if (distSq < 144.0) 10 else 20
        }

        if (Bukkit.getCurrentTick() % rate == 0) {
            val isVeryClose = distSq < 64.0
            val volume = if (isVeryClose) 1.2f else 0.6f
            val pitch = if (isVeryClose) 1.1f else 0.7f
            survivor.playSound(survivor.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, volume, pitch)
        }

        if (distSq < 100.0) {
            survivor.addPotionEffect(darknessEffect)
        }
    }

    /** Qué susto toca según el estado. El CUÁNDO ya lo decidió el director. */
    private fun fireEventFor(survivor: Player, state: TensionDirector.State) {
        when (state) {
            TensionDirector.State.CALMA -> return

            // Lejano y sonoro: algo se mueve, no sabes dónde.
            TensionDirector.State.INQUIETUD -> playDistortedSound(survivor)

            // Presencia visual en la periferia.
            TensionDirector.State.ACECHO -> triggerParanoia(survivor)

            // Encima: visual + físico a la vez.
            TensionDirector.State.CAZA -> {
                triggerParanoia(survivor)
                packetFactory.sendFakeHit(survivor)
            }
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
        director.clear(p.uniqueId)
    }

    fun stopAll() {
        trackedSurvivors.clear()
        director.clearAll()
    }
}