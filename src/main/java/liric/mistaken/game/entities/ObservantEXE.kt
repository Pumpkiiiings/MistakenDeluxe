package liric.mistaken.game.entities

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TROLL
 * OBSERVANT.EXE: El ente que solo se mueve cuando no lo ves.
 * Mecánica: Estatua si lo ven | Movimiento si no lo ven | Bloodlust tras 60s.
 */
class ObservantEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = true
    private var currentTarget: Player? = null

    private var lastVictimUUID: UUID? = null
    private val teamName = "ObservantGlow"

    // Timer para el Bloodlust (si no lo ven por 60 segundos)
    private var secondsNotSeen = 0

    fun spawn(startLoc: Location) {
        plugin.pluginScope.launch(plugin.bukkitDispatcher) {
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName).apply { color(NamedTextColor.WHITE) }

                // --- CONSTRUCCIÓN 3x3x3 ---
                val body = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3f, 3f, 3f), Vector3f(-1.5f, 0f, -1.5f))
                val eyeL = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.5f, 0.2f, 0.1f), Vector3f(-1.0f, 1.8f, 1.51f))
                val eyeR = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.5f, 0.2f, 0.1f), Vector3f(0.5f, 1.8f, 1.51f))
                val handL = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.8f, 0.8f, 0.8f), Vector3f(-2.8f, 0.5f, 0.5f))
                val handR = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.8f, 0.8f, 0.8f), Vector3f(2.0f, 0.5f, 0.5f))

                parts.addAll(listOf(body, eyeL, eyeR, handL, handR))
                parts.forEach { team.addEntry(it.uniqueId.toString()) }

                Bukkit.broadcast(plugin.mm.deserialize("<newline><gray><b>[!]</b> <white>Algo se siente diferente... <red><b>OBSERVANT.EXE</b> <gray>ha entrado."))
                iniciarIA()
            } catch (e: Exception) {
                plugin.logger.severe("Fallo al invocar al Observant: ${e.message}")
            }
        }
    }

    private fun createPart(loc: Location, mat: Material, scale: Vector3f, translation: Vector3f): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(translation, Quaternionf(), scale, Quaternionf())
            bd.isPersistent = false
            bd.interpolationDuration = 2
            bd.teleportDuration = 2
            bd.brightness = org.bukkit.entity.Display.Brightness(15, 15)
            bd.isGlowing = true
        }
    }

    private fun iniciarIA() {
        scope.launch {
            var secondCounter = 0
            while (isRunning) {
                // 1. BÚSQUEDA DE OBJETIVO
                val target = withContext(plugin.bukkitDispatcher) {
                    val bodyLoc = parts.firstOrNull()?.location ?: return@withContext null
                    val nearby = bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0)
                        .filter { it.gameMode == org.bukkit.GameMode.SURVIVAL && !plugin.isIgnored(it) }

                    nearby.filter { it.uniqueId != lastVictimUUID }.minByOrNull { it.location.distanceSquared(bodyLoc) }
                        ?: nearby.minByOrNull { it.location.distanceSquared(bodyLoc) }
                }

                if (target == null) { delay(2000); continue }
                currentTarget = target

                // 2. ¿ALGUIEN LO ESTÁ VIENDO?
                val isBeingWatched = withContext(plugin.bukkitDispatcher) {
                    val bodyLoc = parts[0].location
                    target.world.getNearbyPlayers(bodyLoc, 60.0).any { player ->
                        isLookingAt(player, bodyLoc)
                    }
                }

                if (isBeingWatched && secondsNotSeen < 60) {
                    // SI LO VEN: Se queda tieso y el contador de Bloodlust baja/se resetea
                    secondsNotSeen = 0
                    delay(500)
                    continue
                }

                // 3. FASE DE MOVIMIENTO (Si no lo ven O si ya pasaron 60 segundos)
                secondsNotSeen++

                withContext(plugin.bukkitDispatcher) {
                    val bodyLoc = parts[0].location
                    val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()

                    // Si ya pasó el minuto, se vuelve un misil (4m), si no, pasos de acecho (2.5m)
                    val speed = if (secondsNotSeen >= 60) 4.0 else 2.5
                    val nextLoc = bodyLoc.add(dir.multiply(speed))

                    moverTodo(nextLoc, true)
                    target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f)
                    aplicarAuraMiedo(nextLoc)

                    // Impacto
                    if (nextLoc.distanceSquared(target.location) < 3.0) {
                        ejecutarMuerte(target)
                        secondsNotSeen = 0
                    }
                }

                // Si está en Bloodlust se mueve más rápido (cada 0.2s), si no, más lento (cada 0.8s)
                val moveDelay = if (secondsNotSeen >= 60) 200L else 800L
                delay(moveDelay)
            }
        }
    }

    /**
     * 🔥 LÓGICA DE WEEPING ANGEL:
     * Calcula si el jugador está mirando en la dirección de la entidad.
     */
    private fun isLookingAt(player: Player, loc: Location): Boolean {
        val playerLoc = player.eyeLocation
        val vecToEntity = loc.toVector().subtract(playerLoc.toVector()).normalize()
        val playerDirection = playerLoc.direction.normalize()

        // El producto punto determina el ángulo. 0.7 ~ 45 grados de FOV
        val dot = playerDirection.dot(vecToEntity)
        if (dot < 0.7) return false

        // Raytrace para ver si hay bloques en medio
        val result = player.world.rayTraceBlocks(playerLoc, vecToEntity, 60.0)
        return result == null || result.hitBlock == null // Si es nulo, no hay bloques tapando
    }

    private fun moverTodo(loc: Location, look: Boolean) {
        if (parts.isEmpty() || !parts[0].isValid) return
        val newL = loc.clone()
        if (look && currentTarget != null) {
            val dir = currentTarget!!.location.toVector().subtract(loc.toVector())
            newL.setDirection(dir)
        }
        parts.forEach { it.teleport(newL) }
    }

    private fun aplicarAuraMiedo(loc: Location) {
        loc.world.getNearbyPlayers(loc, 10.0).forEach { p ->
            if (!plugin.asesinoManager.esElAsesino(p)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false, false))
            }
        }
    }

    private fun ejecutarMuerte(victim: Player) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, victim.location, 3)
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)

        repeat(5) { plugin.gameManager.combatManager.takeDamage(victim) }

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 4))

        Bukkit.broadcast(plugin.mm.deserialize("<red><b>[!]</b> <white>${victim.name} fue atrapado por <dark_gray><b>OBSERVANT.EXE</b></dark_gray> (No debiste parpadear)"))
    }

    private fun explodeAndRemove() {
        if (parts.isNotEmpty()) {
            val loc = parts[0].location
            loc.world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f)
        }
        remove()
    }

    fun remove() {
        isRunning = false
        parts.forEach { it.remove() }
        parts.clear()
        scope.cancel()
    }
}
