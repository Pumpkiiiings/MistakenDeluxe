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
 * GEOFFREY 3.0: RAGE EDITION.
 * OPTIMIZADO: Schedulers Nativos, Sin Lentitud de Aura.
 */
class GeoffreyEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = true
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null

    private val teamWhite = "GeoffreyGlow"
    private val teamRed = "GeoffreyAngry"
    private var consecutiveMisses = 0

    // Constantes para la máquina de estados
    private enum class State { BUSCANDO, SALTANDO, MISIL, AEREO, FURIA }
    private var currentState = State.BUSCANDO

    fun spawn(startLoc: Location) {
        // En Paper 1.21.4, manipular Scoreboards y Entidades DEBE ser en el hilo principal
        plugin.server.globalRegionScheduler.run(plugin) {
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                val white = scoreboard.getTeam(teamWhite) ?: scoreboard.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                val red = scoreboard.getTeam(teamRed) ?: scoreboard.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                // --- CONSTRUCCIÓN (3x3x3) ---
                val body = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(3f, 3f, 3f), Vector3f(-1.5f, 0f, -1.5f))
                val leftEye = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.5f, 0.2f, 0.1f), Vector3f(-1.0f, 1.8f, 1.51f))
                val rightEye = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.5f, 0.2f, 0.1f), Vector3f(0.5f, 1.8f, 1.51f))
                val leftHand = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.8f, 0.8f, 0.8f), Vector3f(-2.8f, 0.5f, 0.5f))
                val rightHand = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.8f, 0.8f, 0.8f), Vector3f(2.0f, 0.5f, 0.5f))

                parts.addAll(listOf(body, leftEye, rightEye, leftHand, rightHand))

                repeat(5) { i ->
                    val offset = i * 0.15f
                    parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.08f, 0.4f, 0.08f), Vector3f(-2.8f + offset, 1.3f, 1.0f)))
                    parts.add(createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.08f, 0.4f, 0.08f), Vector3f(2.0f + offset, 1.3f, 1.0f)))
                }

                setGlowColor(NamedTextColor.WHITE)
                Bukkit.broadcast(plugin.mm.deserialize("<red><b>[!]</b> <dark_red>ANOMALÍA DETECTADA: <b>GEOFFREY 3.0</b> HA DESPERTADO."))

                // Iniciamos la IA usando el scheduler nativo
                iniciarIANativa()
            } catch (e: Exception) {
                plugin.componentLogger.error(plugin.mm.deserialize("<red>Fallo al invocar al Geoffrey 3.0: ${e.message}</red>"))
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

    /**
     * 🔥 IA NATIVA (Zero Lag)
     * Reemplaza el bucle while infinito de las corrutinas por una máquina de estados
     * basada en Schedulers que no bloquea la RAM.
     */
    private fun iniciarIANativa() {
        // Tarea que corre cada 1 segundo (20 Ticks) para decidir qué hacer
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || parts.isEmpty() || !parts[0].isValid) {
                task.cancel()
                return@runAtFixedRate
            }

            // Buscar objetivo
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

            // Decisión de IA
            if (currentState == State.BUSCANDO) {
                if (consecutiveMisses >= 5) {
                    currentState = State.FURIA
                    ejecutarModoFuria(currentTarget!!)
                } else {
                    currentState = State.SALTANDO
                    ejecutarSaltos(currentTarget!!)
                }
            }

        }, 1L, 20L) // Chequea su estado cada segundo
    }

    private fun ejecutarSaltos(target: Player) {
        var saltos = 0
        // Hacemos 5 saltos, cada 16 ticks (~800ms)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || !target.isOnline || saltos >= 5) {
                task.cancel()

                // Después de los saltos, decidimos el ataque
                if (isRunning && target.isOnline) {
                    val isAereo = ThreadLocalRandom.current().nextInt(100) < 30
                    if (isAereo) {
                        currentState = State.AEREO
                        ejecutarAtaqueAereo(target)
                    } else {
                        currentState = State.MISIL
                        ejecutarAtaqueMisil(target)
                    }
                } else {
                    currentState = State.BUSCANDO
                }
                return@runAtFixedRate
            }

            val bodyLoc = parts[0].location
            val nextLoc = bodyLoc.add(target.location.toVector().subtract(bodyLoc.toVector()).normalize().multiply(2.5))
            moverTodo(nextLoc, lookAtTarget = true)
            aplicarAuraMiedo(nextLoc, 20)
            target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.8f)

            saltos++
        }, 1L, 16L) // 16 Ticks = 800ms
    }

    private fun ejecutarAtaqueMisil(target: Player) {
        target.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f)
        target.showTitle(Title.title(
            plugin.mm.deserialize("<dark_red><bold>GEOFFREY VIENE"),
            plugin.mm.deserialize("<red>¡¡¡¡corre!!!!")
        ))

        // Retraso de advertencia (1.2s = 24 Ticks)
        plugin.server.globalRegionScheduler.runDelayed(plugin, {
            if (!isRunning || !target.isOnline) {
                currentState = State.BUSCANDO
                return@runDelayed
            }

            val startLoc = parts[0].location
            val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(startLoc.toVector()).normalize()
            var step = 0
            var hitAny = false

            // Vuelo del misil: Corre extremadamente rápido (Cada 2 Ticks = 100ms)
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
                if (!isRunning || hitAny || step >= 12) {
                    task.cancel()
                    if (hitAny) consecutiveMisses = 0 else consecutiveMisses++
                    currentState = State.BUSCANDO // Volvemos a buscar tras el ataque
                    return@runAtFixedRate
                }

                val current = parts[0].location
                val nextLoc = current.add(dir.clone().multiply(4.0))
                moverTodo(nextLoc, false)

                aplicarAuraMiedo(nextLoc, 40)
                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.3f)
                nextLoc.world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, nextLoc, 10, 0.5, 0.5, 0.5, 0.1)

                val victims = nextLoc.world.getNearbyPlayers(nextLoc, 4.0).filter { !plugin.asesinoManager.esElAsesino(it) }
                if (victims.isNotEmpty()) {
                    victims.forEach { ejecutarMuerte(it) }
                    hitAny = true
                }

                step++
            }, 1L, 2L)

        }, 24L)
    }

    private fun ejecutarAtaqueAereo(target: Player) {
        target.showTitle(Title.title(plugin.mm.deserialize("<red>ALERTA AÉREA"), plugin.mm.deserialize("<yellow>PICADO DE GEOFFREY")))

        var subidas = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { taskSubida ->
            if (!isRunning || subidas >= 10) {
                taskSubida.cancel()

                // Después de subir, bajamos en picada
                plugin.server.globalRegionScheduler.runDelayed(plugin, {
                    if (!isRunning || !target.isOnline) {
                        currentState = State.BUSCANDO
                        return@runDelayed
                    }

                    val start = parts[0].location
                    val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(start.toVector()).normalize()
                    var step = 0
                    var hitAny = false

                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { taskBajada ->
                        if (!isRunning || hitAny || step >= 25) {
                            taskBajada.cancel()
                            parts.forEach { it.transformation = it.transformation.apply { leftRotation.set(0f,0f,0f,1f) } }
                            if (hitAny) consecutiveMisses = 0 else consecutiveMisses++
                            currentState = State.BUSCANDO
                            return@runAtFixedRate
                        }

                        val nextLoc = parts[0].location.add(dir.clone().multiply(1.5))
                        parts.forEach { it.transformation = it.transformation.apply { leftRotation.rotateZ(0.4f) } }
                        moverTodo(nextLoc, false)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)

                        val victims = nextLoc.world.getNearbyPlayers(nextLoc, 3.5).filter { !plugin.asesinoManager.esElAsesino(it) }
                        if (victims.isNotEmpty()) {
                            victims.forEach { ejecutarMuerte(it) }
                            hitAny = true
                        }
                        step++
                    }, 1L, 1L)

                }, 10L) // 500ms de delay

                return@runAtFixedRate
            }

            val next = parts[0].location.add(0.0, 1.2, 0.0)
            moverTodo(next, true)
            target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 2f)
            subidas++

        }, 1L, 2L) // 2 Ticks = 100ms
    }

    private fun ejecutarModoFuria(target: Player) {
        setGlowColor(NamedTextColor.RED)
        target.playSound(target.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f)
        target.showTitle(Title.title(
            plugin.mm.deserialize("<dark_red><bold>!!! MODO FURIA !!!"),
            plugin.mm.deserialize("<red>GEOFFREY HA PERDIDO LA PACIENCIA")
        ))

        var hasHit = false
        var ticks = 0

        plugin.server.globalRegionScheduler.runDelayed(plugin, {
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
                if (!isRunning || hasHit || ticks >= 100 || !target.isOnline) { // 100 Ticks = 5 Segundos max
                    task.cancel()
                    setGlowColor(NamedTextColor.WHITE)

                    // Cooldown post-furia
                    plugin.server.globalRegionScheduler.runDelayed(plugin, {
                        currentState = State.BUSCANDO
                    }, if (hasHit) 100L else 20L)

                    return@runAtFixedRate
                }

                val current = parts[0].location
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(1.3))

                moverTodo(nextLoc, lookAtTarget = true)
                aplicarAuraMiedo(nextLoc, 40)
                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f)

                if (nextLoc.distanceSquared(target.location) < 2.5) {
                    ejecutarMuerte(target, enrage = true)
                    hasHit = true
                }
                ticks++
            }, 1L, 1L) // Persigue cada Tick (Súper Rápido)
        }, 20L) // Espera 1s tras la advertencia
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, victim.location, 3)
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)

        // --- 💀 DAÑO MÚLTIPLE ---
        repeat(5) { plugin.gameManager.combatManager.takeDamage(victim) }

        // Aplicamos solo Darkness, Slowness fuerte y Weakness
        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 1, false, false, true))

        val prefix = if (enrage) "<dark_red><b>[FURIA]</b>" else "<red><b>[!]</b>"
        Bukkit.broadcast(plugin.mm.deserialize("$prefix <white>${victim.name} fue triturado por <dark_red>GEOFFREY 3.0"))
    }

    private fun aplicarAuraMiedo(loc: Location, duration: Int) {
        // 🔥 PETICIÓN APLICADA: Ahora solo da oscuridad, NO da lentitud.
        loc.world.getNearbyPlayers(loc, 12.0).forEach { p ->
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
