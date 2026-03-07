package liric.mistaken.game.entities

import liric.mistaken.Mistaken
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
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0] - BOSS ENTITY
 * WITHER STORM: El Devorador de Mundos.
 * FIX: Corregido error de sintaxis en Vector.add() y createBlockData().
 */
class WitherStorm(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = true
    private var currentTarget: Player? = null

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
        plugin.server.globalRegionScheduler.run(plugin) {
            try {
                currentLocation = startLoc.clone().add(0.0, 5.0, 0.0)

                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                val team = scoreboard.getTeam(teamPurple) ?: scoreboard.registerNewTeam(teamPurple).apply {
                    color(NamedTextColor.DARK_PURPLE)
                }

                // 1. Cuerpo
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(4f, 4f, 4f), bodyOffset))

                // 2. Núcleo
                parts.add(createPart(startLoc, Material.REPEATING_COMMAND_BLOCK, Vector3f(1.2f, 1.2f, 1.2f), coreOffset).apply {
                    brightness = Display.Brightness(15, 15)
                })

                // 3. Cabeza Central
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(2.5f, 2.5f, 2.5f), headCenterOffset))
                parts.add(createPart(startLoc, Material.PEARLESCENT_FROGLIGHT, Vector3f(1.8f, 0.4f, 0.2f), headCenterOffset.clone().add(Vector(0.0, 0.5, 1.3))))

                // 4. Cabeza Izquierda
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.8f, 1.8f, 1.8f), headLeftOffset))
                parts.add(createPart(startLoc, Material.PEARLESCENT_FROGLIGHT, Vector3f(1.0f, 0.3f, 0.2f), headLeftOffset.clone().add(Vector(0.0, 0.3, 1.0))))

                // 5. Cabeza Derecha
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.8f, 1.8f, 1.8f), headRightOffset))
                parts.add(createPart(startLoc, Material.PEARLESCENT_FROGLIGHT, Vector3f(1.0f, 0.3f, 0.2f), headRightOffset.clone().add(Vector(0.0, 0.3, 1.0))))

                parts.forEach {
                    if (!team.hasEntry(it.uniqueId.toString())) team.addEntry(it.uniqueId.toString())
                }

                startLoc.world.playSound(startLoc, Sound.ENTITY_WITHER_SPAWN, 5f, 0.5f)
                startLoc.world.playSound(startLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 5f, 0.5f)
                Bukkit.broadcast(plugin.mm.deserialize("<gradient:#aa00aa:#000000><bold>WITHER STORM</bold></gradient> <red>ha sido invocado."))

                iniciarIA()

            } catch (e: Exception) {
                plugin.componentLogger.error(plugin.mm.deserialize("<red>Error spawneando Wither Storm: ${e.message}</red>"))
            }
        }
    }

    private fun createPart(base: Location, mat: Material, scale: Vector3f, offset: Vector): BlockDisplay {
        val adjustX = -(scale.x / 2)
        val adjustY = -(scale.y / 2)
        val adjustZ = -(scale.z / 2)

        return base.world.spawn(base, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                Vector3f(offset.x.toFloat() + adjustX, offset.y.toFloat() + adjustY, offset.z.toFloat() + adjustZ),
                Quaternionf(),
                scale,
                Quaternionf()
            )
            bd.interpolationDuration = 3
            bd.teleportDuration = 3
            bd.isPersistent = false
            bd.isGlowing = true
            bd.glowColorOverride = Color.PURPLE
        }
    }

    private fun iniciarIA() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || parts.isEmpty() || parts[0].isDead) {
                task.cancel()
                explodeAndDie()
                return@runAtFixedRate
            }

            val pivot = currentLocation ?: return@runAtFixedRate

            if (stateTicks % 20 == 0) {
                val potentialTargets = pivot.world.getNearbyPlayers(pivot, 80.0)
                    .filter {
                        !plugin.asesinoManager.esElAsesino(it) &&
                                it.gameMode == GameMode.SURVIVAL &&
                                !plugin.isIgnored(it)
                    }
                currentTarget = potentialTargets.minByOrNull { it.location.distanceSquared(pivot) }
            }

            when (currentState) {
                State.IDLE -> {
                    hoverEffect(pivot)
                    if (currentTarget != null) changeState(State.PERSECUCION)
                }
                State.PERSECUCION -> {
                    if (currentTarget == null || !currentTarget!!.isOnline) {
                        changeState(State.IDLE)
                    } else {
                        moveTowards(currentTarget!!.location.add(0.0, 6.0, 0.0), 0.35)

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

            updatePartsPosition(pivot)
            stateTicks++

        }, 1L, 2L)
    }

    private fun updatePartsPosition(pivot: Location) {
        currentTarget?.let { target ->
            val dir = target.location.toVector().subtract(pivot.toVector()).normalize()
            val targetYaw = Math.toDegrees(kotlin.math.atan2(-dir.x, dir.z)).toFloat()

            val currentYaw = pivot.yaw
            val newYaw = moveAngle(currentYaw, targetYaw, 2.5f)
            pivot.yaw = newYaw
        }

        parts.forEach { part ->
            part.teleport(pivot)
        }
    }

    private fun processTractorBeam(pivot: Location) {
        if (stateTicks > 60) {
            changeState(State.PERSECUCION)
            return
        }

        // 🔥 FIX: Usamos Vector() para sumar
        val beamStart = pivot.clone().add(0.0, 2.0, 0.0).add(pivot.direction.multiply(2))
        val beamDir = Vector(0.0, -1.0, 0.0)

        for (i in 0..15) {
            val center = beamStart.clone().add(beamDir.clone().multiply(i.toDouble()))
            center.world.spawnParticle(Particle.DUST, center, 5, 1.5, 0.5, 1.5, 0.0, Particle.DustOptions(Color.PURPLE, 2f))
            center.world.spawnParticle(Particle.REVERSE_PORTAL, center, 2, 1.0, 0.5, 1.0, 0.0)
        }

        if (stateTicks % 5 == 0) pivot.world.playSound(pivot, Sound.BLOCK_BEACON_AMBIENT, 5f, 0.5f)

        val beamZone = beamStart.clone().subtract(0.0, 15.0, 0.0)
        val victims = beamZone.world.getNearbyEntities(beamZone, 5.0, 15.0, 5.0)
            .filterIsInstance<Player>()
            .filter { !plugin.asesinoManager.esElAsesino(it) && it.gameMode == GameMode.SURVIVAL }

        victims.forEach { p ->
            val pull = beamStart.toVector().subtract(p.location.toVector()).normalize().multiply(0.6)
            p.velocity = pull
            p.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 10, 2, false, false, false))

            if (p.location.distanceSquared(beamStart) < 9.0) {
                plugin.gameManager.combatManager.takeDamage(p)
                p.playSound(p.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
                p.sendMessage(plugin.mm.deserialize("<dark_purple><i>¡Estás siendo devorado!</i></dark_purple>"))
            }
        }
    }

    private fun processSkullRain(pivot: Location) {
        if (stateTicks > 40) {
            changeState(State.PERSECUCION)
            return
        }

        if (stateTicks % 5 == 0 && currentTarget != null) {
            val spawnLoc = if (ThreadLocalRandom.current().nextBoolean()) {
                pivot.clone().add(pivot.direction.rotateAroundY(Math.toRadians(-45.0)).multiply(4)).add(0.0, 3.0, 0.0)
            } else {
                pivot.clone().add(pivot.direction.rotateAroundY(Math.toRadians(45.0)).multiply(4)).add(0.0, 3.0, 0.0)
            }

            val skull = spawnLoc.world.spawn(spawnLoc, WitherSkull::class.java)
            val dir = currentTarget!!.location.toVector().subtract(spawnLoc.toVector()).normalize()
            skull.direction = dir
            skull.velocity = dir.multiply(1.5)
            skull.isCharged = true

            spawnLoc.world.playSound(spawnLoc, Sound.ENTITY_WITHER_SHOOT, 3f, 0.8f)
        }
    }

    private fun processRoar(pivot: Location) {
        if (stateTicks == 1) {
            pivot.world.playSound(pivot, Sound.ENTITY_WARDEN_ROAR, 10f, 0.6f)
            pivot.world.spawnParticle(Particle.SONIC_BOOM, pivot.clone().add(0.0, 3.0, 0.0), 5, 3.0, 3.0, 3.0)

            pivot.world.getNearbyPlayers(pivot, 30.0).forEach { p ->
                if (!plugin.asesinoManager.esElAsesino(p)) {
                    val push = p.location.toVector().subtract(pivot.toVector()).normalize().multiply(3.0).setY(0.5)
                    p.velocity = push
                    p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
                    p.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 120, 1))
                    p.showTitle(Title.title(plugin.mm.deserialize(" "), plugin.mm.deserialize("<dark_purple><b>¡RUUUUAAARRR!</b>")))
                }
            }
        }
        if (stateTicks > 20) changeState(State.PERSECUCION)
    }

    private fun hoverEffect(pivot: Location) {
        val yOffset = sin(System.currentTimeMillis() / 500.0) * 0.05
        pivot.add(0.0, yOffset, 0.0)
    }

    private fun moveTowards(target: Location, speed: Double) {
        val current = currentLocation!!
        val dir = target.toVector().subtract(current.toVector())
        if (dir.lengthSquared() > 1.0) {
            dir.normalize().multiply(speed)
            current.add(dir)
        }
    }

    private fun changeState(newState: State) {
        currentState = newState
        stateTicks = 0
    }

    private fun explodeAndDie() {
        val loc = parts.firstOrNull()?.location ?: return
        loc.world.createExplosion(loc, 0F, false)
        loc.world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 5f, 0.6f)

        parts.forEach {
            // 🔥 FIX: createBlockData() correcto
            it.world.spawnParticle(Particle.BLOCK_CRUMBLE, it.location, 20, 1.0, 1.0, 1.0, it.block)
            it.remove()
        }
        parts.clear()

        Bukkit.broadcast(plugin.mm.deserialize("<green>La <dark_purple>Tormenta Wither<green> ha sido destruida."))
    }

    private fun moveAngle(from: Float, to: Float, step: Float): Float {
        var dist = to - from
        while (dist > 180) dist -= 360f
        while (dist < -180) dist += 360f
        return if (kotlin.math.abs(dist) < step) to else from + Math.copySign(step, dist)
    }

    fun remove() {
        isRunning = false
    }
}
