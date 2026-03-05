package liric.mistaken.game.entities

import liric.mistaken.Mistaken
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
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
 * OBSERVANT 4.0: EL HERMANO MAYOR.
 * Diseño más grande, aterrador y con mecánicas de acoso y agarre.
 */
class ObservantEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = true
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null

    private val teamWhite = "ObsGlow"
    private val teamRed = "ObsAngry"
    private var consecutiveMisses = 0

    private enum class State { BUSCANDO, ACECHANDO, AEREO_DOBLE, AGARRE, FURIA }
    private var currentState = State.BUSCANDO

    fun spawn(startLoc: Location) {
        plugin.server.globalRegionScheduler.run(plugin) {
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                val white = scoreboard.getTeam(teamWhite) ?: scoreboard.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                val red = scoreboard.getTeam(teamRed) ?: scoreboard.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                // --- CONSTRUCCIÓN DEL HERMANO MAYOR (Scale +0.5 = 3.5) ---

                // Cuerpo Base (Cubo central grande)
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3.5f, 3.5f, 3.5f), Vector3f(-1.75f, 0f, -1.75f)))
                // Cruz para hacerlo más esférico/redondo
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3.8f, 3.0f, 3.0f), Vector3f(-1.9f, 0.25f, -1.5f)))
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3.0f, 3.8f, 3.0f), Vector3f(-1.5f, -0.15f, -1.5f)))

                // --- OJOS REALISTAS ---
                // Ojo Izquierdo (Fondo Blanco, Pupila Roja, Centro Negro)
                parts.add(createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(0.8f, 0.5f, 0.1f), Vector3f(-1.3f, 2.2f, 1.76f)))
                parts.add(createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.3f, 0.3f, 0.1f), Vector3f(-1.05f, 2.3f, 1.77f)))
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.1f, 0.1f, 0.1f), Vector3f(-0.95f, 2.4f, 1.78f)))

                // Ojo Derecho
                parts.add(createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(0.8f, 0.5f, 0.1f), Vector3f(0.5f, 2.2f, 1.76f)))
                parts.add(createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.3f, 0.3f, 0.1f), Vector3f(0.75f, 2.3f, 1.77f)))
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.1f, 0.1f, 0.1f), Vector3f(0.85f, 2.4f, 1.78f)))

                // --- SONRISA MACABRA ---
                parts.add(createPart(startLoc, Material.RED_CONCRETE, Vector3f(2.2f, 0.6f, 0.1f), Vector3f(-1.1f, 0.7f, 1.76f))) // Encías
                parts.add(createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(1.8f, 0.2f, 0.1f), Vector3f(-0.9f, 0.9f, 1.77f))) // Dientes

                // --- MANOS ENORMES (ENGARRUÑADAS) ---
                // Mano Izquierda
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.2f, 1.2f, 0.4f), Vector3f(-3.5f, 0.5f, 0.5f))) // Palma
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.8f, 0.2f), Vector3f(-3.4f, -0.2f, 0.8f))) // Dedo 1
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.9f, 0.2f), Vector3f(-3.0f, -0.3f, 0.9f))) // Dedo 2
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.8f, 0.2f), Vector3f(-2.6f, -0.2f, 0.8f))) // Dedo 3
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.6f, 0.2f), Vector3f(-2.3f, 0.0f, 0.7f))) // Dedo 4 (Meñique)
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.8f, 0.2f, 0.2f), Vector3f(-2.2f, 0.8f, 0.7f))) // Pulgar

                // Mano Derecha
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.2f, 1.2f, 0.4f), Vector3f(2.3f, 0.5f, 0.5f))) // Palma
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.8f, 0.2f), Vector3f(2.4f, -0.2f, 0.8f))) // Dedo 1
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.9f, 0.2f), Vector3f(2.8f, -0.3f, 0.9f))) // Dedo 2
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.8f, 0.2f), Vector3f(3.2f, -0.2f, 0.8f))) // Dedo 3
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.6f, 0.2f), Vector3f(3.5f, 0.0f, 0.7f))) // Dedo 4
                parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.8f, 0.2f, 0.2f), Vector3f(1.4f, 0.8f, 0.7f))) // Pulgar

                setGlowColor(NamedTextColor.WHITE)
                Bukkit.broadcast(plugin.mm.deserialize("<dark_purple><b>[!]</b> <dark_gray>EL HERMANO MAYOR HA DESPERTADO. <b>OBSERVANT</b> ESTÁ AQUÍ.</dark_gray>"))

                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error(plugin.mm.deserialize("<red>Fallo al invocar a Observant: ${e.message}</red>"))
            }
        }
    }

    private fun createPart(loc: Location, mat: Material, scale: Vector3f, translation: Vector3f): BlockDisplay {
        return loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
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

    private fun iniciarIA() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || parts.isEmpty() || !parts[0].isValid) {
                task.cancel()
                return@runAtFixedRate
            }

            val bodyLoc = parts[0].location
            val nearbyPlayers = bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0)
                .filter { it.gameMode == org.bukkit.GameMode.SURVIVAL && !plugin.asesinoManager.esElAsesino(it) && !plugin.isIgnored(it) }

            currentTarget = nearbyPlayers.filter { it.uniqueId != lastVictimUUID }
                .minByOrNull { it.location.distanceSquared(bodyLoc) }
                ?: nearbyPlayers.minByOrNull { it.location.distanceSquared(bodyLoc) }

            if (currentTarget == null) {
                explodeAndRemove()
                task.cancel()
                return@runAtFixedRate
            }

            val target = currentTarget!!
            val distSq = bodyLoc.distanceSquared(target.location)

            if (currentState == State.BUSCANDO) {
                if (consecutiveMisses >= 4) {
                    currentState = State.FURIA
                    ejecutarModoFuria(target)
                } else if (distSq <= 900.0 && ThreadLocalRandom.current().nextInt(100) < 30) { // 30 bloques = 900 sq
                    currentState = State.AGARRE
                    ejecutarAgarre(target)
                } else {
                    // Decisión 50/50 entre Acecho y Aéreo
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        currentState = State.ACECHANDO
                        ejecutarAcechoYEmbestida(target)
                    } else {
                        currentState = State.AEREO_DOBLE
                        ejecutarDobleAereo(target)
                    }
                }
            }
        }, 1L, 20L)
    }

    // --- HABILIDAD 1: ACECHO Y EMBESTIDA ---
    private fun ejecutarAcechoYEmbestida(target: Player) {
        var ticks = 0
        target.showTitle(Title.title(
            plugin.mm.deserialize("<dark_gray>Te estoy observando...</dark_gray>"),
            plugin.mm.deserialize("<red>No te muevas.")
        ))

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || !target.isOnline) {
                task.cancel(); currentState = State.BUSCANDO; return@runAtFixedRate
            }

            val current = parts[0].location
            val dir = target.location.toVector().subtract(current.toVector()).normalize()

            if (ticks < 60) {
                // FASE 1: Acecho lento (3 segundos)
                val nextLoc = current.add(dir.multiply(0.3)) // Muy lento
                moverTodo(nextLoc, true)
                aplicarAuraMiedo(nextLoc, 40)

                if (ticks % 15 == 0) {
                    target.playSound(nextLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.5f)
                }
            } else if (ticks == 60) {
                // FASE 2: Grito antes de la embestida
                target.playSound(target.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.5f)
            } else if (ticks in 61..75) {
                // FASE 3: Embestida brutal
                val nextLoc = current.add(dir.multiply(3.5)) // Muy rápido
                moverTodo(nextLoc, false)
                target.playSound(nextLoc, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 0.5f)

                if (nextLoc.distanceSquared(target.location) < 9.0) { // Radio grande por sus manos
                    ejecutarDaño(target, 3) // Quita vida pero no mata instantáneamente
                    target.velocity = dir.multiply(2.0).setY(0.5) // Lo manda a volar
                    consecutiveMisses = 0
                    task.cancel()
                    currentState = State.BUSCANDO
                    return@runAtFixedRate
                }
            } else {
                consecutiveMisses++
                task.cancel()
                currentState = State.BUSCANDO
            }
            ticks++
        }, 1L, 1L)
    }

    // --- HABILIDAD 2: DOBLE ATAQUE AÉREO ---
    private fun ejecutarDobleAereo(target: Player) {
        target.showTitle(Title.title(plugin.mm.deserialize("<dark_red>MIRA ARRIBA</dark_red>"), plugin.mm.deserialize("")))
        var step = 0
        var diveCount = 0

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || !target.isOnline || diveCount >= 2) {
                task.cancel()
                if (diveCount >= 2) consecutiveMisses++ // Falló ambos
                currentState = State.BUSCANDO
                return@runAtFixedRate
            }

            val current = parts[0].location
            if (step < 15) {
                // SUBIDA
                moverTodo(current.add(0.0, 1.5, 0.0), true)
                if (step == 0) target.playSound(current, Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f)
            } else if (step < 30) {
                // PICADA
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(2.5))
                moverTodo(nextLoc, false)
                parts.forEach { it.transformation = it.transformation.apply { leftRotation.rotateZ(0.5f) } }

                if (nextLoc.distanceSquared(target.location) < 9.0) {
                    ejecutarDaño(target, 4)
                    target.playSound(target.location, Sound.BLOCK_ANVIL_DESTROY, 1f, 0.5f)
                    consecutiveMisses = 0

                    // Resetear rotación y terminar ataque si lo golpea
                    parts.forEach { it.transformation = it.transformation.apply { leftRotation.set(0f,0f,0f,1f) } }
                    task.cancel()
                    currentState = State.BUSCANDO
                    return@runAtFixedRate
                }
            } else {
                // Reiniciar para el segundo dive
                step = -1
                diveCount++
                parts.forEach { it.transformation = it.transformation.apply { leftRotation.set(0f,0f,0f,1f) } }
            }
            step++
        }, 1L, 1L)
    }

    // --- HABILIDAD 3: AGARRE TENTACULAR ---
    private fun ejecutarAgarre(target: Player) {
        target.showTitle(Title.title(
            plugin.mm.deserialize("<dark_purple>NO ESCAPARÁS</dark_purple>"),
            plugin.mm.deserialize("<gray>Observant te ha atrapado...</gray>")
        ))

        target.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.1f)

        var ticks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || !target.isOnline || ticks >= 60) {
                task.cancel()
                currentState = State.BUSCANDO
                return@runAtFixedRate
            }

            val obsLoc = parts[0].location
            val playerLoc = target.location

            // Mirar al jugador
            moverTodo(obsLoc, true)

            // Calcular vector de atracción (Del jugador HACIA la entidad)
            val pullDir = obsLoc.toVector().subtract(playerLoc.toVector()).normalize()
            target.velocity = pullDir.multiply(0.6) // Lo jala agresivamente

            // Rayo de partículas moradas (Simula la magia/brazo invisible)
            val dist = obsLoc.distance(playerLoc)
            for (i in 0..dist.toInt()) {
                val point = playerLoc.clone().add(pullDir.clone().multiply(i))
                point.world.spawnParticle(org.bukkit.Particle.WITCH, point.add(0.0, 1.0, 0.0), 2, 0.1, 0.1, 0.1, 0.0)
            }

            if (obsLoc.distanceSquared(playerLoc) < 9.0) {
                ejecutarMuerte(target, false) // Si lo atrae por completo, lo tritura
                task.cancel()
                currentState = State.BUSCANDO
                consecutiveMisses = 0
            }

            ticks++
        }, 1L, 1L)
    }

    private fun ejecutarModoFuria(target: Player) {
        setGlowColor(NamedTextColor.RED)
        target.playSound(target.location, Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.5f)
        target.showTitle(Title.title(
            plugin.mm.deserialize("<dark_red><bold>IRA DE OBSERVANT"),
            plugin.mm.deserialize("<red>NO HAY SALIDA")
        ))

        var ticks = 0
        var hasHit = false

        plugin.server.globalRegionScheduler.runDelayed(plugin, {
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
                if (!isRunning || hasHit || ticks >= 140 || !target.isOnline) { // 7 Segundos de furia
                    task.cancel()
                    setGlowColor(NamedTextColor.WHITE)
                    plugin.server.globalRegionScheduler.runDelayed(plugin, { currentState = State.BUSCANDO }, 60L)
                    return@runAtFixedRate
                }

                val current = parts[0].location
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(1.8)) // Más rápido que Geoffrey

                moverTodo(nextLoc, lookAtTarget = true)
                aplicarAuraMiedo(nextLoc, 40)

                if (ticks % 3 == 0) target.playSound(nextLoc, Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 0.5f)

                if (nextLoc.distanceSquared(target.location) < 9.0) {
                    ejecutarMuerte(target, enrage = true)
                    hasHit = true
                }
                ticks++
            }, 1L, 1L)
        }, 30L)
    }

    private fun ejecutarDaño(victim: Player, amount: Int) {
        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)
        repeat(amount) { plugin.gameManager.combatManager.takeDamage(victim) }
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, victim.location, 5)
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.1f)

        repeat(10) { plugin.gameManager.combatManager.takeDamage(victim) } // Instakill asegurado

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false, true))

        val prefix = if (enrage) "<dark_red><b>[FURIA DESATADA]</b>" else "<dark_purple><b>[!]</b>"
        Bukkit.broadcast(plugin.mm.deserialize("$prefix <white>${victim.name} fue atrapado por las garras de <dark_purple>OBSERVANT 4.0</dark_purple>."))
    }

    private fun aplicarAuraMiedo(loc: Location, duration: Int) {
        loc.world.getNearbyPlayers(loc, 15.0).forEach { p ->
            if (!plugin.asesinoManager.esElAsesino(p)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, false, false))
            }
        }
    }

    private fun moverTodo(baseLoc: Location, lookAtTarget: Boolean) {
        if (parts.isEmpty() || !parts[0].isValid) return
        val newLoc = baseLoc.clone()
        if (lookAtTarget && currentTarget != null) {
            newLoc.setDirection(currentTarget!!.location.toVector().subtract(baseLoc.toVector()))
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
