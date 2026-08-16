package liric.mistaken.game.managers.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.packet.PacketFactory
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import pumpking.lib.service.PumpkingServiceManager


class FlashlightManager(private val plugin: Mistaken) {

    private class FlashlightState {
        var task: ScheduledTask? = null
        /** Posiciones con un LIGHT falso enviado ahora mismo. */
        var litBlocks: List<Location> = emptyList()
        /** Bloque real de cada posicion, para poder revertir. */
        var restoreData: Map<Location, BlockData> = emptyMap()
        /** A quien se le envio el ultimo update. */
        var viewers: Set<UUID> = emptySet()
        var worldName: String = ""
    }

    private val states = ConcurrentHashMap<UUID, FlashlightState>()

    private val range: Double get() = plugin.config.getDouble("settings.flashlight.range", 8.0)
    private val points: Int get() = plugin.config.getInt("settings.flashlight.points", 3)
    private val viewerRadius: Double get() = plugin.config.getDouble("settings.flashlight.viewer-radius", 32.0)

    /** Distancia minima al ojo. Mas cerca la luz queda dentro de la cabeza del jugador. */
    private val minDistance = 1.5

    // --- API PUBLICA ---

    fun isOn(player: Player): Boolean = states.containsKey(player.uniqueId)

    /**
     * Mismos requisitos que una habilidad de superviviente (ver SurvivorHabilidadListener).
     * Si devuelve false, el evento de swap NO se cancela: asesinos y lobby conservan la
     * segunda mano normal.
     */
    fun canUse(player: Player): Boolean {
        val session = plugin.sessionManager.getSession(player) ?: return false
        if (session.currentState != GameState.INGAME) return false
        if (session.isKiller(player.uniqueId)) return false
        if (!plugin.supervivienteManager.esSurvivorActivo(player)) return false
        if (player.gameMode != GameMode.SURVIVAL) return false
        if (plugin.combatManager.isFrozen(player)) return false
        return true
    }

    fun toggle(player: Player) {
        if (isOn(player)) {
            disable(player)
            player.playSound(player.location, Sound.BLOCK_LEVER_CLICK, 0.6f, 1.2f)
            PumpkingServiceManager.messages.actionBar(player, "listeners.flashlight.disabled")
        } else {
            if (!canUse(player)) return
            enable(player)
            player.playSound(player.location, Sound.BLOCK_LEVER_CLICK, 0.6f, 1.6f)
            PumpkingServiceManager.messages.actionBar(player, "listeners.flashlight.enabled")
        }
    }

    private fun enable(player: Player) {
        val uuid = player.uniqueId
        if (states.containsKey(uuid)) return

        val state = FlashlightState()
        state.worldName = player.world.name
        states[uuid] = state

        // Scheduler de la entidad: es Folia-safe y se autocancela si el jugador se va.
        // El callback 'retired' cubre el caso de que la entidad desaparezca sin quit event.
        state.task = player.scheduler.runAtFixedRate(
            plugin,
            Consumer { _ -> tick(player) },
            Runnable { clear(uuid) },
            1L,
            2L
        )
    }

    /** Apaga y restaura. Seguro de llamar aunque no este encendida. */
    fun disable(player: Player) {
        val state = states.remove(player.uniqueId) ?: return
        state.task?.cancel()
        restore(state)
    }

    /**
     * Version por UUID: sirve cuando el jugador ya se desconecto y no hay Player.
     * Los bloques se restauran a los espectadores que sigan online.
     */
    fun clear(uuid: UUID) {
        val state = states.remove(uuid) ?: return
        state.task?.cancel()
        restore(state)
    }

    fun disableAll() {
        states.keys.toList().forEach { clear(it) }
    }

    // --- LOGICA INTERNA ---

    private fun tick(player: Player) {
        val state = states[player.uniqueId] ?: return

        if (!canUse(player)) {
            disable(player)
            return
        }

        // Cambio de mundo: los viewers antiguos ya no estan viendo esos bloques,
        
        if (player.world.name != state.worldName) {
            restore(state)
            state.worldName = player.world.name
        }

        val targets = computeBeam(player)
        val audience = audience(player)
        val audienceIds = audience.map { it.uniqueId }.toSet()

        // --- EFECTO VISUAL DE HAZ DE LUZ (PARTICULAS VOLUMETRICAS) ---
        val eye = player.eyeLocation
        val dir = eye.direction
        // Hacer las partículas muy sutiles para no estorbar la visión
        var currentDist = 1.0
        val maxParticleDist = targets.lastOrNull()?.distance(eye) ?: range
        // Color más tenue y tamaño muy pequeño (0.2f en lugar de 0.6f)
        val dustOptions = org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(120, 120, 100), 0.2f)
        
        while (currentDist <= maxParticleDist) {
            val point = eye.clone().add(dir.clone().multiply(currentDist))
            // Enviar la partícula solo a la audiencia
            audience.forEach { viewer ->
                viewer.spawnParticle(org.bukkit.Particle.DUST, point, 1, 0.02, 0.02, 0.02, 0.0, dustOptions)
            }
            // Espaciado más amplio para que no se vea una línea sólida densa
            currentDist += 1.0
        }

        // Quieto y sin cambios de publico: cero packets de bloques.
        if (targets == state.litBlocks && audienceIds == state.viewers) return

        restore(state)

        if (targets.isEmpty()) return

        val restoreData = HashMap<Location, BlockData>(targets.size)
        val lightData = Bukkit.createBlockData(Material.LIGHT)
        if (lightData is org.bukkit.block.data.type.Light) {
            lightData.level = 15
        }
        
        targets.forEach { loc ->
            restoreData[loc] = loc.block.blockData
            audience.forEach { viewer ->
                PacketFactory.blocks.sendBlockChange(viewer, loc, lightData)
            }
        }

        state.litBlocks = targets
        state.restoreData = restoreData
        state.viewers = audienceIds
    }

    /**
     * Muestrea puntos a lo largo del rayo de vision y se queda solo con los que caen en aire.
     * Sobrescribir un bloque solido lo taparia visualmente para todo el que reciba el packet.
     */
    private fun computeBeam(player: Player): List<Location> {
        val eye = player.eyeLocation
        val dir = eye.direction
        val maxRange = range

        val hit = player.world.rayTraceBlocks(eye, dir, maxRange, FluidCollisionMode.NEVER, true)
        // 0.3 de margen para no meter la luz dentro del bloque golpeado.
        val maxDist = hit?.hitPosition?.distance(eye.toVector())?.minus(0.3) ?: maxRange
        if (maxDist < minDistance) return emptyList()

        val total = points.coerceAtLeast(1)
        val result = LinkedHashSet<Location>(total)

        for (i in 1..total) {
            // Reparte los puntos hasta el 90% del alcance util (0.3 / 0.6 / 0.9 con 3 puntos).
            val fraction = (i.toDouble() / total) * 0.9
            val distance = (maxDist * fraction).coerceAtLeast(minDistance)
            if (distance > maxDist) continue

            val block = eye.clone().add(dir.clone().multiply(distance)).block
            if (block.type != Material.AIR && block.type != Material.CAVE_AIR) continue
            result.add(block.location)
        }

        return result.toList()
    }

    /** Jugadores de la misma sesion dentro del radio. Incluye al asesino: la luz delata. */
    private fun audience(player: Player): List<Player> {
        val session = plugin.sessionManager.getSession(player) ?: return listOf(player)
        return player.world.getNearbyPlayers(player.location, viewerRadius)
            .filter { session.players.contains(it.uniqueId) }
    }

    /** Devuelve los bloques reales a los ultimos viewers y limpia el estado visual. */
    private fun restore(state: FlashlightState) {
        if (state.litBlocks.isNotEmpty()) {
            state.viewers.forEach { viewerId ->
                val viewer = Bukkit.getPlayer(viewerId) ?: return@forEach
                if (!viewer.isOnline) return@forEach
                state.restoreData.forEach { (loc, data) ->
                    PacketFactory.blocks.sendBlockChange(viewer, loc, data)
                }
            }
        }
        state.litBlocks = emptyList()
        state.restoreData = emptyMap()
        state.viewers = emptySet()
    }
}
