package liric.mistaken.game.entities

import liric.mistaken.Mistaken
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
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 *[LIRIC-MISTAKEN 2.0] - MODO TROLL SUPREMO
 * GEOFFREY 3.0: EVIL SCARY EDITION.
 * TamaÃ±o MASIVO (x2), Manos hacia adelante, Sin collar, Terror puro.
 * TRACKING EN TIEMPO REAL: Cambia al objetivo mÃ¡s cercano dinÃ¡micamente (TODOS SON PRESA).
 */
class GeoffreyEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = true
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null

    private val teamWhite = "GeoffreyGlow"
    private val teamRed = "GeoffreyAngry"
    private var consecutiveMisses = 0

    // Constantes para la mÃ¡quina de estados
    private enum class State { BUSCANDO, SALTANDO, MISIL, AEREO, FURIA }
    private var currentState = State.BUSCANDO

    fun spawn(startLoc: Location) {
        plugin.server.globalRegionScheduler.run(plugin) {
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                val white = scoreboard.getTeam(teamWhite) ?: scoreboard.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                val red = scoreboard.getTeam(teamRed) ?: scoreboard.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                // =========================================================
                // ðŸ”¥ DISEÃ‘O "EVIL SCARY" (ESCALA x2, MÃS ATERRADOR)
                // =========================================================

                val core1 = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(5.2f, 5.2f, 5.2f), Vector3f(-2.6f, 0f, -2.6f))
                val core2 = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(5.6f, 4.4f, 4.8f), Vector3f(-2.8f, 0.4f, -2.4f))
                val core3 = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(4.4f, 5.6f, 4.8f), Vector3f(-2.2f, -0.2f, -2.4f))

                val leftEye = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(1.0f, 0.3f, 0.2f), Vector3f(-1.6f, 3.6f, 2.7f), rotZ = 0.2f)
                val rightEye = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(1.0f, 0.3f, 0.2f), Vector3f(0.6f, 3.8f, 2.7f), rotZ = -0.2f)

                val mouthCenter = createPart(startLoc, Material.RED_CONCRETE, Vector3f(2.8f, 0.4f, 0.2f), Vector3f(-1.4f, 1.6f, 2.7f))
                val mouthL = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.8f, 0.2f), Vector3f(-1.6f, 1.6f, 2.7f), rotZ = 0.3f)
                val mouthR = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.8f, 0.2f), Vector3f(1.2f, 1.8f, 2.7f), rotZ = -0.3f)
                val teeth = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(2.0f, 0.16f, 0.22f), Vector3f(-1.0f, 1.7f, 2.72f))

                val palmL = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.6f, 1.6f, 0.8f), Vector3f(-6.0f, 1.2f, 1.6f))
                val f1L = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.4f, 0.3f), Vector3f(-6.4f, 2.6f, 2.0f), rotZ = 0.4f, rotX = 0.6f)
                val f2L = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.8f, 0.3f), Vector3f(-5.6f, 2.8f, 2.0f), rotZ = 0.1f, rotX = 0.6f)
                val f3L = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.4f, 0.3f), Vector3f(-4.8f, 2.6f, 2.0f), rotZ = -0.2f, rotX = 0.6f)
                val thumbL = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 1.6f, 0.3f), Vector3f(-4.0f, 1.2f, 2.0f), rotZ = -0.8f, rotX = 0.4f)

                val palmR = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.6f, 1.6f, 0.8f), Vector3f(4.4f, 1.2f, 1.6f))
                val f1R = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.4f, 0.3f), Vector3f(4.4f, 2.6f, 2.0f), rotZ = 0.2f, rotX = 0.6f)
                val f2R = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.8f, 0.3f), Vector3f(5.2f, 2.8f, 2.0f), rotZ = -0.1f, rotX = 0.6f)
                val f3R = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 2.4f, 0.3f), Vector3f(6.0f, 2.6f, 2.0f), rotZ = -0.4f, rotX = 0.6f)
                val thumbR = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.3f, 1.6f, 0.3f), Vector3f(3.6f, 1.2f, 2.0f), rotZ = 0.8f, rotX = 0.4f)

                parts.addAll(listOf(
                    core1, core2, core3,
                    leftEye, rightEye,
                    mouthCenter, mouthL, mouthR, teeth,
                    palmL, f1L, f2L, f3L, thumbL,
                    palmR, f1R, f2R, f3R, thumbR
                ))

                setGlowColor(NamedTextColor.WHITE)
                Bukkit.broadcast(plugin.mm.deserialize("<red><b>[!]</b> <dark_red>ANOMALÃA DETECTADA: <b>EL ABISMO HA DESPERTADO.</b>"))

                iniciarIANativa()
            } catch (e: Exception) {
                plugin.componentLogger.error(plugin.mm.deserialize("[ERROR] [Entity] Failed to invoke dark entity: ${e.message}"))
            }
        }
    }

    private fun createPart(loc: Location, mat: Material, scale: Vector3f, translation: Vector3f, rotZ: Float = 0f, rotX: Float = 0f): BlockDisplay {
        return liric.mistaken.packet.PacketFactory.displays.buildBlockDisplay(org.bukkit.Bukkit.getOnlinePlayers().toList(), loc) { bd ->
            bd.block = mat.createBlockData()
            val leftRotation = Quaternionf().rotateX(rotX).rotateZ(rotZ)
            bd.transformation = Transformation(translation, leftRotation, scale, Quaternionf())
            bd.isPersistent = false
            bd.interpolationDuration = 1
            bd.teleportDuration = 1
            bd.brightness = Display.Brightness(15, 15)
            bd.isGlowing = true
        }
    }

    private fun setGlowColor(color: NamedTextColor) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val targetTeam = if (color == NamedTextColor.RED) teamRed else teamWhite
        val otherTeam = if (color == NamedTextColor.RED) teamWhite else teamRed
        val team = scoreboard.getTeam(targetTeam) ?: return
        val oldTeam = scoreboard.getTeam(otherTeam)

        parts.forEach {
            val id = it.uniqueId.toString()
            oldTeam?.removeEntry(id)
            if (!team.hasEntry(id)) team.addEntry(id)
        }
    }

    // ðŸ”¥ FIX: Ahora persigue a TODOS los jugadores vivos, sin importar si son el Killer o no.
    private fun getClosestTarget(): Player? {
        if (parts.isEmpty()) return null
        val bodyLoc = parts[0].location
        val nearbyPlayers = bodyLoc.world.getNearbyPlayers(bodyLoc, 150.0)
            .filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }

        return nearbyPlayers.filter { it.uniqueId != lastVictimUUID }
            .minByOrNull { it.location.distanceSquared(bodyLoc) }
            ?: nearbyPlayers.minByOrNull { it.location.distanceSquared(bodyLoc) }
    }

    private fun iniciarIANativa() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || parts.isEmpty() || !parts[0].isValid) {
                task.cancel()
                return@runAtFixedRate
            }

            val target = getClosestTarget()
            if (target == null) {
                explodeAndRemove()
                task.cancel()
                return@runAtFixedRate
            }

            if (currentState == State.BUSCANDO) {
                if (consecutiveMisses >= 5) {
                    currentState = State.FURIA
                    ejecutarModoFuria()
                } else {
                    currentState = State.SALTANDO
                    ejecutarSaltos()
                }
            }
        }, 1L, 20L)
    }

    private fun ejecutarSaltos() {
        var saltos = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            val target = getClosestTarget()

            if (!isRunning || target == null || saltos >= 5) {
                task.cancel()
                if (isRunning && target != null) {
                    val isAereo = ThreadLocalRandom.current().nextInt(100) < 30
                    if (isAereo) {
                        currentState = State.AEREO
                        ejecutarAtaqueAereo()
                    } else {
                        currentState = State.MISIL
                        ejecutarAtaqueMisil()
                    }
                } else {
                    currentState = State.BUSCANDO
                }
                return@runAtFixedRate
            }

            val bodyLoc = parts[0].location
            val nextLoc = bodyLoc.add(target.location.toVector().subtract(bodyLoc.toVector()).normalize().multiply(3.0))
            moverTodo(nextLoc, target.location)
            aplicarAuraMiedo(nextLoc, 20)
            target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.8f)

            saltos++
        }, 1L, 16L)
    }

    private fun ejecutarAtaqueMisil() {
        val initialTarget = getClosestTarget() ?: return
        initialTarget.playSound(initialTarget.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f)
        initialTarget.showTitle(Title.title(
            plugin.mm.deserialize("<dark_red><bold>!!! VIENE !!!"),
            plugin.mm.deserialize("<red>Â¡Â¡Â¡Â¡corre!!!!")
        ))

        plugin.server.globalRegionScheduler.runDelayed(plugin, {
            var step = 0
            var hitAny = false

            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
                val target = getClosestTarget()
                if (!isRunning || hitAny || step >= 12 || target == null) {
                    task.cancel()
                    if (hitAny) consecutiveMisses = 0 else consecutiveMisses++
                    currentState = State.BUSCANDO
                    return@runAtFixedRate
                }

                val current = parts[0].location
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.clone().multiply(4.0))

                moverTodo(nextLoc, target.location)

                aplicarAuraMiedo(nextLoc, 40)
                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.3f)
                nextLoc.world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, nextLoc, 10, 0.5, 0.5, 0.5, 0.1)

                // ðŸ”¥ FIX: TambiÃ©n daÃ±a a cualquiera en Survival (incluyendo asesinos)
                val victims = nextLoc.world.getNearbyPlayers(nextLoc, 2.0).filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                if (victims.isNotEmpty()) {
                    victims.forEach { ejecutarMuerte(it) }
                    hitAny = true
                }

                step++
            }, 1L, 2L)

        }, 24L)
    }

    private fun ejecutarAtaqueAereo() {
        var subidas = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { taskSubida ->
            if (!isRunning || subidas >= 12) {
                taskSubida.cancel()

                plugin.server.globalRegionScheduler.runDelayed(plugin, {
                    var step = 0
                    var hitAny = false

                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { taskBajada ->
                        val target = getClosestTarget()
                        if (!isRunning || hitAny || step >= 30 || target == null) {
                            taskBajada.cancel()

                            // ðŸ”¥ FIX: Eliminamos el cÃ³digo que reseteaba la rotaciÃ³n a 0 y daÃ±aba la pose original.

                            if (hitAny) consecutiveMisses = 0 else consecutiveMisses++
                            currentState = State.BUSCANDO
                            return@runAtFixedRate
                        }

                        val current = parts[0].location
                        val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                        val nextLoc = current.add(dir.clone().multiply(2.0))

                        // ðŸ”¥ FIX: Eliminamos la rotaciÃ³n continua que deformaba los bloques.
                        // Ahora caerÃ¡ firme y recto hacia el jugador, viÃ©ndose mucho mÃ¡s estable e intimidante.

                        moverTodo(nextLoc, target.location)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)

                        val victims = nextLoc.world.getNearbyPlayers(nextLoc, 2.0).filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                        if (victims.isNotEmpty()) {
                            victims.forEach { ejecutarMuerte(it) }
                            hitAny = true
                        }
                        step++
                    }, 1L, 1L)

                }, 10L)
                return@runAtFixedRate
            }

            val next = parts[0].location.add(0.0, 1.5, 0.0)
            val target = getClosestTarget()

            moverTodo(next, target?.location ?: next)
            target?.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 2f)
            subidas++

        }, 1L, 2L)
    }

    private fun ejecutarModoFuria() {
        setGlowColor(NamedTextColor.RED)

        if (parts.size > 4) {
            parts[3].block = Material.RED_CONCRETE.createBlockData()
            parts[4].block = Material.RED_CONCRETE.createBlockData()
        }

        Bukkit.broadcast(plugin.mm.deserialize("<dark_red><bold>!!! GEOFFREY ESTÃ FURIOSO !!!"))

        var hasHit = false
        var ticks = 0

        plugin.server.globalRegionScheduler.runDelayed(plugin, {
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
                val target = getClosestTarget()

                if (!isRunning || hasHit || ticks >= 100 || target == null) {
                    task.cancel()
                    setGlowColor(NamedTextColor.WHITE)

                    if (parts.size > 4) {
                        parts[3].block = Material.WHITE_CONCRETE.createBlockData()
                        parts[4].block = Material.WHITE_CONCRETE.createBlockData()
                    }

                    plugin.server.globalRegionScheduler.runDelayed(plugin, {
                        currentState = State.BUSCANDO
                    }, if (hasHit) 100L else 20L)

                    return@runAtFixedRate
                }

                val current = parts[0].location
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(1.8))

                moverTodo(nextLoc, target.location)
                aplicarAuraMiedo(nextLoc, 40)
                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f)

                if (nextLoc.distanceSquared(target.location) < 6.50) {
                    ejecutarMuerte(target, enrage = true)
                    hasHit = true
                }
                ticks++
            }, 1L, 1L)
        }, 20L)
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, victim.location, 2)
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)

        // CuÃ¡nto daÃ±o harÃ¡ el ataque (14.0 = 7 corazones).
        // Si estÃ¡ enfurecido, hace mÃ¡s daÃ±o (20.0 = 10 corazones / Instakill si no tiene armor)
        val damageAmount = if (enrage) 20.0 else 7.0

        val nuevaVida = victim.health - damageAmount

        if (nuevaVida <= 0) {
            // AQUÃ SÃ MUERE
            victim.health = 0.0
            val prefix = if (enrage) "<dark_red><b>[FURIA]</b>" else "<red><b>[!]</b>"
            Bukkit.broadcast(plugin.mm.deserialize("$prefix <white>${victim.name} fue destrozado por Geoffrey."))
        } else {
            // SOBREVIVE AL GOLPE, PERO QUEDA GRAVEMENTE HERIDO Y EMPUJADO
            victim.health = nuevaVida

            // Empuje (Knockback) fuerte para alejarlo y que tenga oportunidad de huir
            if (parts.isNotEmpty()) {
                val empuje = victim.location.toVector().subtract(parts[0].location.toVector()).normalize().multiply(2.0).setY(0.8)
                victim.velocity = empuje
            }

            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true))
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false, true))

            victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 0.5f)
        }
    }

    private fun aplicarAuraMiedo(loc: Location, duration: Int) {
        // ðŸ”¥ FIX: TambiÃ©n le da oscuridad al asesino si estÃ¡ cerca
        loc.world.getNearbyPlayers(loc, 15.0).forEach { p ->
            if (p.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(p)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, false, false))
            }
        }
    }

    private fun moverTodo(baseLoc: Location, lookAtTargetLoc: Location?) {
        if (parts.isEmpty() || !parts[0].isValid) return
        val newLoc = baseLoc.clone()
        if (lookAtTargetLoc != null) {
            val dir = lookAtTargetLoc.toVector().subtract(baseLoc.toVector())
            // ðŸ”¥ FIX: Anulamos el eje Y para que rote solo en horizontal (Yaw) y no se desarme (Pitch)
            dir.y = 0.0
            if (dir.lengthSquared() > 0.001) { // Evita errores matemÃ¡ticos si el jugador estÃ¡ exactamente dentro
                newLoc.direction = dir
            }
        }
        parts.forEach { it.teleport(newLoc) }
    }

    private fun explodeAndRemove() {
        if (parts.isNotEmpty() && parts[0].isValid) {
            val loc = parts[0].location
            loc.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc, 5)
            loc.world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f)
        }
        remove()
    }

    fun remove() {
        isRunning = false
        parts.forEach { if (it.isValid) it.remove() }
        parts.clear()
    }
}

