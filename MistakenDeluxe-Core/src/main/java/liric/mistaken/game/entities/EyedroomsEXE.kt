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
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0] - MODO PESADILLA
 * EYEDROOMS.EXE: El Ojo de los Backrooms.
 * ADAPTADO: Multiarena/Velocity con tracking dinÃ¡mico y aislamiento de sesiÃ³n.
 */
class EyedroomsEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = false
    private var lastVictimUUID: UUID? = null
    private val teamName = "EyedroomsGlow"

    // ðŸ”¥ Referencia a la sesiÃ³n a la que pertenece esta entidad
    private var assignedSession: GameSession? = null

    private var fase = 0
    private var ticksEnFase = 0
    private var pasos = 0

    fun spawn(startLoc: Location) {
        // ðŸ”¥ Detectamos la sesiÃ³n basada en el mundo del spawn
        assignedSession = plugin.sessionManager.activeSessions.values.find {
            it.currentMapName != "Esperando..." && it.getPlayers().any { p -> p.world == startLoc.world }
        }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                if (scoreboard.getTeam(teamName) == null) {
                    scoreboard.registerNewTeam(teamName).apply { color(NamedTextColor.DARK_PURPLE) }
                }

                val whiteEye = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(3f, 3f, 3f), Vector3f(-1.5f, 0f, -1.5f))
                val pupil = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.8f, 1.8f, 0.2f), Vector3f(-0.9f, 0.6f, 1.45f))
                val glitch1 = createPart(startLoc, Material.NETHERRACK, Vector3f(0.5f, 0.5f, 0.5f), Vector3f(-2.0f, 2.0f, 0f))
                val glitch2 = createPart(startLoc, Material.NETHERRACK, Vector3f(0.5f, 0.5f, 0.5f), Vector3f(1.5f, 0.5f, 0f))

                parts.addAll(listOf(whiteEye, pupil, glitch1, glitch2))
                parts.forEach { scoreboard.getTeam(teamName)?.addEntry(it.uniqueId.toString()) }

                // ðŸ”¥ Broadcast solo para la sesiÃ³n infectada
                val infectMsg = plugin.mm.deserialize("<newline><dark_purple><b>[!]</b> <white>EL SISTEMA HA SIDO INFECTADO POR <dark_red><b>EYEDROOMS.EXE</b>")
                assignedSession?.getPlayers()?.forEach { it.sendMessage(infectMsg) }

                isRunning = true
                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error("[ERROR] [Entity] Failed to invoke Eyedrooms: ${e.message}")
            }
        }
    }

    private fun createPart(loc: Location, mat: Material, scale: Vector3f, translation: Vector3f): BlockDisplay {
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

    // ðŸ”¥ Obtener el objetivo mÃ¡s cercano dentro de la sesiÃ³n asignada
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
                    pasos = 0
                }
                return@Consumer
            }

            // PERDIÃ“ AL TARGET O CAMBIÃ“ DE SESIÃ“N
            if (target == null || !target.isOnline) {
                fase = 0
                return@Consumer
            }

            when (fase) {
                1 -> { // Acecho (3 saltos largos)
                    if (ticksEnFase % 14 == 0) {
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(3.5))
                        moverTodo(next, target.location)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 0.1f)
                        target.playSound(next, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.5f)
                        aplicarEfectosAgresivos(next, 30)

                        pasos++
                        if (pasos >= 3) {
                            fase = 2
                            ticksEnFase = 0
                        }
                    }
                }
                2 -> { // El Regalo Envenenado
                    if (ticksEnFase == 30) {
                        target.playSound(target.location, Sound.BLOCK_CONDUIT_ACTIVATE, 2f, 0.1f)
                        target.showTitle(Title.title(
                            plugin.mm.deserialize("<dark_purple><obfuscated>ERR_VOICE"),
                            plugin.mm.deserialize("<gray>Has recibido una <green>bendiciÃ³n <red>corrupta"),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
                        ))
                        target.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 2, false, false))
                        target.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 100, 1, false, false))
                        target.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false))
                    }
                    if (ticksEnFase >= 60) {
                        fase = 3
                        ticksEnFase = 0
                        pasos = 0
                    }
                }
                3 -> { // Modo Misil CuÃ¡ntico (Tracking DinÃ¡mico cada 2 ticks)
                    if (ticksEnFase % 2 == 0) {
                        val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(5.0))
                        moverTodo(next, target.location)

                        target.playSound(next, Sound.ENTITY_GHAST_SCREAM, 0.8f, 2.0f)
                        next.world.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, next, 15, 0.8, 0.8, 0.8, 0.1)

                        if (next.distanceSquared(target.location) < 12.25) { // 3.5 bloques reales
                            ejecutarMuerte(target)
                            fase = 0
                            return@Consumer
                        }

                        pasos++
                        if (pasos >= 15) {
                            fase = 0
                        }
                    }
                }
            }
            ticksEnFase++
        }, 1L, 1L)
    }

    private fun aplicarEfectosAgresivos(loc: Location, duration: Int) {
        loc.world.getNearbyPlayers(loc, 15.0).forEach { p ->
            val pSession = plugin.sessionManager.getSession(p)
            // Solo afecta si es de la misma sesiÃ³n y no es el asesino "jugador"
            if (pSession == assignedSession && pSession?.isKiller(p.uniqueId) != true) {
                p.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, duration, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 2, false, false, false))
                p.playSound(p.location, Sound.BLOCK_BEEHIVE_WORK, 0.5f, 0.1f)
            }
        }
    }

    private fun moverTodo(baseLoc: Location, targetLoc: Location?) {
        if (parts.isEmpty() || !parts[0].isValid) return
        val newLoc = baseLoc.clone()
        if (targetLoc != null) {
            newLoc.setDirection(targetLoc.toVector().subtract(baseLoc.toVector()))
        }
        parts.forEach { it.teleport(newLoc) }
    }

    private fun ejecutarMuerte(victim: Player) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.SONIC_BOOM, victim.location, 1)
        victim.world.playSound(victim.location, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.5f)

        // DaÃ±o directo (bypass al combatManager global para asegurar ejecuciÃ³n)
        victim.health = 0.0

        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
        victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 200, 0))

        val deathMsg = plugin.mm.deserialize("<dark_purple><b>[!]</b> <white>${victim.name} fue borrado por la mirada de <dark_red>EYEDROOMS")
        assignedSession?.getPlayers()?.forEach { it.sendMessage(deathMsg) }
    }

    fun remove() {
        isRunning = false
        parts.forEach { if (it.isValid) it.remove() }
        parts.clear()
    }
}

