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
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class AmongUsEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = false
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null
    private var consecutiveMisses = 0
    private var assignedSession: GameSession? = null

    private val teamNormal = "SusNormal"
    private val teamAngry = "SusAngry"

    private var fase = 0
    private var ticksEnFase = 0
    private var saltos = 0

    fun spawn(startLoc: Location) {
        val sessionManager = plugin.sessionManager
        if (sessionManager != null) {
            assignedSession = sessionManager.activeSessions.values.find {
                it.currentMapName != "Esperando..." && it.getPlayers().any { p -> p.world == startLoc.world }
            }
        }

        plugin.server.regionScheduler.run(plugin, startLoc, { _ ->
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                if (scoreboard.getTeam(teamNormal) == null) scoreboard.registerNewTeam(teamNormal).apply { color(NamedTextColor.WHITE) }
                if (scoreboard.getTeam(teamAngry) == null) scoreboard.registerNewTeam(teamAngry).apply { color(NamedTextColor.RED) }

                val body = createPart(startLoc, Material.RED_CONCRETE, Vector3f(1.2f, 1.8f, 1.0f), Vector3f(-0.6f, 0.4f, -0.5f))
                val visor = createPart(startLoc, Material.LIGHT_BLUE_CONCRETE, Vector3f(0.9f, 0.5f, 0.2f), Vector3f(-0.45f, 1.3f, 0.45f))
                val backpack = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.8f, 1.2f, 0.4f), Vector3f(-0.4f, 0.6f, -0.85f))
                val legL = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.5f, 0.4f), Vector3f(-0.5f, 0f, -0.2f))
                val legR = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.5f, 0.4f), Vector3f(0.1f, 0f, -0.2f))

                parts.addAll(listOf(body, visor, backpack, legL, legR))
                setGlowColor(NamedTextColor.WHITE)

                val msg = plugin.mm.deserialize("<red><b>[!]</b> <white>Hay un <b>IMPOSTOR</b> entre nosotros...")
                assignedSession?.broadcastLocalized("events.amongus-spawn") ?: plugin.server.onlinePlayers.forEach { it.sendMessage(msg) }

                isRunning = true
                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error("Fallo al invocar al AmongUs: ${e.message}")
            }
        })
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
        // En Folia, el movimiento de entidades debe hacerse desde el scheduler global (si atraviesa chunks)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!isRunning || parts.isEmpty() || !parts[0].isValid) {
                task.cancel()
                return@Consumer
            }

            val bodyLoc = parts[0].location
            val session = assignedSession

            if (fase == 0) {
                val potentialTargets = if (session != null) {
                    session.getPlayers().filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                } else {
                    plugin.server.onlinePlayers.filter { it.world == bodyLoc.world && it.location.distanceSquared(bodyLoc) < 10000.0 && it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                }

                val pot = potentialTargets.filter { it.uniqueId != lastVictimUUID }.minByOrNull { it.location.distanceSquared(bodyLoc) }
                currentTarget = pot ?: potentialTargets.minByOrNull { it.location.distanceSquared(bodyLoc) }

                if (currentTarget == null) return@Consumer

                if (consecutiveMisses >= 5) {
                    fase = 4
                    ticksEnFase = 0
                } else {
                    fase = 1
                    saltos = 0
                    ticksEnFase = 0
                }
                return@Consumer
            }

            val target = currentTarget
            val sm = plugin.sessionManager
            if (target == null || !target.isOnline || (session != null && sm?.getSession(target) != session)) {
                fase = 0
                currentTarget = null
                return@Consumer
            }

            when (fase) {
                1 -> {
                    if (ticksEnFase % 14 == 0) {
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val nextLoc = bodyLoc.add(dir.multiply(1.8))
                        moverTodo(nextLoc, true)

                        target.scheduler.run(plugin, { _ ->
                            target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f)
                        }, null)

                        aplicarAura(nextLoc, 20)
                        saltos++

                        if (saltos >= 6) { fase = 2; ticksEnFase = 0 }
                    }
                }
                2 -> {
                    if (ticksEnFase == 16) {
                        target.scheduler.run(plugin, { _ ->
                            target.playSound(target.location, Sound.ENTITY_CREEPER_PRIMED, 1f, 0.5f)
                            target.showTitle(Title.title(plugin.mm.deserialize("<red><b>IMPOSTOR DETECTADO"), plugin.mm.deserialize("<gray>¡Corre por tu vida!")))
                        }, null)
                    }
                    if (ticksEnFase >= 36) { fase = 3; ticksEnFase = 0; saltos = 0 }
                }
                3 -> {
                    if (ticksEnFase % 2 == 0) {
                        val dir = target.location.add(0.0, 0.8, 0.0).toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(2.5))
                        moverTodo(next, false)

                        target.scheduler.run(plugin, { _ -> target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f) }, null)

                        val hit = plugin.server.onlinePlayers.filter {
                            it.world == next.world && it.location.distanceSquared(next) <= 6.25 &&
                                    sm?.getSession(it) == session && sm?.getSession(it)?.esAsesino(it.uniqueId) != true
                        }

                        if (hit.isNotEmpty()) {
                            hit.forEach { matar(it) }
                            consecutiveMisses = 0; fase = 0
                            return@Consumer
                        }

                        saltos++
                        if (saltos >= 15) { consecutiveMisses++; fase = 0 }
                    }
                }
                4 -> {
                    if (ticksEnFase == 0) {
                        setGlowColor(NamedTextColor.RED)
                        target.scheduler.run(plugin, { _ ->
                            target.playSound(target.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f)
                            target.sendMessage(plugin.mm.deserialize("<dark_red><b>[!] EL IMPOSTOR ESTÁ FURIOSO"))
                        }, null)
                    }

                    if (ticksEnFase in 21..119) {
                        val next = bodyLoc.add(target.location.toVector().subtract(bodyLoc.toVector()).normalize().multiply(1.2))
                        moverTodo(next, true)

                        target.scheduler.run(plugin, { _ -> target.playSound(next, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.8f) }, null)

                        if (next.distanceSquared(target.location) < 2.0) {
                            matar(target, true)
                            consecutiveMisses = 0
                            setGlowColor(NamedTextColor.WHITE)
                            fase = 0
                            return@Consumer
                        }
                    }

                    if (ticksEnFase >= 120) {
                        setGlowColor(NamedTextColor.WHITE)
                        consecutiveMisses = 0; fase = 0
                    }
                }
            }
            ticksEnFase++
        }, 1L, 1L) // 1 Tick de Paper
    }

    private fun matar(victim: Player, rage: Boolean = false) {
        lastVictimUUID = victim.uniqueId

        victim.scheduler.run(plugin, { _ ->
            victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)
            repeat(5) { plugin.combatManager?.takeDamage(victim) }

            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true))
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, false, true))

            val prefix = if (rage) "<dark_red><b>[SABOTAJE]</b>" else "<red><b>[!]</b>"
            val deathMsg = plugin.mm.deserialize("$prefix <white>${victim.name} fue eliminado por el impostor.")
            assignedSession?.getPlayers()?.forEach { it.sendMessage(deathMsg) }
        }, null)
    }

    private fun aplicarAura(loc: Location, time: Int) {
        plugin.server.onlinePlayers.filter { it.world == loc.world && it.location.distanceSquared(loc) <= 64.0 }.forEach { p ->
            val sm = plugin.sessionManager
            if (sm?.getSession(p) == assignedSession && sm?.getSession(p)?.esAsesino(p.uniqueId) != true) {
                p.scheduler.run(plugin, { _ ->
                    p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, time, 0, false, false, false))
                    p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, time, 1, false, false, false))
                }, null)
            }
        }
    }

    private fun moverTodo(loc: Location, look: Boolean) {
        if (parts.isEmpty() || !parts[0].isValid) return
        val newL = loc.clone()
        if (look && currentTarget != null) newL.setDirection(currentTarget!!.location.toVector().subtract(loc.toVector()))

        // Folia: Para BlockDisplays creados via API, teleport asíncrono es legal si no tienen AI.
        parts.forEach { it.teleport(newL) }
    }

    private fun setGlowColor(color: NamedTextColor) {
        val sb = Bukkit.getScoreboardManager().mainScoreboard
        val team = sb.getTeam(if (color == NamedTextColor.RED) teamAngry else teamNormal) ?: return
        parts.forEach { team.addEntry(it.uniqueId.toString()) }
    }

    fun remove() { isRunning = false; parts.forEach { it.remove() }; parts.clear() }
}
