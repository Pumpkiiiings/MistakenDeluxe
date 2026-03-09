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
 *[LIRIC-MISTAKEN 2.0] - MODO TROLL
 * AMONGUS.EXE: El Impostor de Concreto.
 * FIX: State Machine nativa en lugar de while(true) con delays.
 */
class AmongUsEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private var isRunning = false
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null
    private var consecutiveMisses = 0

    private val teamNormal = "SusNormal"
    private val teamAngry = "SusAngry"

    // Variables de Máquina de Estado
    private var fase = 0
    private var ticksEnFase = 0
    private var saltos = 0

    fun spawn(startLoc: Location) {
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                scoreboard.getTeam(teamNormal) ?: scoreboard.registerNewTeam(teamNormal).apply { color(NamedTextColor.WHITE) }
                scoreboard.getTeam(teamAngry) ?: scoreboard.registerNewTeam(teamAngry).apply { color(NamedTextColor.RED) }

                val body = createPart(startLoc, Material.RED_CONCRETE, Vector3f(1.2f, 1.8f, 1.0f), Vector3f(-0.6f, 0.4f, -0.5f))
                val visor = createPart(startLoc, Material.LIGHT_BLUE_CONCRETE, Vector3f(0.9f, 0.5f, 0.2f), Vector3f(-0.45f, 1.3f, 0.45f))
                val backpack = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.8f, 1.2f, 0.4f), Vector3f(-0.4f, 0.6f, -0.85f))
                val legL = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.5f, 0.4f), Vector3f(-0.5f, 0f, -0.2f))
                val legR = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.5f, 0.4f), Vector3f(0.1f, 0f, -0.2f))

                parts.addAll(listOf(body, visor, backpack, legL, legR))
                setGlowColor(NamedTextColor.WHITE)

                plugin.gameManager.broadcastLocalized("events.amongus-spawn")
                Bukkit.broadcast(plugin.mm.deserialize("<red><b>[!]</b> <white>Hay un <b>IMPOSTOR</b> entre nosotros..."))

                isRunning = true
                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error("Fallo al invocar al AmongUs: ${e.message}")
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

                if (currentTarget == null) {
                    // Espera pasiva (1 segundo)
                    return@Consumer
                }

                if (consecutiveMisses >= 5) {
                    fase = 4 // Furia
                    ticksEnFase = 0
                } else {
                    fase = 1 // Caminado Sus
                    saltos = 0
                    ticksEnFase = 0
                }
                return@Consumer
            }

            // PERDIÓ AL TARGET
            if (target == null || !target.isOnline) {
                fase = 0
                return@Consumer
            }

            // MÁQUINA DE ESTADOS
            when (fase) {
                1 -> { // FASE 1: Caminado SUS (Cada 14 ticks = 700ms)
                    if (ticksEnFase % 14 == 0) {
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val nextLoc = bodyLoc.add(dir.multiply(1.8))
                        moverTodo(nextLoc, true)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f)
                        aplicarAura(nextLoc, 20)
                        saltos++

                        if (saltos >= 6) {
                            fase = 2
                            ticksEnFase = 0
                            return@Consumer
                        }
                    }
                }
                2 -> { // FASE 2: Advertencia (Delay de 20 ticks)
                    if (ticksEnFase == 16) {
                        target.playSound(target.location, Sound.ENTITY_CREEPER_PRIMED, 1f, 0.5f)
                        target.showTitle(Title.title(plugin.mm.deserialize("<red><b>IMPOSTOR DETECTADO"), plugin.mm.deserialize("<gray>¡Corre por tu vida!")))
                    }
                    if (ticksEnFase >= 36) {
                        fase = 3
                        ticksEnFase = 0
                        saltos = 0 // Usamos saltos como contador del misil
                    }
                }
                3 -> { // FASE 3: MISIL (Cada 2 ticks = 100ms)
                    if (ticksEnFase % 2 == 0) {
                        val dir = target.location.add(0.0, 0.8, 0.0).toVector().subtract(bodyLoc.toVector()).normalize()
                        val next = bodyLoc.add(dir.multiply(2.5))
                        moverTodo(next, false)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)

                        val hit = next.world.getNearbyPlayers(next, 2.5).filter { !plugin.gameManager.esAsesino(it.uniqueId) }
                        if (hit.isNotEmpty()) {
                            hit.forEach { matar(it) }
                            consecutiveMisses = 0
                            fase = 0 // Reset
                            return@Consumer
                        }

                        saltos++
                        if (saltos >= 15) {
                            consecutiveMisses++
                            fase = 0 // Reset fallido
                            return@Consumer
                        }
                    }
                }
                4 -> { // MODO FURIA (Tick a tick constante)
                    if (ticksEnFase == 0) {
                        setGlowColor(NamedTextColor.RED)
                        target.playSound(target.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f)
                        target.sendMessage(plugin.mm.deserialize("<dark_red><b>[!] EL IMPOSTOR ESTÁ FURIOSO"))
                    }

                    if (ticksEnFase > 20 && ticksEnFase < 120) { // 5 segundos max
                        val next = bodyLoc.add(target.location.toVector().subtract(bodyLoc.toVector()).normalize().multiply(1.2))
                        moverTodo(next, true)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.8f)

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
                        consecutiveMisses = 0
                        fase = 0
                    }
                }
            }
            ticksEnFase++
        }, 1L, 1L)
    }

    private fun matar(victim: Player, rage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)
        repeat(5) { plugin.combatManager.takeDamage(victim) }
        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, false, true))

        val msg = if (rage) "<dark_red><b>[SABOTAJE]</b>" else "<red><b>[!]</b>"
        Bukkit.broadcast(plugin.mm.deserialize("$msg <white>${victim.name} fue eliminado por el impostor."))
    }

    private fun aplicarAura(loc: Location, time: Int) {
        loc.world.getNearbyPlayers(loc, 8.0).forEach { p ->
            if (!plugin.gameManager.esAsesino(p.uniqueId)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, time, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, time, 1, false, false, false))
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
        val team = sb.getTeam(if (color == NamedTextColor.RED) teamAngry else teamNormal) ?: return
        parts.forEach { team.addEntry(it.uniqueId.toString()) }
    }

    fun remove() { isRunning = false; parts.forEach { it.remove() }; parts.clear() }
}
