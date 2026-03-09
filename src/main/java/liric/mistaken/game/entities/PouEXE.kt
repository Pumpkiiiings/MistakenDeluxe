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
import java.util.*
import java.util.function.Consumer

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TROLL
 * POU.EXE: La mascota virtual que nunca alimentaste... y ahora tiene hambre.
 * FIX: State Machine segura con GlobalRegionScheduler.
 */
class PouEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = false
    private var currentTarget: Player? = null

    private var lastVictimUUID: UUID? = null
    private var consecutiveMisses = 0

    private val teamWhite = "PouGlow"
    private val teamRed = "PouAngry"

    // Variables de IA
    private var fase = 0
    private var ticksEnFase = 0
    private var saltos = 0

    fun spawn(startLoc: Location) {
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                scoreboard.getTeam(teamWhite) ?: scoreboard.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                scoreboard.getTeam(teamRed) ?: scoreboard.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

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

                Bukkit.broadcast(plugin.mm.deserialize("<newline><yellow><b>[!]</b> <white>Se ha detectado una mascota virtual abandonada... <brown>POU.EXE"))
                isRunning = true
                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error("Fallo al invocar al Pou: ${e.message}")
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

    private fun iniciarIA() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!isRunning || parts.isEmpty() || !parts[0].isValid) {
                task.cancel()
                return@Consumer
            }

            val bodyLoc = parts[0].location
            val target = currentTarget

            // BUSCAR PRESA
            if (fase == 0) {
                val players = bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0).filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                val pot = players.filter { it.uniqueId != lastVictimUUID }.minByOrNull { it.location.distanceSquared(bodyLoc) }
                currentTarget = pot ?: players.minByOrNull { it.location.distanceSquared(bodyLoc) }

                if (currentTarget == null) return@Consumer

                if (consecutiveMisses >= 5) {
                    fase = 4 // Furia
                    ticksEnFase = 0
                } else {
                    fase = 1 // Saltos
                    saltos = 0
                    ticksEnFase = 0
                }
                return@Consumer
            }

            if (target == null || !target.isOnline) {
                fase = 0
                return@Consumer
            }

            // MÁQUINA DE ESTADOS
            when (fase) {
                1 -> { // Saltos (Cada 16 ticks = 800ms)
                    if (ticksEnFase % 16 == 0) {
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val nextLoc = bodyLoc.add(dir.multiply(2.2))
                        moverTodo(nextLoc, true)
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
                        target.showTitle(Title.title(plugin.mm.deserialize("<gradient:#8B4513:#D2B48C><b>POU TIENE HAMBRE"), plugin.mm.deserialize("<red>¡Aliméntalo con tu alma!")))
                    }
                    if (ticksEnFase >= 40) {
                        fase = 3
                        ticksEnFase = 0
                        saltos = 0 // Lo usamos para los pasos del misil
                    }
                }
                3 -> { // Misil Traga-Mundos (Cada 2 ticks = 100ms)
                    if (ticksEnFase % 2 == 0) {
                        val dir = target.location.add(0.0, 0.5, 0.0).toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(3.0))
                        moverTodo(next, false)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 0.8f)

                        val hit = next.world.getNearbyPlayers(next, 3.0).filter { !plugin.gameManager.esAsesino(it.uniqueId) }
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
                        target.sendMessage(plugin.mm.deserialize("<dark_red><b>[!] POU SE HA VUELTO SALVAJE"))
                    }

                    if (ticksEnFase > 20 && ticksEnFase < 120) { // Ataque por 5s máx
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(1.5))
                        moverTodo(next, true)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 0.8f, 2.0f)

                        if (next.distanceSquared(target.location) < 2.5) {
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
        repeat(5) { plugin.combatManager.takeDamage(victim) }

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 4))

        val prefix = if (enrage) "<dark_red><b>[HAMBRE]</b>" else "<brown><b>[!]</b>"
        Bukkit.broadcast(plugin.mm.deserialize("$prefix <white>${victim.name} fue devorado por <brown>POU.EXE"))
    }

    private fun aplicarAura(loc: Location, time: Int) {
        loc.world.getNearbyPlayers(loc, 10.0).forEach { p ->
            if (!plugin.gameManager.esAsesino(p.uniqueId)) {
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

    fun remove() {
        isRunning = false
        parts.forEach { it.remove() }
        parts.clear()
    }
}
