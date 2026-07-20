package liric.mistaken.game.entities

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.time.Duration
import java.util.*
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TROLL
 * POU.EXE: La mascota virtual abandonada.
 * ADAPTADO: Multiarena/Velocity con tracking dinámico y aislamiento de sesión.
 */
class PouEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<liric.mistaken.packet.fake.VirtualBlockDisplay>()
    private var isRunning = false
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null
    private var consecutiveMisses = 0

    // 🔥 Referencia a la sesión a la que pertenece esta entidad
    private var assignedSession: GameSession? = null

    private val teamWhite = "PouGlow"
    private val teamRed = "PouAngry"

    private var fase = 0
    private var ticksEnFase = 0
    private var saltos = 0

    fun spawn(startLoc: Location) {
        // 🔥 Detectamos la sesión basada en el mundo del spawn
        assignedSession = plugin.sessionManager.activeSessions.values.find {
            it.currentMapName != "Esperando..." && it.getPlayers().any { p -> p.world == startLoc.world }
        }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                if (scoreboard.getTeam(teamWhite) == null) scoreboard.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                if (scoreboard.getTeam(teamRed) == null) scoreboard.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                // --- INGENIERÍA DE LA PAPA (POU) ---
                val base = createPart(startLoc, Material.BROWN_CONCRETE, Vector3f(2.5f, 0.8f, 2.5f), Vector3f(-1.25f, 0f, -1.25f))
                val mid = createPart(startLoc, Material.BROWN_CONCRETE, Vector3f(2.0f, 0.8f, 2.0f), Vector3f(-1.0f, 0.8f, -1.0f))
                val top = createPart(startLoc, Material.BROWN_CONCRETE, Vector3f(1.2f, 0.6f, 1.2f), Vector3f(-0.6f, 1.6f, -0.6f))
                val eyeL = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(0.5f, 0.5f, 0.1f), Vector3f(-0.7f, 1.0f, 0.91f))
                val eyeR = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(0.5f, 0.5f, 0.1f), Vector3f(0.2f, 1.0f, 0.91f))
                val pupilL = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.2f, 0.11f), Vector3f(-0.55f, 1.15f, 0.92f))
                val pupilR = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.2f, 0.11f), Vector3f(0.35f, 1.15f, 0.92f))

                parts.addAll(listOf(base, mid, top, eyeL, eyeR, pupilL, pupilR))
                setGlowColor(NamedTextColor.WHITE)

                // 🔥 Broadcast solo para la sesión afectada
                val spawnMsg = pumpking.lib.color.ColorTranslator.translate("<newline><yellow><b>[!]</b> <white>Se ha detectado una mascota virtual abandonada... <brown><b>POU.EXE</b>")
                assignedSession?.getPlayers()?.forEach { it.sendMessage(spawnMsg) }

                isRunning = true
                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error("[ERROR] [Entity] Failed to invoke Pou: ${e.message}")
            }
        }
    }

    private fun createPart(loc: Location, mat: Material, scale: Vector3f, translation: Vector3f): liric.mistaken.packet.fake.VirtualBlockDisplay {
        return liric.mistaken.packet.PacketFactory.displays.buildBlockDisplay(org.bukkit.Bukkit.getOnlinePlayers().toList(), loc) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(translation, Quaternionf(), scale, Quaternionf())
            bd.isPersistent = false
            bd.interpolationDuration = 1
            bd.teleportDuration = 1
            bd.brightness = Display.Brightness(15, 15)
            bd.isGlowing = true
        }
    }

    // 🔥 Obtener el objetivo más cercano dentro de la sesión asignada
    private fun getClosestTarget(): Player? {
        val bodyLoc = if (parts.isNotEmpty()) parts[0].location else return null
        val potentialTargets = if (assignedSession != null) {
            assignedSession!!.getPlayers().filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
        } else {
            bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0).filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
        }

        return potentialTargets.filter { it.uniqueId != lastVictimUUID }
            .minByOrNull { it.location.distanceSquared(bodyLoc) }
            ?: potentialTargets.minByOrNull { it.location.distanceSquared(bodyLoc) }
    }

    private fun iniciarIA() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!isRunning || parts.isEmpty() || !parts[0].isValid) {
                task.cancel()
                return@Consumer
            }

            val bodyLoc = parts[0].location
            val target = getClosestTarget()

            // BUSCAR PRESA
            if (fase == 0) {
                if (target != null) {
                    fase = 1
                    ticksEnFase = 0
                    saltos = 0
                }
                return@Consumer
            }

            // PERDIÓ AL TARGET O CAMBIÓ DE SESIÓN
            if (target == null || !target.isOnline) {
                fase = 0
                currentTarget = null
                return@Consumer
            }

            currentTarget = target

            when (fase) {
                1 -> { // Saltos (Cada 16 ticks)
                    if (ticksEnFase % 16 == 0) {
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val nextLoc = bodyLoc.add(dir.multiply(2.2))
                        moverTodo(nextLoc, target.location)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f)
                        aplicarAura(nextLoc, 20)
                        saltos++

                        if (saltos >= 5) {
                            fase = 2
                            ticksEnFase = 0
                        }
                    }
                }
                2 -> { // Advertencia
                    if (ticksEnFase == 16) {
                        target.playSound(target.location, Sound.ENTITY_GENERIC_EAT, 2f, 0.5f)
                        target.showTitle(Title.title(
                            pumpking.lib.color.ColorTranslator.translate("<gradient:#8B4513:#D2B48C><b>POU TIENE HAMBRE"),
                            pumpking.lib.color.ColorTranslator.translate("<red>¡Aliméntalo con tu alma!"),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
                        ))
                    }
                    if (ticksEnFase >= 40) {
                        fase = 3
                        ticksEnFase = 0
                        saltos = 0
                    }
                }
                3 -> { // Misil (Cada 2 ticks)
                    if (ticksEnFase % 2 == 0) {
                        val dir = target.location.add(0.0, 0.5, 0.0).toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(3.0))
                        moverTodo(next, target.location)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 0.8f)

                        // Detectar colisión con supervivientes de la sesión
                        val hit = next.world.getNearbyPlayers(next, 3.0).filter { p ->
                            val pSession = plugin.sessionManager.getSession(p)
                            pSession == assignedSession && pSession?.isKiller(p.uniqueId) != true
                        }

                        if (hit.isNotEmpty()) {
                            hit.forEach { ejecutarMuerte(it) }
                            consecutiveMisses = 0
                            fase = 0
                            return@Consumer
                        }

                        saltos++
                        if (saltos >= 15) {
                            consecutiveMisses++
                            fase = 0
                        }
                    }
                }
                4 -> { // Modo Furia
                    if (ticksEnFase == 0) {
                        setGlowColor(NamedTextColor.RED)
                        target.playSound(target.location, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.5f, 0.1f)
                        target.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(target, "anomalies.pou.rage"))
                    }

                    if (ticksEnFase > 20 && ticksEnFase < 120) {
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(1.8))
                        moverTodo(next, target.location)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 0.8f, 2.0f)

                        if (next.distanceSquared(target.location) < 9.0) { // 3 bloques
                            ejecutarMuerte(target, true)
                            consecutiveMisses = 0
                            setGlowColor(NamedTextColor.WHITE)
                            fase = 0
                            return@Consumer
                        }
                    }

                    if (ticksEnFase >= 120) {
                        setGlowColor(NamedTextColor.WHITE)
                        consecutiveMisses = 0
                        fase = 0
                    }
                }
            }
            ticksEnFase++
        }, 1L, 1L)
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.playSound(victim.location, Sound.ENTITY_GENERIC_EAT, 2f, 0.1f)

        // Muerte nativa directa
        victim.health = 0.0

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 4))

        val prefix = if (enrage) "<dark_red><b>[HAMBRE]</b>" else "<brown><b>[!]</b>"
        val deathMsg = pumpking.lib.color.ColorTranslator.translate("$prefix <white>${victim.name} fue devorado por <brown>POU.EXE")

        assignedSession?.getPlayers()?.forEach { it.sendMessage(deathMsg) }
    }

    private fun aplicarAura(loc: Location, time: Int) {
        loc.world.getNearbyPlayers(loc, 10.0).forEach { p ->
            val pSession = plugin.sessionManager.getSession(p)
            if (pSession == assignedSession && pSession?.isKiller(p.uniqueId) != true) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, time, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, time, 2, false, false, false))
            }
        }
    }

    private fun moverTodo(loc: Location, lookAt: Location?) {
        if (parts.isEmpty() || !parts[0].isValid) return
        val newL = loc.clone()
        if (lookAt != null) {
            newL.setDirection(lookAt.toVector().subtract(loc.toVector()))
        }
        parts.forEach { it.teleport(newL) }
    }

    private fun setGlowColor(color: NamedTextColor) {
        val sb = Bukkit.getScoreboardManager().mainScoreboard
        val team = sb.getTeam(if (color == NamedTextColor.RED) teamRed else teamWhite) ?: return
        parts.forEach { team.addEntry(it.uniqueId.toString()) }
    }

    fun remove() {
        isRunning = false
        parts.forEach { if (it.isValid) it.remove() }
        parts.clear()
    }
}



