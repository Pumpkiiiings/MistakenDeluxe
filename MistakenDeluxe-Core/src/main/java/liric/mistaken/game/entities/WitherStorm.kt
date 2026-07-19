package liric.mistaken.game.entities

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.WitherSkull
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0] - BOSS ENTITY
 * WITHER STORM: El Devorador de Mundos.
 * ADAPTADO: Multiarena/Velocity con aislamiento de sesión y tracking dinámico.
 */
class WitherStorm(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = true
    private var currentTarget: Player? = null

    // 🔥 Referencia a la sesión a la que pertenece este Boss
    private var assignedSession: GameSession? = null

    private val teamPurple = "StormGlow"

    private val bodyOffset = Vector(0.0, 0.0, 0.0)
    private val coreOffset = Vector(0.0, 1.5, 0.5)
    private val headCenterOffset = Vector(0.0, 3.5, 0.5)
    private val headLeftOffset = Vector(-2.5, 2.5, 0.0)
    private val headRightOffset = Vector(2.5, 2.5, 0.0)

    private enum class State { IDLE, PERSECUCION, TRACTOR_BEAM, SKULL_RAIN, ROAR }
    private var currentState = State.IDLE
    private var stateTicks = 0

    private var currentLocation: Location? = null

    fun spawn(startLoc: Location) {
        // 🔥 Detectamos la sesión basada en el mundo del spawn
        assignedSession = plugin.sessionManager.activeSessions.values.find {
            it.currentMapName != "Esperando..." && it.getPlayers().any { p -> p.world == startLoc.world }
        }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            try {
                currentLocation = startLoc.clone().add(0.0, 10.0, 0.0)

                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                if (scoreboard.getTeam(teamPurple) == null) {
                    scoreboard.registerNewTeam(teamPurple).apply { color(NamedTextColor.DARK_PURPLE) }
                }

                // 1. Cuerpo
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(4f, 4f, 4f), bodyOffset))

                // 2. Núcleo (Comandos)
                parts.add(createPart(startLoc, Material.REPEATING_COMMAND_BLOCK, Vector3f(1.2f, 1.2f, 1.2f), coreOffset).apply {
                    brightness = Display.Brightness(15, 15)
                })

                // 3. Cabezas y Ojos (Froglights para el brillo púrpura)
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(2.5f, 2.5f, 2.5f), headCenterOffset))
                parts.add(createPart(startLoc, Material.PEARLESCENT_FROGLIGHT, Vector3f(1.8f, 0.4f, 0.2f), headCenterOffset.clone().add(Vector(0.0, 0.5, 1.3))))

                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.8f, 1.8f, 1.8f), headLeftOffset))
                parts.add(createPart(startLoc, Material.PEARLESCENT_FROGLIGHT, Vector3f(1.0f, 0.3f, 0.2f), headLeftOffset.clone().add(Vector(0.0, 0.3, 1.0))))

                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.8f, 1.8f, 1.8f), headRightOffset))
                parts.add(createPart(startLoc, Material.PEARLESCENT_FROGLIGHT, Vector3f(1.0f, 0.3f, 0.2f), headRightOffset.clone().add(Vector(0.0, 0.3, 1.0))))

                parts.forEach { scoreboard.getTeam(teamPurple)?.addEntry(it.uniqueId.toString()) }

                // 🔥 Broadcast solo para la sesión
                val msg = pumpking.lib.color.ColorTranslator.translate("<gradient:#aa00aa:#000000><bold>WITHER STORM</bold></gradient> <red>ha sido invocado.")
                assignedSession?.getPlayers()?.forEach { it.sendMessage(msg) }

                startLoc.world.playSound(startLoc, Sound.ENTITY_WITHER_SPAWN, 5f, 0.5f)
                isRunning = true
                iniciarIA()

            } catch (e: Exception) {
                plugin.componentLogger.error("[ERROR] [Entity] Error spawning Wither Storm: ${e.message}")
            }
        }
    }

    private fun createPart(base: Location, mat: Material, scale: Vector3f, offset: Vector): BlockDisplay {
        return liric.mistaken.packet.PacketFactory.displays.buildBlockDisplay(org.bukkit.Bukkit.getOnlinePlayers().toList(), base) { bd ->
            bd.block = mat.createBlockData()
            val translation = Vector3f(offset.x.toFloat() - (scale.x / 2), offset.y.toFloat() - (scale.y / 2), offset.z.toFloat() - (scale.z / 2))
            bd.transformation = Transformation(translation, Quaternionf(), scale, Quaternionf())
            bd.interpolationDuration = 3
            bd.teleportDuration = 3
            bd.isPersistent = false
            bd.isGlowing = true
            bd.glowColorOverride = Color.PURPLE
        }
    }

    private fun iniciarIA() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!isRunning || parts.isEmpty() || !parts[0].isValid) {
                task.cancel()
                explodeAndDie()
                return@Consumer
            }

            val pivot = currentLocation ?: return@Consumer
            val session = assignedSession

            // BUSCAR PRESA (Aislado por sesión)
            if (stateTicks % 20 == 0) {
                val potentialTargets = if (session != null) {
                    session.getPlayers().filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                } else {
                    pivot.world.getNearbyPlayers(pivot, 100.0).filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                }
                currentTarget = potentialTargets.minByOrNull { it.location.distanceSquared(pivot) }
            }

            when (currentState) {
                State.IDLE -> {
                    hoverEffect(pivot)
                    if (currentTarget != null) changeState(State.PERSECUCION)
                }
                State.PERSECUCION -> {
                    val target = currentTarget
                    if (target == null || !target.isOnline) {
                        changeState(State.IDLE)
                    } else {
                        moveTowards(target.location.add(0.0, 8.0, 0.0), 0.35)

                        if (stateTicks > 60 && ThreadLocalRandom.current().nextInt(100) < 5) {
                            val rng = ThreadLocalRandom.current().nextInt(3)
                            when (rng) {
                                0 -> changeState(State.TRACTOR_BEAM)
                                1 -> changeState(State.SKULL_RAIN)
                                2 -> changeState(State.ROAR)
                            }
                        }
                    }
                }
                State.TRACTOR_BEAM -> processTractorBeam(pivot)
                State.SKULL_RAIN -> processSkullRain(pivot)
                State.ROAR -> processRoar(pivot)
            }

            updatePartsRotation(pivot)
            stateTicks++

        }, 1L, 2L)
    }

    private fun updatePartsRotation(pivot: Location) {
        currentTarget?.let { target ->
            val dir = target.location.toVector().subtract(pivot.toVector()).normalize()
            val targetYaw = Math.toDegrees(kotlin.math.atan2(-dir.x, dir.z)).toFloat()
            pivot.yaw = moveAngle(pivot.yaw, targetYaw, 3.0f)
        }
        parts.forEach { it.teleport(pivot) }
    }

    private fun processTractorBeam(pivot: Location) {
        if (stateTicks > 80) {
            changeState(State.PERSECUCION)
            return
        }

        val beamStart = pivot.clone().add(0.0, 1.0, 0.0).add(pivot.direction.multiply(3.0))
        val beamDir = Vector(0.0, -1.0, 0.0)

        // Visual del Rayo
        for (i in 0..20) {
            val pLoc = beamStart.clone().add(beamDir.clone().multiply(i.toDouble()))
            pLoc.world.spawnParticle(Particle.DUST, pLoc, 3, 2.0, 0.5, 2.0, 0.0, Particle.DustOptions(Color.PURPLE, 3f))
            pLoc.world.spawnParticle(Particle.REVERSE_PORTAL, pLoc, 2, 1.0, 0.5, 1.0, 0.0)
        }

        if (stateTicks % 10 == 0) pivot.world.playSound(pivot, Sound.BLOCK_BEACON_AMBIENT, 5f, 0.5f)

        // Succión de jugadores
        val session = assignedSession
        val victims = (session?.getPlayers() ?: pivot.world.players).filter {
            it.gameMode == GameMode.SURVIVAL && it.location.distanceSquared(beamStart) < 100.0 // Radio de 10 bloques
        }

        victims.forEach { p ->
            val pull = beamStart.toVector().subtract(p.location.toVector()).normalize().multiply(0.5)
            p.velocity = pull.setY(0.4)
            p.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 10, 2, false, false, false))

            if (p.location.distanceSquared(beamStart) < 12.25) { // 3.5 bloques reales
                plugin.combatManager.takeDamage(p)
                p.playSound(p.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
                p.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "anomalies.witherstorm.tractor-beam"))
            }
        }
    }

    private fun processSkullRain(pivot: Location) {
        if (stateTicks > 50) {
            changeState(State.PERSECUCION)
            return
        }

        if (stateTicks % 4 == 0 && currentTarget != null) {
            val side = if (ThreadLocalRandom.current().nextBoolean()) -5.0 else 5.0
            val spawnLoc = pivot.clone().add(pivot.direction.clone().crossProduct(Vector(0,1,0)).multiply(side)).add(0.0, 2.0, 0.0)

            val skull = spawnLoc.world.spawn(spawnLoc, WitherSkull::class.java)
            val targetDir = currentTarget!!.eyeLocation.toVector().subtract(spawnLoc.toVector()).normalize()
            skull.setDirection(targetDir)
            skull.velocity = targetDir.multiply(1.8)
            skull.isCharged = true

            spawnLoc.world.playSound(spawnLoc, Sound.ENTITY_WITHER_SHOOT, 2f, 0.7f)
        }
    }

    private fun processRoar(pivot: Location) {
        if (stateTicks == 1) {
            pivot.world.playSound(pivot, Sound.ENTITY_WARDEN_ROAR, 10f, 0.5f)
            pivot.world.spawnParticle(Particle.SONIC_BOOM, pivot.clone().add(0.0, 2.0, 2.0), 3)

            val session = assignedSession
            (session?.getPlayers() ?: pivot.world.players).forEach { p ->
                if (p.location.distanceSquared(pivot) < 1600.0) { // Radio 40 bloques
                    val push = p.location.toVector().subtract(pivot.toVector()).normalize().multiply(2.5).setY(0.6)
                    p.velocity = push
                    p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                    p.showTitle(Title.title(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "anomalies.witherstorm.title-roar"), pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "anomalies.witherstorm.subtitle-roar")))
                }
            }
        }
        if (stateTicks > 30) changeState(State.PERSECUCION)
    }

    private fun hoverEffect(pivot: Location) {
        val y = sin(System.currentTimeMillis() / 400.0) * 0.08
        pivot.add(0.0, y, 0.0)
    }

    private fun moveTowards(target: Location, speed: Double) {
        val current = currentLocation ?: return
        val dir = target.toVector().subtract(current.toVector())
        if (dir.lengthSquared() > 1.0) {
            current.add(dir.normalize().multiply(speed))
        }
    }

    private fun changeState(newState: State) {
        currentState = newState
        stateTicks = 0
    }

    private fun explodeAndDie() {
        val loc = parts.firstOrNull()?.location ?: return
        loc.world.createExplosion(loc, 4F, false, false)
        loc.world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 10f, 0.5f)

        parts.forEach {
            it.world.spawnParticle(Particle.BLOCK, it.location, 40, 1.0, 1.0, 1.0, it.block)
            it.remove()
        }
        parts.clear()

        val deathMsg = pumpking.lib.color.ColorTranslator.translate("<green>¡La <dark_purple>Wither Storm<green> ha sido derrotada!")
        assignedSession?.getPlayers()?.forEach { it.sendMessage(deathMsg) }
    }

    private fun moveAngle(from: Float, to: Float, step: Float): Float {
        var dist = to - from
        while (dist > 180) dist -= 360f
        while (dist < -180) dist += 360f
        return if (kotlin.math.abs(dist) < step) to else from + Math.copySign(step, dist)
    }

    fun remove() {
        isRunning = false
        parts.forEach { it.remove() }
        parts.clear()
    }
}

