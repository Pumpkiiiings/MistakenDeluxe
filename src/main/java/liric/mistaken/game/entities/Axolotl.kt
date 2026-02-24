package liric.mistaken.game.entities

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Axolotl
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TROLL
 * AXOLOTL.EXE: El Ajolote Colosal (Mob Real Escalado).
 * IA: Saltos Stop-Motion -> Advertencia -> Misil Sónico -> Furia.
 */
class Axolotl(private val plugin: Mistaken) {

    private var entity: Axolotl? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = true
    private var currentTarget: Player? = null

    private var lastVictimUUID: UUID? = null
    private var consecutiveMisses = 0

    private val teamWhite = "AxoGlowWhite"
    private val teamRed = "AxoGlowRed"

    fun spawn(startLoc: Location) {
        plugin.pluginScope.launch(plugin.bukkitDispatcher) {
            try {
                // 1. Preparar equipos de Glow
                val sb = Bukkit.getScoreboardManager().mainScoreboard
                sb.getTeam(teamWhite) ?: sb.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                sb.getTeam(teamRed) ?: sb.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                // 2. Spawnear al Ajolote Real
                entity = startLoc.world.spawnEntity(startLoc, EntityType.AXOLOTL) as Axolotl
                entity?.apply {
                    // 🔥 LA MAGIA DE LA 1.21.4: Escala Triple
                    getAttribute(Attribute.SCALE)?.baseValue = 3.0

                    variant = Axolotl.Variant.LUCY // Rosa clásico (tétrico)
                    setAI(false) // Le quitamos el cerebro de Minecraft
                    isInvulnerable = true
                    isPersistent = false
                    isGlowing = true

                    // Empezamos con brillo blanco
                    sb.getTeam(teamWhite)?.addEntry(uniqueId.toString())
                }

                Bukkit.broadcast(plugin.mm.deserialize("<newline><aqua><b>[!]</b> <white>Algo enorme ha salido del agua... <pink><b>AXOLOTL.EXE</b>"))
                iniciarIA()
            } catch (e: Exception) {
                plugin.logger.severe("Error al invocar al Ajolote Gigante: ${e.message}")
            }
        }
    }

    private fun iniciarIA() {
        scope.launch {
            while (isRunning && entity != null && entity!!.isValid) {
                // 1. Buscamos a la presa
                val target = withContext(plugin.bukkitDispatcher) {
                    val loc = entity!!.location
                    val players = loc.world.getNearbyPlayers(loc, 100.0)
                        .filter { it.gameMode == org.bukkit.GameMode.SURVIVAL && !plugin.isIgnored(it) }

                    val potential = players.filter { it.uniqueId != lastVictimUUID }
                        .minByOrNull { it.location.distanceSquared(loc) }

                    potential ?: players.minByOrNull { it.location.distanceSquared(loc) }
                }

                if (target == null) { delay(2000); continue }
                currentTarget = target

                // --- ¿SE ENOJÓ? (Modo Furia) ---
                if (consecutiveMisses >= 5) {
                    ejecutarModoFuria(target)
                    consecutiveMisses = 0
                    continue
                }

                // --- FASE 1: SALTOS TETRICOS (5 veces a 2.5m) ---
                repeat(5) {
                    if (!isRunning || !target.isOnline) return@repeat
                    withContext(plugin.bukkitDispatcher) {
                        val loc = entity!!.location
                        val dir = target.location.toVector().subtract(loc.toVector()).normalize()
                        val nextLoc = loc.add(dir.multiply(2.5))

                        // Rotar para que mire al jugador
                        nextLoc.setDirection(dir)
                        entity!!.teleport(nextLoc)

                        // Sonido de yunque (Stop-motion)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f)
                        aplicarAuraMiedo(nextLoc, 20)
                    }
                    delay(800)
                }

                if (!isRunning || !target.isOnline) continue
                delay(1000)

                // --- FASE 2: ADVERTENCIA ---
                withContext(plugin.bukkitDispatcher) {
                    target.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f)
                    target.showTitle(Title.title(
                        plugin.mm.deserialize("<pink><b>AJOLOTE HAMBRIENTO"),
                        plugin.mm.deserialize("<gray>¡Va a morder!")
                    ))
                }
                delay(1200)

                // --- FASE 3: ATAQUE MISIL (12 pasos de 4m) ---
                var hit = false
                val start = withContext(plugin.bukkitDispatcher) { entity!!.location }
                val attackDir = withContext(plugin.bukkitDispatcher) {
                    target.location.add(0.0, 0.5, 0.0).toVector().subtract(start.toVector()).normalize()
                }

                for (i in 1..12) {
                    if (!isRunning || hit) break
                    withContext(plugin.bukkitDispatcher) {
                        val nextLoc = entity!!.location.add(attackDir.clone().multiply(4.0))
                        entity!!.teleport(nextLoc)

                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.2f)
                        nextLoc.world.spawnParticle(org.bukkit.Particle.BUBBLE_POP, nextLoc, 20, 1.0, 1.0, 1.0, 0.1)

                        val victims = nextLoc.world.getNearbyPlayers(nextLoc, 4.0)
                            .filter { !plugin.asesinoManager.esElAsesino(it) }

                        if (victims.isNotEmpty()) {
                            victims.forEach { ejecutarMuerte(it) }
                            hit = true
                        }
                    }
                    delay(120)
                }

                if (hit) consecutiveMisses = 0 else consecutiveMisses++
            }
        }
    }

    private suspend fun ejecutarModoFuria(target: Player) {
        withContext(plugin.bukkitDispatcher) {
            setGlowColor(NamedTextColor.RED)
            target.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 1.5f, 0.5f)
            target.sendMessage(plugin.mm.deserialize("<dark_red><b>[!] EL AJOLOTE SE HA VUELTO AGRESIVO"))
        }
        delay(1000)

        var hit = false
        val startTime = System.currentTimeMillis()
        while (isRunning && !hit && (System.currentTimeMillis() - startTime) < 5000) {
            if (!target.isOnline) break
            withContext(plugin.bukkitDispatcher) {
                val current = entity!!.location
                val dir = target.location.toVector().subtract(current.toVector()).normalize()
                val nextLoc = current.add(dir.multiply(1.5)) // Tracking directo

                nextLoc.setDirection(dir)
                entity!!.teleport(nextLoc)

                target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f)
                if (nextLoc.distanceSquared(target.location) < 3.5) {
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
        victim.world.spawnParticle(org.bukkit.Particle.SPLASH, victim.location, 50, 1.0, 1.0, 1.0, 0.2)
        victim.world.playSound(victim.location, Sound.ENTITY_FISH_SWIM, 2f, 0.1f)

        repeat(5) { plugin.gameManager.combatManager.takeDamage(victim) }

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, false, true))

        val prefix = if (enrage) "<dark_red><b>[DEPREDADOR]</b>" else "<pink><b>[!]</b>"
        Bukkit.broadcast(plugin.mm.deserialize("$prefix <white>${victim.name} fue devorado por <pink>AXOLOTL.EXE"))
    }

    private fun aplicarAuraMiedo(loc: Location, duration: Int) {
        loc.world.getNearbyPlayers(loc, 15.0).forEach { p ->
            if (!plugin.asesinoManager.esElAsesino(p)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 2, false, false, false))
            }
        }
    }

    private fun setGlowColor(color: NamedTextColor) {
        val sb = Bukkit.getScoreboardManager().mainScoreboard
        val team = sb.getTeam(if (color == NamedTextColor.RED) teamRed else teamWhite) ?: return
        val oldTeam = sb.getTeam(if (color == NamedTextColor.RED) teamWhite else teamRed)

        entity?.let {
            oldTeam?.removeEntry(it.uniqueId.toString())
            team.addEntry(it.uniqueId.toString())
        }
    }

    fun remove() {
        isRunning = false
        entity?.remove()
        scope.cancel()
    }
}
