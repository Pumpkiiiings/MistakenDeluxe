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
 * [LIRIC-MISTAKEN 2.0] - MODO PESADILLA
 * EYEDROOMS.EXE: El Ojo de los Backrooms.
 * FIX: State Machine asíncrona segura con RegionScheduler.
 */
class EyedroomsEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = false
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null
    private val teamName = "EyedroomsGlow"

    private var fase = 0
    private var ticks = 0
    private var pasos = 0

    fun spawn(startLoc: Location) {
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName).apply { color(NamedTextColor.DARK_PURPLE) }

                val whiteEye = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(3f, 3f, 3f), Vector3f(-1.5f, 0f, -1.5f))
                val pupil = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.8f, 1.8f, 0.2f), Vector3f(-0.9f, 0.6f, 1.45f))
                val glitch1 = createPart(startLoc, Material.NETHERRACK, Vector3f(0.5f, 0.5f, 0.5f), Vector3f(-2.0f, 2.0f, 0f))
                val glitch2 = createPart(startLoc, Material.NETHERRACK, Vector3f(0.5f, 0.5f, 0.5f), Vector3f(1.5f, 0.5f, 0f))

                parts.addAll(listOf(whiteEye, pupil, glitch1, glitch2))
                parts.forEach { team.addEntry(it.uniqueId.toString()) }

                Bukkit.broadcast(plugin.mm.deserialize("<newline><dark_purple><b>[!]</b> <white>EL SISTEMA HA SIDO INFECTADO POR <dark_red><b>EYEDROOMS.EXE</b>"))
                isRunning = true
                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error("Fallo al invocar al Eyedrooms: ${e.message}")
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

            if (fase == 0) {
                val players = bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0)
                    .filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }

                currentTarget = players.filter { it.uniqueId != lastVictimUUID }
                    .minByOrNull { it.location.distanceSquared(bodyLoc) }
                    ?: players.minByOrNull { it.location.distanceSquared(bodyLoc) }

                if (currentTarget != null) {
                    fase = 1
                    ticks = 0
                    pasos = 0
                }
                return@Consumer
            }

            if (target == null || !target.isOnline) {
                fase = 0
                return@Consumer
            }

            when (fase) {
                1 -> { // Acecho (3 saltos largos de 3.5m)
                    if (ticks % 14 == 0) {
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(3.5))
                        moverTodo(next, true)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 0.1f)
                        target.playSound(next, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.5f)
                        aplicarEfectosAgresivos(next, 30)

                        pasos++
                        if (pasos >= 3) {
                            fase = 2
                            ticks = 0
                        }
                    }
                }
                2 -> { // El Regalo Envenenado
                    if (ticks == 30) {
                        target.playSound(target.location, Sound.BLOCK_CONDUIT_ACTIVATE, 2f, 0.1f)
                        target.showTitle(Title.title(plugin.mm.deserialize("<dark_purple><obfuscated>ERR_VOICE"), plugin.mm.deserialize("<gray>Has recibido una <green>bendición <red>corrupta")))
                        target.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 2, false, false))
                        target.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 100, 1, false, false))
                        target.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false))
                    }
                    if (ticks >= 60) {
                        fase = 3
                        ticks = 0
                        pasos = 0
                    }
                }
                3 -> { // Modo Misil Cuántico
                    if (ticks % 2 == 0) {
                        val dir = target.location.add(0.0, 1.0, 0.0).toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(5.0))
                        moverTodo(next, false)

                        target.playSound(next, Sound.ENTITY_GHAST_SCREAM, 0.8f, 2.0f)
                        next.world.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, next, 15, 0.8, 0.8, 0.8, 0.1)

                        if (next.distanceSquared(target.location) < 3.5) {
                            ejecutarMuerte(target)
                            fase = 0
                            return@Consumer
                        }

                        pasos++
                        if (pasos >= 15) {
                            fase = 0
                        }
                    }
                }
            }
            ticks++
        }, 1L, 1L)
    }

    private fun aplicarEfectosAgresivos(loc: Location, duration: Int) {
        loc.world.getNearbyPlayers(loc, 15.0).forEach { p ->
            if (!plugin.gameManager.esAsesino(p.uniqueId)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, duration, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 2, false, false, false))
                p.playSound(p.location, Sound.BLOCK_BEEHIVE_WORK, 0.5f, 0.1f)
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

    private fun ejecutarMuerte(victim: Player) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.SONIC_BOOM, victim.location, 1)
        victim.world.playSound(victim.location, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.5f)

        repeat(5) { plugin.combatManager.takeDamage(victim) }

        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
        victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 200, 0))

        Bukkit.broadcast(plugin.mm.deserialize("<dark_purple><b>[!]</b> <white>${victim.name} fue borrado por la mirada de <dark_red>EYEDROOMS"))
    }

    fun remove() {
        isRunning = false
        parts.forEach { it.remove() }
        parts.clear()
    }
}
