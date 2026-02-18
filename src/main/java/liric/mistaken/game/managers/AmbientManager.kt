package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.TerrorPacketFactory
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0]
 * AmbientManager: Motor de terror psicológico ultra-optimizado.
 * Uso masivo de Coroutines para liberar el Tick principal del servidor.
 */
class AmbientManager(private val plugin: Mistaken) {

    private val mm = MiniMessage.miniMessage()
    private val packetFactory = TerrorPacketFactory(plugin)

    // Set concurrente para evitar ConcurrentModificationException sin bloquear hilos
    private val trackedSurvivors = Collections.newSetFromMap(ConcurrentHashMap<UUID, Boolean>())

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        startGlobalTask()
    }

    /**
     * Tarea maestra asíncrona.
     * Procesa la lógica de terror cada 50ms (1 tick) sin afectar los TPS.
     */
    private fun startGlobalTask() {
        job = scope.launch {
            var tickCounter = 0
            while (isActive) {
                if (plugin.gameManager.currentState == GameState.INGAME) {
                    tickCounter++

                    val killer = plugin.gameManager.currentAsesino
                    if (killer != null && killer.isOnline) {
                        val killerLoc = killer.location

                        // Iteramos sobre supervivientes de forma asíncrona
                        trackedSurvivors.forEach { uuid ->
                            val survivor = Bukkit.getPlayer(uuid)
                            if (survivor == null || !survivor.isOnline) {
                                trackedSurvivors.remove(uuid)
                                return@forEach
                            }

                            val distSq = survivor.location.distanceSquared(killerLoc)

                            // 1. Latido (Async)
                            processHeartbeat(survivor, distSq, tickCounter)

                            // 2. Paranoia (Async, cada 10 ticks / 0.5s)
                            if (tickCounter % 10 == 0) {
                                processParanoia(survivor, distSq)
                            }

                            // 3. Sonidos Ambientales (Async, cada 300 ticks / 15s)
                            if (tickCounter % 300 == 0) {
                                if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                                    playDistortedSound(survivor)
                                }
                            }
                        }
                    }
                }
                delay(50L) // Pausa de 1 tick
            }
        }
    }

    private suspend fun processHeartbeat(survivor: Player, distSq: Double, ticks: Int) {
        if (distSq > 400.0) return // +20 bloques

        // Determinar ritmo por distancia usando 'when' de Kotlin (más rápido que múltiples ifs)
        val rate = when {
            distSq < 64.0 -> 5  // -8 bloques
            distSq < 225.0 -> 10 // -15 bloques
            else -> 20          // -20 bloques
        }

        if (ticks % rate == 0) {
            val volume = if (distSq < 64.0) 1.4f else 0.7f
            val pitch = if (distSq < 64.0) 1.2f else 0.7f

            // Sonido de latido
            survivor.playSound(survivor.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, volume, pitch)

            // Efecto Darkness (Requiere Hilo Principal)
            if (distSq < 100.0) {
                withContext(Dispatchers.Main) {
                    survivor.addPotionEffect(
                        PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false, false)
                    )
                }
            }
        }
    }

    private suspend fun processParanoia(survivor: Player, distSq: Double) {
        // Rango de acecho: entre 15 y 35 bloques
        if (distSq !in 225.0..1225.0) return

        val dice = ThreadLocalRandom.current().nextInt(100)

        when (dice) {
            0 -> { // Sombra (Packet-based, safe to run async)
                val shadowLoc = getPeripheryLocation(survivor)
                packetFactory.spawnShadowEntity(survivor, shadowLoc, 15)
                survivor.playSound(survivor.location, Sound.ENTITY_ENDERMAN_STARE, 0.4f, 0.1f)
            }
            1 -> { // Glitch de suelo
                packetFactory.sendFakeAir(survivor, survivor.location.subtract(0.0, 1.0, 0.0), 12)
                survivor.playSound(survivor.location, Sound.BLOCK_GLASS_BREAK, 0.3f, 0.5f)
            }
            2 -> { // Mensaje de pánico
                packetFactory.sendFakeHit(survivor)

                // Obtener lenguaje y mensaje de forma reactiva
                val langCode = plugin.playerDataManager.getLanguage(survivor.uniqueId)
                val msgs = plugin.messageConfig.getLangConfig(langCode).getStringList("paranoia")

                if (msgs.isNotEmpty()) {
                    val randomMsg = msgs.random()
                    survivor.sendActionBar(mm.deserialize(randomMsg))
                }
            }
        }
    }

    private fun getPeripheryLocation(p: Player): Location {
        val dir = p.location.direction
        val side = Vector(-dir.z, 0.0, dir.x).normalize()
        if (ThreadLocalRandom.current().nextBoolean()) side.multiply(-1.0)
        return p.location.add(dir.multiply(7.0)).add(side.multiply(4.0))
    }

    private fun playDistortedSound(p: Player) {
        val sounds = arrayOf(
            Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR,
            Sound.BLOCK_CHEST_OPEN,
            Sound.ENTITY_ENDERMAN_SCREAM,
            Sound.AMBIENT_CAVE
        )
        val loc = p.location.add(
            ThreadLocalRandom.current().nextDouble(-5.0, 5.0),
            0.0,
            ThreadLocalRandom.current().nextDouble(-5.0, 5.0)
        )
        p.playSound(loc, sounds.random(), 0.4f, 0.5f)
    }

    fun playSurvivorAmbience(survivor: Player) {
        trackedSurvivors.add(survivor.uniqueId)
    }

    fun stopAmbience(p: Player) {
        trackedSurvivors.remove(p.uniqueId)
    }

    fun stopAll() {
        job?.cancel()
        trackedSurvivors.clear()
    }
}
