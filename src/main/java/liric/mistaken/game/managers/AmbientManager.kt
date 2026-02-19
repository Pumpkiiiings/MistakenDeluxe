package liric.mistaken.game.managers

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.game.TerrorPacketFactory
import liric.mistaken.game.enums.GameState
import liric.mistaken.utils.mainThread
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

class AmbientManager(private val plugin: Mistaken) {

    private val mm = MiniMessage.miniMessage()
    private val packetFactory = TerrorPacketFactory(plugin)

    // Optimizamos el Set: ConcurrentHashMap.newKeySet() es más eficiente que Collections.newSetFromMap
    private val trackedSurvivors = ConcurrentHashMap.newKeySet<UUID>()

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Cache de efectos para evitar recrear objetos PotionEffect cada tick
    private val darknessEffect = PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false)

    init {
        startGlobalTask()
    }

    private fun startGlobalTask() {
        job = scope.launch {
            // Espera inicial
            while (isActive && !plugin.isReady) { delay(500L) }

            var tickCounter = 0

            // OPTIMIZACIÓN: Bajamos a 10 ticks por segundo (delay 100ms)
            // Para lógica ambiental de terror, 10 TPS es indistinguible de 20 y ahorra 50% de CPU.
            while (isActive) {
                if (plugin.gameManager.currentState == GameState.INGAME) {
                    tickCounter++

                    val killer = plugin.gameManager.getCurrentAsesino()
                    if (killer != null && killer.isOnline) {
                        val killerLoc = killer.location
                        val world = killerLoc.world

                        // Lista para agrupar a quiénes debemos darles efectos en el hilo principal
                        // Esto evita saltar de hilo por cada jugador (Batching)
                        val targetsForDarkness = mutableListOf<Player>()

                        // NO USAR .toList(): Iteramos directamente sobre el ConcurrentSet
                        val iterator = trackedSurvivors.iterator()
                        while (iterator.hasNext()) {
                            val uuid = iterator.next()
                            val survivor = Bukkit.getPlayer(uuid)

                            if (survivor == null || !survivor.isOnline) {
                                iterator.remove()
                                continue
                            }

                            // 1. Check de mundo (Matemática rápida antes de distanceSquared)
                            if (survivor.world != world) continue

                            val distSq = survivor.location.distanceSquared(killerLoc)

                            // 2. Procesar Heartbeat (Async)
                            // Retorna true si el jugador necesita el efecto de oscuridad
                            if (processHeartbeat(survivor, distSq, tickCounter)) {
                                targetsForDarkness.add(survivor)
                            }

                            // 3. Paranoia (Cada segundo aprox)
                            if (tickCounter % 10 == 0) {
                                processParanoia(survivor, distSq)
                            }

                            // 4. Sonidos (Cada 15 segundos aprox)
                            if (tickCounter % 150 == 0) {
                                if (ThreadLocalRandom.current().nextFloat() < 0.3f) {
                                    playDistortedSound(survivor)
                                }
                            }
                        }

                        // --- EL GRAN AHORRO: UN SOLO SALTO AL HILO PRINCIPAL ---
                        if (targetsForDarkness.isNotEmpty()) {
                            withContext(plugin.mainThread) {
                                for (target in targetsForDarkness) {
                                    if (target.isOnline) target.addPotionEffect(darknessEffect)
                                }
                            }
                        }
                    }
                }
                delay(100L) // 10 veces por segundo
            }
        }
    }

    /**
     * @return true si el jugador está lo suficientemente cerca para recibir oscuridad
     */
    private fun processHeartbeat(survivor: Player, distSq: Double, ticks: Int): Boolean {
        if (distSq > 576.0) return false // 24 bloques

        // Ritmo del latido (optimizado con valores fijos)
        val rate = when {
            distSq < 49.0 -> 2   // -7 bloques (Latido rápido)
            distSq < 144.0 -> 4  // -12 bloques
            else -> 8            // -24 bloques
        }

        if (ticks % rate == 0) {
            val isVeryClose = distSq < 64.0
            val volume = if (isVeryClose) 1.2f else 0.6f
            val pitch = if (isVeryClose) 1.1f else 0.7f

            // Sound API de Bukkit es mayormente thread-safe para enviar paquetes
            survivor.playSound(survivor.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, volume, pitch)

            return distSq < 100.0 // Necesita oscuridad
        }

        return false
    }

    private suspend fun processParanoia(survivor: Player, distSq: Double) {
        if (distSq !in 225.0..1225.0) return

        // Usamos nextFloat() que es ligeramente más rápido que nextInt(100)
        val dice = ThreadLocalRandom.current().nextFloat()

        if (dice < 0.03f) { // ~3% de probabilidad
            val shadowLoc = getPeripheryLocation(survivor)
            packetFactory.spawnShadowEntity(survivor, shadowLoc, 15)
            survivor.playSound(survivor.location, Sound.ENTITY_ENDERMAN_STARE, 0.4f, 0.1f)
        } else if (dice < 0.06f) {
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
        job?.cancel()
        trackedSurvivors.clear()
    }
}
