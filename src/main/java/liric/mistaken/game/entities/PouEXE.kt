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
 * [LIRIC-MISTAKEN 2.0] - MODO TROLL
 * POU.EXE: La mascota virtual que nunca alimentaste... y ahora tiene hambre.
 * Diseño: Piramidal de hormigón café con ojos saltones.
 */
class PouEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = true
    private var currentTarget: Player? = null

    private var lastVictimUUID: UUID? = null
    private var consecutiveMisses = 0

    private val teamWhite = "PouGlow"
    private val teamRed = "PouAngry"

    fun spawn(startLoc: Location) {
        plugin.pluginScope.launch(plugin.bukkitDispatcher) {
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                scoreboard.getTeam(teamWhite) ?: scoreboard.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                scoreboard.getTeam(teamRed) ?: scoreboard.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                // --- INGENIERÍA DE LA PAPA (POU) ---

                // 1. BASE (Ancha)
                val base = createPart(startLoc, Material.BROWN_CONCRETE, Vector3f(2.5f, 0.8f, 2.5f), Vector3f(-1.25f, 0f, -1.25f))
                // 2. CUERPO MEDIO
                val mid = createPart(startLoc, Material.BROWN_CONCRETE, Vector3f(2.0f, 0.8f, 2.0f), Vector3f(-1.0f, 0.8f, -1.0f))
                // 3. PUNTA (Cima)
                val top = createPart(startLoc, Material.BROWN_CONCRETE, Vector3f(1.2f, 0.6f, 1.2f), Vector3f(-0.6f, 1.6f, -0.6f))

                // 4. OJOS (Blancos con pupilas negras)
                val eyeL = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(0.5f, 0.5f, 0.1f), Vector3f(-0.7f, 1.0f, 0.91f))
                val eyeR = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(0.5f, 0.5f, 0.1f), Vector3f(0.2f, 1.0f, 0.91f))
                val pupilL = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.2f, 0.11f), Vector3f(-0.55f, 1.15f, 0.92f))
                val pupilR = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(0.2f, 0.2f, 0.11f), Vector3f(0.35f, 1.15f, 0.92f))

                parts.addAll(listOf(base, mid, top, eyeL, eyeR, pupilL, pupilR))
                setGlowColor(NamedTextColor.WHITE)

                Bukkit.broadcast(plugin.mm.deserialize("<newline><yellow><b>[!]</b> <white>Se ha detectado una mascota virtual abandonada... <brown>POU.EXE"))
                iniciarIA()
            } catch (e: Exception) {
                plugin.logger.severe("Fallo al invocar al Pou: ${e.message}")
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

    private fun iniciarIA() {
        scope.launch {
            while (isRunning) {
                val target = withContext(plugin.bukkitDispatcher) {
                    val bodyLoc = parts.firstOrNull()?.location ?: return@withContext null
                    val players = bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0)
                        .filter { it.gameMode == org.bukkit.GameMode.SURVIVAL && !plugin.isIgnored(it) }

                    val pot = players.filter { it.uniqueId != lastVictimUUID }.minByOrNull { it.location.distanceSquared(bodyLoc) }
                    pot ?: players.minByOrNull { it.location.distanceSquared(bodyLoc) }
                }

                if (target == null) { delay(2000); continue }
                currentTarget = target

                // MODO FURIA (Check)
                if (consecutiveMisses >= 5) {
                    ejecutarFuria(target)
                    consecutiveMisses = 0
                    continue
                }

                // FASE 1: SALTOS DE "POU" (Hops de yunque)
                repeat(5) {
                    if (!isRunning || !target.isOnline) return@repeat
                    withContext(plugin.bukkitDispatcher) {
                        val bodyLoc = parts[0].location
                        val nextLoc = bodyLoc.add(target.location.toVector().subtract(bodyLoc.toVector()).normalize().multiply(2.2))
                        moverTodo(nextLoc, true)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f) // Sonido más agudo para Pou
                        aplicarAura(nextLoc, 20)
                    }
                    delay(800)
                }

                if (!isRunning || !target.isOnline) continue
                delay(800)

                // FASE 2: ADVERTENCIA (Sonido de "comer")
                withContext(plugin.bukkitDispatcher) {
                    target.playSound(target.location, Sound.ENTITY_GENERIC_EAT, 2f, 0.5f)
                    target.showTitle(Title.title(plugin.mm.deserialize("<gradient:#8B4513:#D2B48C><b>POU TIENE HAMBRE"), plugin.mm.deserialize("<red>¡Aliméntalo con tu alma!")))
                }
                delay(1200)

                // FASE 3: MISIL TRAGA-MUNDOS
                var hit = false
                val start = withContext(plugin.bukkitDispatcher) { parts[0].location }
                val dir = withContext(plugin.bukkitDispatcher) { target.location.add(0.0, 0.5, 0.0).toVector().subtract(start.toVector()).normalize() }

                for (i in 1..15) {
                    if (!isRunning || hit) break
                    withContext(plugin.bukkitDispatcher) {
                        val next = parts[0].location.add(dir.clone().multiply(3.0))
                        moverTodo(next, false)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 0.8f)

                        val victims = next.world.getNearbyPlayers(next, 3.0).filter { !plugin.asesinoManager.esElAsesino(it) }
                        if (victims.isNotEmpty()) {
                            victims.forEach { ejecutarMuerte(it) }
                            hit = true
                        }
                    }
                    delay(100)
                }

                if (hit) consecutiveMisses = 0 else consecutiveMisses++
            }
        }
    }

    private suspend fun ejecutarFuria(target: Player) {
        withContext(plugin.bukkitDispatcher) {
            setGlowColor(NamedTextColor.RED)
            target.playSound(target.location, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.5f, 0.1f)
            target.sendMessage(plugin.mm.deserialize("<dark_red><b>[!] POU SE HA VUELTO SALVAJE"))
        }
        delay(1000)

        var hit = false
        val startTime = System.currentTimeMillis()
        while (isRunning && !hit && (System.currentTimeMillis() - startTime) < 5000) {
            if (!target.isOnline) break
            withContext(plugin.bukkitDispatcher) {
                val next = parts[0].location.add(target.location.toVector().subtract(parts[0].location.toVector()).normalize().multiply(1.5))
                moverTodo(next, true)
                target.playSound(next, Sound.BLOCK_ANVIL_LAND, 0.8f, 2.0f)
                if (next.distanceSquared(target.location) < 2.5) {
                    ejecutarMuerte(target, true)
                    hit = true
                }
            }
            delay(50)
        }
        withContext(plugin.bukkitDispatcher) { setGlowColor(NamedTextColor.WHITE) }
        if (hit) delay(5000)
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.playSound(victim.location, Sound.ENTITY_GENERIC_EAT, 2f, 0.1f)
        repeat(5) { plugin.gameManager.combatManager.takeDamage(victim) }

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 4))

        val prefix = if (enrage) "<dark_red><b>[HAMBRE]</b>" else "<brown><b>[!]</b>"
        Bukkit.broadcast(plugin.mm.deserialize("$prefix <white>${victim.name} fue devorado por <brown>POU.EXE"))
    }

    private fun aplicarAura(loc: Location, time: Int) {
        loc.world.getNearbyPlayers(loc, 10.0).forEach { p ->
            if (!plugin.asesinoManager.esElAsesino(p)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, time, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, time, 2, false, false, false))
            }
        }
    }

    private fun moverTodo(loc: Location, look: Boolean) {
        if (parts.isEmpty() || !parts[0].isValid) return
        val newL = loc.clone()
        if (look && currentTarget != null) newL.setDirection(currentTarget!!.location.toVector().subtract(loc.toVector()))
        parts.forEach { it.teleport(newL) }
    }

    private fun setGlowColor(color: NamedTextColor) {
        val sb = Bukkit.getScoreboardManager().mainScoreboard
        val team = sb.getTeam(if (color == NamedTextColor.RED) teamRed else teamWhite) ?: return
        parts.forEach { team.addEntry(it.uniqueId.toString()) }
    }

    fun remove() { isRunning = false; parts.forEach { it.remove() }; parts.clear(); scope.cancel() }
}
