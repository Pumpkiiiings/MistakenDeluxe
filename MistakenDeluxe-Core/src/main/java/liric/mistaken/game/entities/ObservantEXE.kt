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
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TROLL SUPREMO
 * OBSERVANT 4.0: EL HERMANO MAYOR.
 * ADAPTADO: Multiarena/Velocity con tracking dinÃ¡mico y aislamiento de sesiÃ³n.
 */
class ObservantEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = true
    private var lastVictimUUID: UUID? = null

    // ðŸ”¥ Referencia a la sesiÃ³n a la que pertenece esta entidad
    private var assignedSession: GameSession? = null

    private val teamWhite = "ObsGlow"
    private val teamRed = "ObsAngry"
    private var consecutiveMisses = 0

    private enum class State { BUSCANDO, ACECHANDO, AEREO_DOBLE, AGARRE, FURIA }
    private var currentState = State.BUSCANDO

    fun spawn(startLoc: Location) {
        // ðŸ”¥ Detectamos la sesiÃ³n basada en el mundo del spawn
        assignedSession = plugin.sessionManager.activeSessions.values.find {
            it.currentMapName != "Esperando..." && it.getPlayers().any { p -> p.world == startLoc.world }
        }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                if (scoreboard.getTeam(teamWhite) == null) scoreboard.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                if (scoreboard.getTeam(teamRed) == null) scoreboard.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                // --- CONSTRUCCIÃ“N MASIVA (Scale 3.5) ---
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3.5f, 3.5f, 3.5f), Vector3f(-1.75f, 0f, -1.75f)))
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3.8f, 3.0f, 3.0f), Vector3f(-1.9f, 0.25f, -1.5f)))
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3.0f, 3.8f, 3.0f), Vector3f(-1.5f, -0.15f, -1.5f)))

                // Rostro y Manos (Se mantienen iguales pero en escala masiva)
                parts.add(createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(0.8f, 0.5f, 0.1f), Vector3f(-1.3f, 2.2f, 1.76f)))
                parts.add(createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.3f, 0.3f, 0.1f), Vector3f(-1.05f, 2.3f, 1.77f)))
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.1f, 0.1f, 0.1f), Vector3f(-0.95f, 2.4f, 1.78f)))
                parts.add(createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(0.8f, 0.5f, 0.1f), Vector3f(0.5f, 2.2f, 1.76f)))
                parts.add(createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.3f, 0.3f, 0.1f), Vector3f(0.75f, 2.3f, 1.77f)))
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.1f, 0.1f, 0.1f), Vector3f(0.85f, 2.4f, 1.78f)))
                parts.add(createPart(startLoc, Material.RED_CONCRETE, Vector3f(2.2f, 0.6f, 0.1f), Vector3f(-1.1f, 0.7f, 1.76f)))
                parts.add(createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(1.8f, 0.2f, 0.1f), Vector3f(-0.9f, 0.9f, 1.77f)))

                // Manos Izquierda y Derecha
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.2f, 1.2f, 0.4f), Vector3f(-3.5f, 0.5f, 0.5f)))
                repeat(5) { i -> parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.8f, 0.2f), Vector3f(-3.5f + (i*0.3f), 0.8f, 0.7f))) }
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.2f, 1.2f, 0.4f), Vector3f(2.3f, 0.5f, 0.5f)))
                repeat(5) { i -> parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.8f, 0.2f), Vector3f(2.3f + (i*0.3f), 0.8f, 0.7f))) }

                setGlowColor(NamedTextColor.WHITE)

                // ðŸ”¥ Broadcast solo para la sesiÃ³n
                val spawnMsg = pumpking.lib.color.ColorTranslator.translate("<dark_purple><b>[!]</b> <dark_gray>EL HERMANO MAYOR HA DESPERTADO. <b>OBSERVANT</b> ESTÃ AQUÃ.</dark_gray>")
                assignedSession?.getPlayers()?.forEach { it.sendMessage(spawnMsg) }

                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error("[ERROR] [Entity] Failed to invoke Observant: ${e.message}")
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

    // ðŸ”¥ Obtener el objetivo mÃ¡s cercano dentro de la sesiÃ³n asignada
    private fun getClosestTarget(): Player? {
        val bodyLoc = if (parts.isNotEmpty()) parts[0].location else return null
        val potentialTargets = if (assignedSession != null) {
            assignedSession!!.getPlayers().filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
        } else {
            bodyLoc.world.getNearbyPlayers(bodyLoc, 150.0).filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
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

            if (target == null) {
                explodeAndRemove()
                task.cancel()
                return@Consumer
            }

            val distSq = bodyLoc.distanceSquared(target.location)

            if (currentState == State.BUSCANDO) {
                if (consecutiveMisses >= 4) {
                    currentState = State.FURIA
                    ejecutarModoFuria()
                } else if (distSq <= 900.0 && ThreadLocalRandom.current().nextInt(100) < 30) {
                    currentState = State.AGARRE
                    ejecutarAgarre()
                } else {
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        currentState = State.ACECHANDO
                        ejecutarAcechoYEmbestida()
                    } else {
                        currentState = State.AEREO_DOBLE
                        ejecutarDobleAereo()
                    }
                }
            }
        }, 1L, 20L)
    }

    private fun ejecutarAcechoYEmbestida() {
        var ticks = 0
        val initialTarget = getClosestTarget() ?: return
        initialTarget.showTitle(Title.title(
            pumpking.lib.color.ColorTranslator.translate("<dark_gray>Te estoy observando...</dark_gray>"),
            pumpking.lib.color.ColorTranslator.translate("<red>No te muevas.")
        ))

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            val target = getClosestTarget()
            if (!isRunning || target == null) {
                task.cancel(); currentState = State.BUSCANDO; return@Consumer
            }

            val current = parts[0].location
            val dir = target.location.toVector().subtract(current.toVector()).normalize()

            if (ticks < 60) {
                val nextLoc = current.add(dir.multiply(0.3))
                moverTodo(nextLoc, target.location)
                aplicarAuraMiedo(nextLoc, 40)
                if (ticks % 15 == 0) target.playSound(nextLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.5f)
            } else if (ticks == 60) {
                target.playSound(target.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.5f)
            } else if (ticks in 61..75) {
                val nextLoc = current.add(dir.multiply(3.5))
                moverTodo(nextLoc, target.location)
                target.playSound(nextLoc, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 0.5f)

                if (nextLoc.distanceSquared(target.location) < 12.25) { // Radio 3.5 bloques reales
                    ejecutarDano(target, 3)
                    target.velocity = dir.multiply(2.0).setY(0.5)
                    consecutiveMisses = 0
                    task.cancel()
                    currentState = State.BUSCANDO
                    return@Consumer
                }
            } else {
                consecutiveMisses++
                task.cancel()
                currentState = State.BUSCANDO
            }
            ticks++
        }, 1L, 1L)
    }

    private fun ejecutarDobleAereo() {
        val initialTarget = getClosestTarget() ?: return
        initialTarget.showTitle(Title.title(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(initialTarget, "anomalies.observant.title-look-up"), pumpking.lib.service.PumpkingServiceManager.messages.getComponent(initialTarget, "anomalies.observant.subtitle-look-up")))
        var step = 0
        var diveCount = 0

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            val target = getClosestTarget()
            if (!isRunning || target == null || diveCount >= 2) {
                task.cancel()
                if (diveCount >= 2) consecutiveMisses++
                currentState = State.BUSCANDO
                return@Consumer
            }

            val current = parts[0].location
            if (step < 15) {
                moverTodo(current.add(0.0, 1.5, 0.0), target.location)
                if (step == 0) target.playSound(current, Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f)
            } else if (step < 30) {
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(2.5))
                moverTodo(nextLoc, target.location)
                parts.forEach { it.transformation = it.transformation.apply { leftRotation.rotateZ(0.5f) } }

                if (nextLoc.distanceSquared(target.location) < 12.25) {
                    ejecutarDano(target, 4)
                    target.playSound(target.location, Sound.BLOCK_ANVIL_DESTROY, 1f, 0.5f)
                    consecutiveMisses = 0
                    parts.forEach { it.transformation = it.transformation.apply { leftRotation.set(0f,0f,0f,1f) } }
                    task.cancel()
                    currentState = State.BUSCANDO
                    return@Consumer
                }
            } else {
                step = -1
                diveCount++
                parts.forEach { it.transformation = it.transformation.apply { leftRotation.set(0f,0f,0f,1f) } }
            }
            step++
        }, 1L, 1L)
    }

    private fun ejecutarAgarre() {
        val initialTarget = getClosestTarget() ?: return
        initialTarget.showTitle(Title.title(
            pumpking.lib.color.ColorTranslator.translate("<dark_purple>NO ESCAPARÃS</dark_purple>"),
            pumpking.lib.color.ColorTranslator.translate("<gray>Observant te ha atrapado...</gray>")
        ))
        initialTarget.playSound(initialTarget.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.1f)

        var ticks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            val target = getClosestTarget()
            if (!isRunning || target == null || ticks >= 60) {
                task.cancel()
                currentState = State.BUSCANDO
                return@Consumer
            }

            val obsLoc = parts[0].location
            val playerLoc = target.location
            moverTodo(obsLoc, playerLoc)

            val pullDir = obsLoc.toVector().subtract(playerLoc.toVector()).normalize()
            target.velocity = pullDir.multiply(0.6)

            val dist = obsLoc.distance(playerLoc)
            for (i in 0..dist.toInt()) {
                val point = playerLoc.clone().add(pullDir.clone().multiply(i))
                point.world.spawnParticle(org.bukkit.Particle.WITCH, point.add(0.0, 1.0, 0.0), 2, 0.1, 0.1, 0.1, 0.0)
            }

            if (obsLoc.distanceSquared(playerLoc) < 12.25) {
                ejecutarMuerte(target, false)
                task.cancel()
                currentState = State.BUSCANDO
                consecutiveMisses = 0
            }
            ticks++
        }, 1L, 1L)
    }

    private fun ejecutarModoFuria() {
        setGlowColor(NamedTextColor.RED)
        if (parts.size > 4) {
            parts[3].block = Material.RED_CONCRETE.createBlockData()
            parts[6].block = Material.RED_CONCRETE.createBlockData()
        }

        val rageMsg = pumpking.lib.color.ColorTranslator.translate("<dark_red><b>[!] EL ABISMO SE HA DESBORDADO.</b>")
        assignedSession?.getPlayers()?.forEach {
            it.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(it, "anomalies.observant.rage"))
            it.playSound(it.location, Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.5f)
        }

        var ticks = 0
        var hasHit = false

        plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                val target = getClosestTarget()
                if (!isRunning || hasHit || ticks >= 140 || target == null) {
                    task.cancel()
                    setGlowColor(NamedTextColor.WHITE)
                    if (parts.size > 4) {
                        parts[3].block = Material.WHITE_CONCRETE.createBlockData()
                        parts[6].block = Material.WHITE_CONCRETE.createBlockData()
                    }
                    plugin.server.globalRegionScheduler.runDelayed(plugin, { currentState = State.BUSCANDO }, 60L)
                    return@Consumer
                }

                val current = parts[0].location
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(1.8))

                moverTodo(nextLoc, target.location)
                aplicarAuraMiedo(nextLoc, 40)
                if (ticks % 3 == 0) target.playSound(nextLoc, Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 0.5f)

                if (nextLoc.distanceSquared(target.location) < 12.25) {
                    ejecutarMuerte(target, enrage = true)
                    hasHit = true
                }
                ticks++
            }, 1L, 1L)
        }, 30L)
    }

    private fun ejecutarDano(victim: Player, amount: Int) {
        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)
        repeat(amount) { plugin.combatManager.takeDamage(victim) }
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, victim.location, 5)
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.1f)

        victim.health = 0.0 // Muerte nativa directa

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false, true))

        val prefix = if (enrage) "<dark_red><b>[FURIA DESATADA]</b>" else "<dark_purple><b>[!]</b>"
        val deathMsg = pumpking.lib.color.ColorTranslator.translate("$prefix <white>${victim.name} fue atrapado por las garras de <dark_purple>OBSERVANT 4.0</dark_purple>.")

        assignedSession?.getPlayers()?.forEach { it.sendMessage(deathMsg) }
    }

    private fun aplicarAuraMiedo(loc: Location, duration: Int) {
        loc.world.getNearbyPlayers(loc, 15.0).forEach { p ->
            val pSession = plugin.sessionManager.getSession(p)
            if (pSession == assignedSession && !plugin.asesinoManager.isKiller(p)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, false, false))
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

    private fun explodeAndRemove() {
        if (parts.isNotEmpty() && parts[0].isValid) {
            val loc = parts[0].location
            loc.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc, 8)
            loc.world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f)
        }
        remove()
    }

    fun remove() {
        isRunning = false
        parts.forEach { if (it.isValid) it.remove() }
        parts.clear()
    }
}


