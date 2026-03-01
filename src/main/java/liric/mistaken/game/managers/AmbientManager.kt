package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.game.TerrorPacketFactory
import liric.mistaken.game.enums.GameState
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
import java.util.concurrent.TimeUnit

/**
 * [LIRIC-MISTAKEN 2.0]
 * AmbientManager: Motor de atmósfera de terror.
 * FIX: Reemplazo de Corrutinas por AsyncScheduler (Paper) para Thread-Safety y precisión.
 */
class AmbientManager(private val plugin: Mistaken) {

    private val packetFactory = TerrorPacketFactory(plugin)
    private val trackedSurvivors = ConcurrentHashMap.newKeySet<UUID>()
    private val darknessEffect = PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false)

    init {
        startGlobalTask()
    }

    private fun startGlobalTask() {
        // Usamos AsyncScheduler: Ejecuta cada 100ms (10 veces/segundo) con precisión de reloj
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!plugin.isReady) return@runAtFixedRate

            if (plugin.gameManager.currentState == GameState.INGAME) {
                val killer = plugin.gameManager.getCurrentAsesino() ?: return@runAtFixedRate
                if (!killer.isOnline) return@runAtFixedRate

                // 🔥 FIX: Leemos la ubicación del asesino UNA VEZ (Thread-Safe snapshot si es posible)
                // En Paper moderno, acceder a .location desde async puede ser riesgoso.
                // Lo ideal es usar la API de rastreo o delegar al scheduler de la entidad.

                // Opción SEGURA: Delegar la lógica pesada a cada jugador
                trackedSurvivors.forEach { uuid ->
                    val survivor = Bukkit.getPlayer(uuid)
                    if (survivor != null && survivor.isOnline) {
                        // Ejecutamos la lógica en el hilo del jugador (RegionScheduler)
                        // Esto permite leer .location y .distanceSquared sin crashear el servidor.
                        survivor.scheduler.execute(plugin, {
                            processSurvivorLogic(survivor, killer)
                        }, null, 0L)
                    } else {
                        trackedSurvivors.remove(uuid)
                    }
                }
            }
        }, 1L, 100L, TimeUnit.MILLISECONDS)
    }

    /**
     * Lógica individual por superviviente (Ejecutada en el hilo seguro del jugador)
     */
    private fun processSurvivorLogic(survivor: Player, killer: Player) {
        // Verificación rápida de mundo
        if (survivor.world != killer.world) return

        val killerLoc = killer.location
        val survivorLoc = survivor.location
        val distSq = survivorLoc.distanceSquared(killerLoc)

        // 1. Heartbeat & Oscuridad
        // 24 bloques = 576.0
        if (distSq < 576.0) {
            val currentTick = Bukkit.getCurrentTick() // Tick actual del servidor

            // Ritmo cardiaco basado en distancia
            val rate = when {
                distSq < 49.0 -> 4   // Muy cerca (cada 4 ticks = 5 latidos/seg)
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

        // ~1% de probabilidad por tick (10 ticks/seg = ~10% por segundo)
        if (dice < 0.005f) { // 0.5%
            val dist = Math.sqrt(distSq)
            if (dist in 15.0..35.0) { // Solo si el asesino no está pegado
                triggerParanoia(survivor)
            }
        }

        // Sonidos ambientales raros
        if (dice > 0.998f) { // 0.2%
            playDistortedSound(survivor)
        }
    }

    private fun triggerParanoia(survivor: Player) {
        val dice = ThreadLocalRandom.current().nextFloat()

        if (dice < 0.4f) {
            // Sombra periférica
            val shadowLoc = getPeripheryLocation(survivor)
            packetFactory.spawnShadowEntity(survivor, shadowLoc, 15) // 15 ticks
            survivor.playSound(survivor.location, Sound.ENTITY_ENDERMAN_STARE, 0.4f, 0.1f)
        } else {
            // Piso falso
            packetFactory.sendFakeAir(survivor, survivor.location.subtract(0.0, 1.0, 0.0), 12)
            survivor.playSound(survivor.location, Sound.BLOCK_GLASS_BREAK, 0.3f, 0.5f)
        }
    }

    private fun getPeripheryLocation(p: Player): Location {
        val loc = p.location
        val dir = loc.direction
        // Vector perpendicular para obtener "el rabillo del ojo"
        val side = Vector(-dir.z, 0.0, dir.x).normalize()

        if (ThreadLocalRandom.current().nextBoolean()) side.multiply(-1.0)

        // 7 bloques adelante, 4 a un lado
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
        // Sonido en una ubicación aleatoria cercana
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
        // No necesitamos cancelar el Scheduler global,
        // simplemente dejará de procesar al estar la lista vacía.
    }
}
