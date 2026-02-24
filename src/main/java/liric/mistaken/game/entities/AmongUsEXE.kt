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
 * AMONGUS.EXE: El Impostor de Concreto.
 * IA: Saltos -> Advertencia -> Misil -> Furia (Sin ataque aéreo).
 */
class AmongUsEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = true
    private var currentTarget: Player? = null

    private var lastVictimUUID: UUID? = null
    private var consecutiveMisses = 0

    private val teamNormal = "SusNormal"
    private val teamAngry = "SusAngry"

    fun spawn(startLoc: Location) {
        plugin.pluginScope.launch(plugin.bukkitDispatcher) {
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                scoreboard.getTeam(teamNormal) ?: scoreboard.registerNewTeam(teamNormal).apply { color(NamedTextColor.WHITE) }
                scoreboard.getTeam(teamAngry) ?: scoreboard.registerNewTeam(teamAngry).apply { color(NamedTextColor.RED) }

                // --- CONSTRUCCIÓN DEL TRIPULANTE (1.5x2.5 aprox) ---

                // 1. CUERPO (Rojo)
                val body = createPart(startLoc, Material.RED_CONCRETE, Vector3f(1.2f, 1.8f, 1.0f), Vector3f(-0.6f, 0.4f, -0.5f))

                // 2. VISOR (Azul Claro)
                val visor = createPart(startLoc, Material.LIGHT_BLUE_CONCRETE, Vector3f(0.9f, 0.5f, 0.2f), Vector3f(-0.45f, 1.3f, 0.45f))

                // 3. MOCHILA (Roja)
                val backpack = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.8f, 1.2f, 0.4f), Vector3f(-0.4f, 0.6f, -0.85f))

                // 4. PIERNAS (Pequeñas)
                val legL = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.5f, 0.4f), Vector3f(-0.5f, 0f, -0.2f))
                val legR = createPart(startLoc, Material.RED_CONCRETE, Vector3f(0.4f, 0.5f, 0.4f), Vector3f(0.1f, 0f, -0.2f))

                val allParts = listOf(body, visor, backpack, legL, legR)
                parts.addAll(allParts)
                setGlowColor(NamedTextColor.WHITE)

                Bukkit.broadcast(plugin.mm.deserialize("<red><b>[!]</b> <white>Hay un <b>IMPOSTOR</b> entre nosotros..."))
                iniciarIA()
            } catch (e: Exception) {
                plugin.logger.severe("Fallo al invocar al AmongUs: ${e.message}")
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
                    val nearby = bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0)
                        .filter { it.gameMode == org.bukkit.GameMode.SURVIVAL && !plugin.isIgnored(it) }

                    val pot = nearby.filter { it.uniqueId != lastVictimUUID }.minByOrNull { it.location.distanceSquared(bodyLoc) }
                    pot ?: nearby.minByOrNull { it.location.distanceSquared(bodyLoc) }
                }

                if (target == null) {
                    delay(2000); continue
                }
                currentTarget = target

                // --- MODO FURIA (Check) ---
                if (consecutiveMisses >= 5) {
                    ejecutarFuria(target)
                    consecutiveMisses = 0
                    continue
                }

                // --- FASE 1: CAMINADO "SUS" (Saltos cortos) ---
                repeat(6) {
                    if (!isRunning || !target.isOnline) return@repeat
                    withContext(plugin.bukkitDispatcher) {
                        val bodyLoc = parts[0].location
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val nextLoc = bodyLoc.add(dir.multiply(1.8))

                        moverTodo(nextLoc, true)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f)
                        aplicarAura(nextLoc, 20)
                    }
                    delay(700)
                }

                if (!isRunning || !target.isOnline) continue
                delay(800)

                // --- FASE 2: ADVERTENCIA ---
                withContext(plugin.bukkitDispatcher) {
                    target.playSound(target.location, Sound.ENTITY_CREEPER_PRIMED, 1f, 0.5f)
                    target.showTitle(Title.title(plugin.mm.deserialize("<red><b>IMPOSTOR DETECTADO"), plugin.mm.deserialize("<gray>¡Corre por tu vida!")))
                }
                delay(1000)

                // --- FASE 3: ATAQUE RECTO ---
                var hit = false
                val start = withContext(plugin.bukkitDispatcher) { parts[0].location }
                val dir = withContext(plugin.bukkitDispatcher) { target.location.add(0.0, 0.8, 0.0).toVector().subtract(start.toVector()).normalize() }

                for (i in 1..15) {
                    if (!isRunning || hit) break
                    withContext(plugin.bukkitDispatcher) {
                        val next = parts[0].location.add(dir.clone().multiply(2.5))
                        moverTodo(next, false)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f)

                        val victims = next.world.getNearbyPlayers(next, 2.5).filter { !plugin.asesinoManager.esElAsesino(it) }
                        if (victims.isNotEmpty()) {
                            victims.forEach { matar(it) }
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
            target.playSound(target.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f)
            target.sendMessage(plugin.mm.deserialize("<dark_red><b>[!] EL IMPOSTOR ESTÁ FURIOSO"))
        }
        delay(1000)

        var hit = false
        val start = System.currentTimeMillis()
        while (isRunning && !hit && (System.currentTimeMillis() - start) < 5000) {
            if (!target.isOnline) break
            withContext(plugin.bukkitDispatcher) {
                val next = parts[0].location.add(target.location.toVector().subtract(parts[0].location.toVector()).normalize().multiply(1.2))
                moverTodo(next, true)
                target.playSound(next, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.8f)
                if (next.distanceSquared(target.location) < 2.0) {
                    matar(target, true)
                    hit = true
                }
            }
            delay(50)
        }
        withContext(plugin.bukkitDispatcher) { setGlowColor(NamedTextColor.WHITE) }
        if (hit) delay(5000)
    }

    private fun matar(victim: Player, rage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f)
        repeat(5) { plugin.gameManager.combatManager.takeDamage(victim) }
        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, false, true))

        val msg = if (rage) "<dark_red><b>[SABOTAJE]</b>" else "<red><b>[!]</b>"
        Bukkit.broadcast(plugin.mm.deserialize("$msg <white>${victim.name} fue eliminado por el impostor."))
    }

    private fun aplicarAura(loc: Location, time: Int) {
        loc.world.getNearbyPlayers(loc, 8.0).forEach { p ->
            if (!plugin.asesinoManager.esElAsesino(p)) {
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

    fun remove() { isRunning = false; parts.forEach { it.remove() }; parts.clear(); scope.cancel() }
}
