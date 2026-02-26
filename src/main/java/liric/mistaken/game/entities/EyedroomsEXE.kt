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
 * [LIRIC-MISTAKEN 2.0] - MODO PESADILLA
 * EYEDROOMS.EXE: El Ojo de los Backrooms.
 * IA: Saltos de Vacío -> Inyección de Datos -> Ataque de Colapso.
 */
class EyedroomsEXE(private val plugin: Mistaken) {

    private val parts = mutableListOf<BlockDisplay>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = true
    private var currentTarget: Player? = null

    private var lastVictimUUID: UUID? = null
    private val teamName = "EyedroomsGlow"
    private var consecutiveMisses = 0

    fun spawn(startLoc: Location) {
        plugin.pluginScope.launch(plugin.bukkitDispatcher) {
            try {
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName).apply {
                    color(NamedTextColor.DARK_PURPLE)
                }

                // --- INGENIERÍA DEL OJO (3x3x3 con pupila) ---

                // 1. ESCLERÓTICA (Cubo Blanco de 3x3x3)
                val whiteEye = createPart(startLoc, Material.WHITE_CONCRETE, Vector3f(3f, 3f, 3f), Vector3f(-1.5f, 0f, -1.5f))

                // 2. PUPILA (Cubo Negro que sobresale)
                val pupil = createPart(startLoc, Material.BLACK_CONCRETE, Vector3f(1.8f, 1.8f, 0.2f), Vector3f(-0.9f, 0.6f, 1.45f))

                // 3. VENAS/CORRUPCIÓN (Bloques de Neterrack pequeños flotando)
                val glitch1 = createPart(startLoc, Material.NETHERRACK, Vector3f(0.5f, 0.5f, 0.5f), Vector3f(-2.0f, 2.0f, 0f))
                val glitch2 = createPart(startLoc, Material.NETHERRACK, Vector3f(0.5f, 0.5f, 0.5f), Vector3f(1.5f, 0.5f, 0f))

                parts.addAll(listOf(whiteEye, pupil, glitch1, glitch2))
                parts.forEach { team.addEntry(it.uniqueId.toString()) }

                Bukkit.broadcast(plugin.mm.deserialize("<newline><dark_purple><b>[!]</b> <white>EL SISTEMA HA SIDO INFECTADO POR <dark_red><b>EYEDROOMS.EXE</b>"))
                iniciarIA()
            } catch (e: Exception) {
                plugin.logger.severe("Fallo al invocar al Eyedrooms: ${e.message}")
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
                    bodyLoc.world.getNearbyPlayers(bodyLoc, 100.0)
                        .filter { it.gameMode == org.bukkit.GameMode.SURVIVAL && !plugin.isIgnored(it) }
                        .filter { it.uniqueId != lastVictimUUID }
                        .minByOrNull { it.location.distanceSquared(bodyLoc) }
                }

                if (target == null) { delay(2000); continue }
                currentTarget = target

                // --- FASE 1: ACECHO SÓNICO (3 saltos largos de 3.5m) ---
                repeat(3) {
                    if (!isRunning || !target.isOnline) return@repeat
                    withContext(plugin.bukkitDispatcher) {
                        val bodyLoc = parts[0].location
                        val dir = target.location.toVector().subtract(bodyLoc.toVector()).normalize()
                        val nextLoc = bodyLoc.add(dir.multiply(3.5))

                        moverTodo(nextLoc, true)

                        // Sonido mezclado: Yunque + Warden pitched down (Aterrador)
                        target.playSound(nextLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.1f)
                        target.playSound(nextLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.5f)

                        aplicarEfectosAgresivos(nextLoc, 30)
                    }
                    delay(700)
                }

                // --- FASE 2: EL REGALO ENVENENADO (Targeting) ---
                withContext(plugin.bukkitDispatcher) {
                    target.playSound(target.location, Sound.BLOCK_CONDUIT_ACTIVATE, 2f, 0.1f)
                    target.showTitle(Title.title(
                        plugin.mm.deserialize("<dark_purple><obfuscated>ERR_VOICE"),
                        plugin.mm.deserialize("<gray>Has recibido una <green>bendición <red>corrupta")
                    ))
                    // Efecto "Positivo": Velocidad III | Efecto "Negativo": Wither II y Darkness
                    target.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 2, false, false))
                    target.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 100, 1, false, false))
                    target.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false))
                }
                delay(1500)

                // --- FASE 3: MODO MISIL CUÁNTICO (15 impulsos de 5m) ---
                var hasHit = false
                val startAttackLoc = withContext(plugin.bukkitDispatcher) { parts[0].location }
                val attackDir = withContext(plugin.bukkitDispatcher) {
                    target.location.add(0.0, 1.0, 0.0).toVector().subtract(startAttackLoc.toVector()).normalize()
                }

                for (i in 1..15) {
                    if (!isRunning || hasHit) break
                    withContext(plugin.bukkitDispatcher) {
                        val nextLoc = parts[0].location.add(attackDir.clone().multiply(5.0))
                        moverTodo(nextLoc, false)

                        // Sonido de grito de Ghast rápido (Pitch 2.0)
                        target.playSound(nextLoc, Sound.ENTITY_GHAST_SCREAM, 0.8f, 2.0f)
                        nextLoc.world.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, nextLoc, 15, 0.8, 0.8, 0.8, 0.1)

                        if (nextLoc.distanceSquared(target.location) < 3.5) {
                            ejecutarMuerte(target)
                            hasHit = true
                        }
                    }
                    delay(100)
                }

                if (hasHit) delay(4000) else delay(1000)
            }
        }
    }

    private fun aplicarEfectosAgresivos(loc: Location, duration: Int) {
        loc.world.getNearbyPlayers(loc, 15.0).forEach { p ->
            if (!plugin.asesinoManager.esElAsesino(p)) {
                // Debilita a los jugadores que están cerca del ojo
                p.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, duration, 0, false, false, false))
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 2, false, false, false))
                // Sonido estático de radio
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

        // El daño es masivo: 5 vidas y lo deja casi muerto
        repeat(5) { plugin.gameManager.combatManager.takeDamage(victim) }

        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
        victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 200, 0))

        Bukkit.broadcast(plugin.mm.deserialize("<dark_purple><b>[!]</b> <white>${victim.name} fue borrado por la mirada de <dark_red>EYEDROOMS"))
    }

    private fun explodeAndRemove() {
        if (parts.isNotEmpty()) {
            val loc = parts[0].location
            loc.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc, 5)
            loc.world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.1f)
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
