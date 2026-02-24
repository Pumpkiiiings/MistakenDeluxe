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
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TROLL SUPREMO
 * GEOFFREY 3.0: RAGE EDITION.
 * FIX: Sonidos, Daño y Efectos garantizados en modo Misil (12 pasos).
 */
class GeoffreyEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = true
    private var currentTarget: Player? = null

    private var lastVictimUUID: UUID? = null

    private val teamWhite = "GeoffreyGlow"
    private val teamRed = "GeoffreyAngry"
    private var consecutiveMisses = 0

    fun spawn(startLoc: Location) {
        plugin.pluginScope.launch(plugin.bukkitDispatcher) {
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
                iniciarIAPrincipal()
            } catch (e: Exception) {
                plugin.logger.severe("Fallo al invocar al Geoffrey 3.0: ${e.message}")
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
            bd.brightness = org.bukkit.entity.Display.Brightness(15, 15)
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

    private fun iniciarIAPrincipal() {
        scope.launch {
            while (isRunning) {
                val target = withContext(plugin.bukkitDispatcher) {
                    val bodyLoc = parts.firstOrNull()?.location ?: return@withContext null
                    val nearbyPlayers = bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0)
                        .filter { it.gameMode == org.bukkit.GameMode.SURVIVAL && !plugin.isIgnored(it) }

                    var potentialTarget = nearbyPlayers.filter { it.uniqueId != lastVictimUUID }
                        .minByOrNull { it.location.distanceSquared(bodyLoc) }

                    if (potentialTarget == null) {
                        potentialTarget = nearbyPlayers.minByOrNull { it.location.distanceSquared(bodyLoc) }
                    }
                    potentialTarget
                }

                if (target == null) {
                    withContext(plugin.bukkitDispatcher) { explodeAndRemove() }
                    break
                }
                currentTarget = target

                // --- ¿MODO FURIA? ---
                if (consecutiveMisses >= 5) {
                    ejecutarModoFuria(target)
                    consecutiveMisses = 0
                    continue
                }

                // --- FASE 1: SALTOS (2.5m) ---
                repeat(5) {
                    if (!isRunning || !target.isOnline) return@repeat
                    withContext(plugin.bukkitDispatcher) {
                        val bodyLoc = parts[0].location
                        val nextLoc = bodyLoc.add(target.location.toVector().subtract(bodyLoc.toVector()).normalize().multiply(2.5))
                        moverTodo(nextLoc, lookAtTarget = true)
                        aplicarAuraMiedo(nextLoc, 20)
                        // SONIDO DE YUNQUE SIEMPRE
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.8f)
                    }
                    delay(800)
                }

                if (!isRunning || !target.isOnline) continue
                delay(1000)

                // --- FASE 2: ATAQUE (30% Aereo / 70% Misil) ---
                val hit = if (ThreadLocalRandom.current().nextInt(100) < 30) {
                    ejecutarAtaqueAereo(target)
                } else {
                    ejecutarAtaqueMisil(target)
                }

                if (hit) consecutiveMisses = 0 else consecutiveMisses++
            }
        }
    }

    private suspend fun ejecutarModoFuria(target: Player) {
        withContext(plugin.bukkitDispatcher) {
            setGlowColor(NamedTextColor.RED)
            target.playSound(target.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f)
            target.showTitle(Title.title(
                plugin.mm.deserialize("<dark_red><bold>!!! MODO FURIA !!!"),
                plugin.mm.deserialize("<red>GEOFFREY HA PERDIDO LA PACIENCIA")
            ))
        }
        delay(1000)
        var hasHit = false
        val startTime = System.currentTimeMillis()
        while (isRunning && !hasHit && (System.currentTimeMillis() - startTime) < 5000) {
            if (!target.isOnline) break
            withContext(plugin.bukkitDispatcher) {
                val current = parts[0].location
                val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(1.3))
                moverTodo(nextLoc, lookAtTarget = true)
                aplicarAuraMiedo(nextLoc, 40)
                // YUNQUE EN FURIA
                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f)
                if (nextLoc.distanceSquared(target.location) < 2.5) {
                    ejecutarMuerte(target, enrage = true)
                    hasHit = true
                }
            }
            delay(50)
        }
        withContext(plugin.bukkitDispatcher) { setGlowColor(NamedTextColor.WHITE) }
        if (hasHit) delay(5000) else delay(1000)
    }

    private suspend fun ejecutarAtaqueMisil(target: Player): Boolean {
        var hitAny = false
        withContext(plugin.bukkitDispatcher) {
            target.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f)
            target.showTitle(Title.title(
                plugin.mm.deserialize("<dark_red><bold>GEOFFREY VIENE"),
                plugin.mm.deserialize("<red>¡¡¡¡corre!!!!")
            ))
        }
        delay(1200)

        // Fijamos la dirección al inicio del misil
        val startLoc = withContext(plugin.bukkitDispatcher) { parts[0].location }
        val dir = withContext(plugin.bukkitDispatcher) { target.location.add(0.0, 1.0, 0.0).toVector().subtract(startLoc.toVector()).normalize() }

        for (i in 1..12) {
            if (!isRunning || hitAny) break
            withContext(plugin.bukkitDispatcher) {
                val current = parts[0].location
                val nextLoc = current.add(dir.clone().multiply(4.0)) // 4 bloques por paso
                moverTodo(nextLoc, false)

                // 🔥 EFECTOS Y SONIDOS EN CADA PASO DEL MISIL
                aplicarAuraMiedo(nextLoc, 40)
                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.3f)
                nextLoc.world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, nextLoc, 10, 0.5, 0.5, 0.5, 0.1)

                // Detección de impacto (Aumentamos a 4.0 para no "brincarse" al jugador)
                val victims = nextLoc.world.getNearbyPlayers(nextLoc, 4.0).filter { !plugin.asesinoManager.esElAsesino(it) }
                if (victims.isNotEmpty()) {
                    victims.forEach { ejecutarMuerte(it) }
                    hitAny = true
                }
            }
            delay(120)
        }
        return hitAny
    }

    private suspend fun ejecutarAtaqueAereo(target: Player): Boolean {
        var hitAny = false
        withContext(plugin.bukkitDispatcher) {
            target.showTitle(Title.title(plugin.mm.deserialize("<red>ALERTA AÉREA"), plugin.mm.deserialize("<yellow>PICADO DE GEOFFREY")))
        }
        repeat(10) {
            withContext(plugin.bukkitDispatcher) {
                val next = parts[0].location.add(0.0, 1.2, 0.0)
                moverTodo(next, true)
                target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 2f)
            }
            delay(100)
        }
        delay(500)
        val start = withContext(plugin.bukkitDispatcher) { parts[0].location }
        val dir = withContext(plugin.bukkitDispatcher) { target.location.add(0.0, 1.0, 0.0).toVector().subtract(start.toVector()).normalize() }
        for (i in 1..25) {
            if (!isRunning || hitAny) break
            withContext(plugin.bukkitDispatcher) {
                val nextLoc = parts[0].location.add(dir.clone().multiply(1.5))
                parts.forEach { it.transformation = it.transformation.apply { leftRotation.rotateZ(0.4f) } }
                moverTodo(nextLoc, false)
                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)

                val victims = nextLoc.world.getNearbyPlayers(nextLoc, 3.5).filter { !plugin.asesinoManager.esElAsesino(it) }
                if (victims.isNotEmpty()) {
                    victims.forEach { ejecutarMuerte(it) }
                    hitAny = true
                }
            }
            delay(50)
        }
        withContext(plugin.bukkitDispatcher) { parts.forEach { it.transformation = it.transformation.apply { leftRotation.set(0f,0f,0f,1f) } } }
        return hitAny
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, victim.location, 3)
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)

        // --- 💀 DAÑO Y EFECTOS ---
        repeat(5) { plugin.gameManager.combatManager.takeDamage(victim) }

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 60, 1, false, false, true))

        val prefix = if (enrage) "<dark_red><b>[FURIA]</b>" else "<red><b>[!]</b>"
        Bukkit.broadcast(plugin.mm.deserialize("$prefix <white>${victim.name} fue triturado por <dark_red>GEOFFREY 3.0"))
    }

    private fun aplicarAuraMiedo(loc: Location, duration: Int) {
        loc.world.getNearbyPlayers(loc, 12.0).forEach { p ->
            if (!plugin.asesinoManager.esElAsesino(p)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 2, false, false, false))
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
        if (parts.isNotEmpty()) {
            val loc = parts[0].location
            loc.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc, 5)
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
