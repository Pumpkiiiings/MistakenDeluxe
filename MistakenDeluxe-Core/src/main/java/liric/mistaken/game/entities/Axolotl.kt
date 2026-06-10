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
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Axolotl
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration
import java.util.*
import java.util.function.Consumer

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TROLL
 * AXOLOTL.EXE: El Ajolote Colosal (Mob Real Escalado).
 * ADAPTADO: Multiarena/Velocity con aislamiento de sesión.
 */
class Axolotl(private val plugin: Mistaken) {

    private var entity: Axolotl? = null
    private var isRunning = false
    private var currentTarget: Player? = null
    private var lastVictimUUID: UUID? = null
    private var consecutiveMisses = 0

    // 🔥 Referencia a la sesión a la que pertenece esta entidad
    private var assignedSession: GameSession? = null

    private val teamWhite = "AxoGlowWhite"
    private val teamRed = "AxoGlowRed"

    private var fase = 0
    private var ticks = 0
    private var pasos = 0

    fun spawn(startLoc: Location) {
        // 🔥 Detectamos la sesión en la ubicación de spawn
        assignedSession = plugin.sessionManager.activeSessions.values.find {
            it.currentMapName != "Esperando..." && it.getPlayers().any { p -> p.world == startLoc.world }
        }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            try {
                val sb = Bukkit.getScoreboardManager().mainScoreboard
                if (sb.getTeam(teamWhite) == null) sb.registerNewTeam(teamWhite).apply { color(NamedTextColor.WHITE) }
                if (sb.getTeam(teamRed) == null) sb.registerNewTeam(teamRed).apply { color(NamedTextColor.RED) }

                entity = startLoc.world.spawnEntity(startLoc, EntityType.AXOLOTL) as Axolotl
                entity?.apply {
                    getAttribute(Attribute.SCALE)?.baseValue = 3.0
                    variant = Axolotl.Variant.LUCY
                    setAI(false)
                    isInvulnerable = true
                    isPersistent = false
                    isGlowing = true
                    sb.getTeam(teamWhite)?.addEntry(uniqueId.toString())
                }

                // 🔥 Broadcast solo para la sesión
                val spawnMsg = plugin.mm.deserialize("<newline><aqua><b>[!]</b> <white>Algo enorme ha salido del agua... <pink><b>AXOLOTL.EXE</b>")
                assignedSession?.getPlayers()?.forEach { it.sendMessage(spawnMsg) }

                isRunning = true
                iniciarIA()
            } catch (e: Exception) {
                plugin.componentLogger.error("Error al invocar al Ajolote Gigante: ${e.message}")
            }
        }
    }

    private fun iniciarIA() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!isRunning || entity == null || !entity!!.isValid) {
                task.cancel()
                return@Consumer
            }

            val loc = entity!!.location
            val session = assignedSession

            // BUSCAR PRESA (Filtrada por Sesión)
            if (fase == 0) {
                val potentialTargets = if (session != null) {
                    session.getPlayers().filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                } else {
                    loc.world.getNearbyPlayers(loc, 100.0).filter { it.gameMode == GameMode.SURVIVAL && !plugin.isIgnored(it) }
                }

                val pot = potentialTargets.filter { it.uniqueId != lastVictimUUID }.minByOrNull { it.location.distanceSquared(loc) }
                currentTarget = pot ?: potentialTargets.minByOrNull { it.location.distanceSquared(loc) }

                if (currentTarget == null) return@Consumer

                if (consecutiveMisses >= 5) {
                    fase = 4
                    ticks = 0
                } else {
                    fase = 1
                    ticks = 0
                    pasos = 0
                }
                return@Consumer
            }

            val target = currentTarget
            if (target == null || !target.isOnline || (session != null && plugin.sessionManager.getSession(target) != session)) {
                fase = 0
                currentTarget = null
                return@Consumer
            }

            when (fase) {
                1 -> { // 5 Saltos tétricos
                    if (ticks % 16 == 0) {
                        val dir = target.location.toVector().subtract(loc.toVector()).normalize()
                        val nextLoc = loc.add(dir.multiply(2.5))
                        nextLoc.setDirection(dir)
                        entity!!.teleport(nextLoc)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f)
                        aplicarAuraMiedo(nextLoc, 20)
                        pasos++

                        if (pasos >= 5) {
                            fase = 2
                            ticks = 0
                        }
                    }
                }
                2 -> { // Advertencia
                    if (ticks == 20) {
                        target.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f)
                        target.showTitle(Title.title(plugin.mm.deserialize("<pink><b>AJOLOTE HAMBRIENTO"), plugin.mm.deserialize("<gray>¡Va a morder!")))
                    }
                    if (ticks >= 44) {
                        fase = 3
                        ticks = 0
                        pasos = 0
                    }
                }
                3 -> { // Ataque misil
                    if (ticks % 2 == 0) {
                        val dir = target.location.add(0.0, 0.5, 0.0).toVector().subtract(loc.toVector()).normalize()
                        val next = loc.add(dir.multiply(4.0))
                        entity!!.teleport(next)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.2f)
                        next.world.spawnParticle(org.bukkit.Particle.BUBBLE_POP, next, 20, 1.0, 1.0, 1.0, 0.1)

                        // Solo golpea si es de la sesión y no es el asesino
                        val hit = next.world.getNearbyPlayers(next, 4.0).filter { p ->
                            val pSession = plugin.sessionManager.getSession(p)
                            pSession == session && pSession?.esAsesino(p.uniqueId) != true
                        }

                        if (hit.isNotEmpty()) {
                            hit.forEach { ejecutarMuerte(it) }
                            consecutiveMisses = 0
                            fase = 0
                            return@Consumer
                        }

                        pasos++
                        if (pasos >= 12) {
                            consecutiveMisses++
                            fase = 0
                        }
                    }
                }
                4 -> { // Modo Furia
                    if (ticks == 0) {
                        setGlowColor(NamedTextColor.RED)
                        target.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 1.5f, 0.5f)
                        target.sendMessage(plugin.mm.deserialize("<dark_red><b>[!] EL AJOLOTE SE HA VUELTO AGRESIVO"))
                    }

                    if (ticks > 20 && ticks < 120) {
                        val dir = target.location.toVector().subtract(loc.toVector()).normalize()
                        val next = loc.add(dir.multiply(1.5))
                        next.setDirection(dir)
                        entity!!.teleport(next)
                        target.playSound(next, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f)

                        if (next.distanceSquared(target.location) < 12.25) { // 3.5 bloques reales
                            ejecutarMuerte(target, true)
                            consecutiveMisses = 0
                            setGlowColor(NamedTextColor.WHITE)
                            fase = 0
                            return@Consumer
                        }
                    }

                    if (ticks >= 120) {
                        setGlowColor(NamedTextColor.WHITE)
                        consecutiveMisses = 0
                        fase = 0
                    }
                }
            }
            ticks++
        }, 1L, 1L)
    }

    private fun ejecutarMuerte(victim: Player, enrage: Boolean = false) {
        lastVictimUUID = victim.uniqueId
        victim.world.spawnParticle(org.bukkit.Particle.SPLASH, victim.location, 50, 1.0, 1.0, 1.0, 0.2)
        victim.world.playSound(victim.location, Sound.ENTITY_FISH_SWIM, 2f, 0.1f)

        // Daño procesado por manager global
        repeat(5) { plugin.combatManager.takeDamage(victim) }

        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true))
        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, false, true))

        val prefix = if (enrage) "<dark_red><b>[DEPREDADOR]</b>" else "<pink><b>[!]</b>"
        val deathMsg = plugin.mm.deserialize("$prefix <white>${victim.name} fue devorado por <pink>AXOLOTL.EXE")

        assignedSession?.getPlayers()?.forEach { it.sendMessage(deathMsg) }
    }

    private fun aplicarAuraMiedo(loc: Location, duration: Int) {
        loc.world.getNearbyPlayers(loc, 15.0).forEach { p ->
            val pSession = plugin.sessionManager.getSession(p)
            if (pSession == assignedSession && pSession?.esAsesino(p.uniqueId) != true) {
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
    }
}
