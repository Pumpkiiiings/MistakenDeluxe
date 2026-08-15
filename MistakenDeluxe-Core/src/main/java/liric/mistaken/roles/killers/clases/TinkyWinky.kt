package liric.mistaken.roles.killers.clases

import liric.mistaken.utils.sessionViewers
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.roles.killers.CoreKiller
import liric.mistaken.utils.hooks.CraftEngine
import liric.mistaken.packet.PacketFactory
import liric.mistaken.packet.fake.VirtualItemDisplay
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

/**
 * [LIRIC-MISTAKEN 2.0]
 * TinkyWinky: El Horror de los Teletubbies.
 * Inspirado en el antagonista principal de Slendytubbies.
 * Estilo de juego: Sigilo, terror psicológico y trampas de área.
 */
class TinkyWinky : CoreKiller(
    "tinkywinky",
    PumpkingServiceManager.messages.getStrictString(null, "asesinos.tinkywinky.nombre", "killers_info")
), Listener {

    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Trail orbital (3 items: Slime, Purple Dye, Music Disc)
    private val orbitadores = ConcurrentHashMap<UUID, MutableList<VirtualItemDisplay>>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val orbitMaterials = listOf(Material.SLIME_BALL, Material.PURPLE_DYE, Material.MUSIC_DISC_CAT)

    // Pasiva: Melodía Corrompida — IDs de jugadores ya afectados en este tick
    private val pasiveAura = ConcurrentHashMap.newKeySet<UUID>()

    // Skill 1: Bolsa Mágica — jugadores marcados (glow)
    private val marcados = ConcurrentHashMap<UUID, Long>()

    // Skill 2: Paso Silencioso — players actualmente invisibles
    private val enSigilo = ConcurrentHashMap.newKeySet<UUID>()

    // Anti-spam finishers
    private val lastKillEffect = ConcurrentHashMap<UUID, Long>()

    init {
        preLoadKit()
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // ─── KIT ────────────────────────────────────────────────────────────────

    private fun preLoadKit() {
        val config = plugin.configManager.getKillerConfig(this.id)

        listOf("helmet", "chestplate", "leggings", "boots").forEach { key ->
            config.getString("armor.$key")?.let { id ->
                if (id != "none" && id.isNotBlank()) {
                    itemKitCache[key] = CraftEngine.getCustomItem(id)
                        ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.NETHERITE_HELMET)
                }
            }
        }

        listOf("weapon", "skill1", "skill2", "skill3", "skill4").forEach { key ->
            config.getString("items.$key")?.let { id ->
                if (id != "none" && id.isNotBlank()) {
                    itemKitCache[key] = CraftEngine.getCustomItem(id)
                        ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
                }
            }
        }
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        if (itemKitCache.isEmpty()) preLoadKit()

        val configMecanica = plugin.configManager.getKillerConfig(this.id)
        val langInfo = PumpkingServiceManager.messages.getSpecificFile(player, "killers_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("armor.$key")
            else configMecanica.getString("items.$key")

            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "weapon") "asesinos.tinkywinky.skill_names.weapon"
            else "asesinos.tinkywinky.skill_names.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(ColorTranslator.translate(it)) }
            }

            if (isArmor) {
                when (key) {
                    "helmet"     -> inv.helmet = item
                    "chestplate" -> inv.chestplate = item
                    "leggings"   -> inv.leggings = item
                    "boots"      -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        deliver("helmet", 0, true); deliver("chestplate", 0, true)
        deliver("leggings", 0, true); deliver("boots", 0, true)
        deliver("skill1", 1); deliver("skill2", 2)
        deliver("skill3", 3); deliver("skill4", 4)
        deliver("weapon", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    // ─── HABILIDADES ────────────────────────────────────────────────────────

    override fun useSkill(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        when (slot) {
            1 -> habilidadBolsaMagica(player)
            2 -> habilidadPasoSilencioso(player)
            3 -> habilidadSenialTV(player)
            4 -> habilidadUltimaCancion(player)
        }
        playSkillEffects(player, slot)
    }

    /**
     * SKILL 1 — Bolsa Mágica:
     * Lanza un proyectil (Snowball) que al impactar un sobreviviente lo marca con Glow
     * durante 5 segundos y lo revela a TinkyWinky.
     */
    private fun habilidadBolsaMagica(player: Player) {
        val snowball = player.launchProjectile(Snowball::class.java)
        snowball.velocity = player.location.direction.normalize().multiply(1.8)

        var life = 0
        snowball.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (life >= 80 || !snowball.isValid) {
                task.cancel()
                return@Consumer
            }

            // Partículas moradas de la bolsa en vuelo
            snowball.world.spawnParticle(
                org.bukkit.Particle.WITCH,
                snowball.location, 2, 0.1, 0.1, 0.1, 0.01
            )

            val victim = snowball.world.getNearbyPlayers(snowball.location, 1.0)
                .firstOrNull { isValidTarget(player, it) }

            if (victim != null) {
                // Marcar al objetivo con Glow
                victim.isGlowing = true
                marcados[victim.uniqueId] = System.currentTimeMillis() + 5000L

                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 0))
                victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_STARE, 1f, 0.3f)
                victim.sendMessage(ColorTranslator.translate(
                    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(victim, "asesinos.tinkywinky.habilidades.bolsa_marcado", "killers_info")
                ))

                snowball.world.spawnParticle(org.bukkit.Particle.WITCH, victim.location.add(0.0, 1.0, 0.0), 30, 0.5, 0.5, 0.5, 0.1)
                snowball.remove()
                task.cancel()

                // Quitar glow después de 5s
                plugin.server.regionScheduler.runDelayed(plugin, victim.location, Consumer { _ ->
                    if (marcados.getOrDefault(victim.uniqueId, 0L) <= System.currentTimeMillis()) {
                        victim.isGlowing = false
                        marcados.remove(victim.uniqueId)
                    }
                }, 100L) // 5 segundos = 100 ticks
            }
            life++
        }, null, 1L, 1L)
    }

    /**
     * SKILL 2 — Paso Silencioso:
     * TinkyWinky desaparece (invisibilidad + velocidad) durante 3 segundos.
     */
    private fun habilidadPasoSilencioso(player: Player) {
        if (enSigilo.contains(player.uniqueId)) return
        enSigilo.add(player.uniqueId)

        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 1, false, false))
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f)

        // Partículas de humo negro al activar
        player.world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, player.location.add(0.0, 1.0, 0.0), 20, 0.3, 0.5, 0.3, 0.02)

        player.scheduler.runDelayed(plugin, Consumer { _ ->
            enSigilo.remove(player.uniqueId)
            // Partículas de reaparición
            player.world.spawnParticle(org.bukkit.Particle.WITCH, player.location.add(0.0, 1.0, 0.0), 25, 0.3, 0.5, 0.3, 0.05)
            player.playSound(player.location, Sound.ENTITY_PHANTOM_AMBIENT, 0.6f, 0.5f)
        }, null, 60L)
    }

    /**
     * SKILL 3 — Señal de TV:
     * Estática que ciega y marea a todos los sobrevivientes en radio 10.
     */
    private fun habilidadSenialTV(player: Player) {
        var afectados = 0
        player.world.getNearbyPlayers(player.location, 10.0).forEach { victim ->
            if (isValidTarget(player, victim)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 80, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0))
                victim.playSound(victim.location, Sound.BLOCK_NOTE_BLOCK_BASS, 2f, 0.1f)
                victim.sendMessage(ColorTranslator.translate(
                    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(victim, "asesinos.tinkywinky.habilidades.senal_distorsiona", "killers_info")
                ))
                afectados++
            }
        }

        // Efecto visual: flash + partículas eléctricas desde el killer
        player.world.spawnParticle(org.bukkit.Particle.FIREWORK, player.location.add(0.0, 1.0, 0.0), 3, 1.0, 1.0, 1.0, 0.0)
        player.world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, player.location.add(0.0, 1.5, 0.0), 30, 1.5, 1.5, 1.5, 0.1)
        player.world.playSound(player.location, Sound.BLOCK_BEACON_AMBIENT, 2f, 0.1f)

        if (afectados == 0) {
            player.sendMessage(ColorTranslator.translate(
                pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(player, "asesinos.tinkywinky.habilidades.nadie_cerca", "killers_info")
            ))
        }
    }

    /**
     * SKILL 4 (Ultimate) — La Última Canción:
     * Grito distorsionado: EvokerFangs en espiral + terror auditivo.
     */
    private fun habilidadUltimaCancion(player: Player) {
        val startLoc = player.location
        val world = startLoc.world ?: return

        world.playSound(startLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.3f)
        world.playSound(startLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f)

        // Espiral de EvokerFangs hacia afuera
        var tick = 0
        plugin.server.regionScheduler.runAtFixedRate(plugin, startLoc, Consumer { task ->
            if (tick >= 40) { task.cancel(); return@Consumer }

            val angle = tick * 0.4
            val radius = tick * 0.25
            val x = radius * cos(angle)
            val z = radius * sin(angle)
            val spawnLoc = startLoc.clone().add(x, 0.0, z)

            if (!spawnLoc.block.type.isSolid) {
                world.spawn(spawnLoc, EvokerFangs::class.java)
                world.spawnParticle(org.bukkit.Particle.WITCH, spawnLoc.clone().add(0.0, 1.0, 0.0), 3, 0.1, 0.1, 0.1, 0.01)
            }

            // Efecto en sobrevivientes cercanos
            world.getNearbyPlayers(spawnLoc, 1.5).forEach { victim ->
                if (isValidTarget(player, victim)) {
                    plugin.combatManager.takeDamage(victim)
                    victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 60, 0))
                    victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.2f)
                }
            }
            tick++
        }, 1L, 1L)
    }

    // ─── PASIVA: MELODÍA CORROMPIDA ──────────────────────────────────────────
    // Llamada desde showTrail() que se invoca cada tick de movimiento del killer.
    // Aplica DARKNESS periódico a sobrevivientes muy cercanos (≤ 8 bloques).

    private fun aplicarAuraPasiva(player: Player) {
        player.world.getNearbyPlayers(player.location, 8.0).forEach { victim ->
            if (isValidTarget(player, victim) && !pasiveAura.contains(victim.uniqueId)) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false))
                // Reproducir un sonido tenue para el sobreviviente
                victim.playSound(victim.location, Sound.AMBIENT_CAVE, 0.4f, 0.5f)
                pasiveAura.add(victim.uniqueId)

                // Cooldown de 3 segundos por sobreviviente para no spamear
                plugin.server.regionScheduler.runDelayed(plugin, victim.location, Consumer { _ ->
                    pasiveAura.remove(victim.uniqueId)
                }, 60L)
            }
        }
    }

    // ─── FINISHERS ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTinkyWinkyKill(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val session = plugin.sessionManager.getSession(attacker) ?: return
        if (session.isKiller(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
            if (victim.gameMode == GameMode.SPECTATOR) {
                val now = System.currentTimeMillis()
                if (now - lastKillEffect.getOrDefault(victim.uniqueId, 0L) > 2000L) {
                    lastKillEffect[victim.uniqueId] = now
                    triggerFinisherAleatorio(victim.location)
                }
            }
        }
    }

    private fun triggerFinisherAleatorio(loc: Location) {
        val effectType = ThreadLocalRandom.current().nextInt(3)
        val world = loc.world ?: return

        when (effectType) {
            0 -> {
                // FINISHER 1: BOLSA DEL CAOS — espiral de almas aspiradas hacia la bolsa
                world.playSound(loc, Sound.ENTITY_ENDERMAN_STARE, 1.5f, 0.1f)

                var ticks = 0
                plugin.server.regionScheduler.runAtFixedRate(plugin, loc, Consumer { task ->
                    if (ticks >= 50) { task.cancel(); return@Consumer }

                    val angle = ticks * 0.6
                    val radius = 3.0 - (ticks * 0.06)
                    val x = radius * cos(angle)
                    val z = radius * sin(angle)
                    val y = ticks * 0.08

                    world.spawnParticle(org.bukkit.Particle.SCULK_SOUL, loc.clone().add(x, y, z), 2, 0.05, 0.05, 0.05, 0.0)
                    world.spawnParticle(org.bukkit.Particle.WITCH, loc.clone().add(-x, y * 0.5, -z), 1, 0.05, 0.05, 0.05, 0.0)
                    ticks++
                }, 1L, 1L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.5f)
                    world.spawnParticle(org.bukkit.Particle.SONIC_BOOM, loc.clone().add(0.0, 1.0, 0.0), 1)
                }, 50L)
            }

            1 -> {
                // FINISHER 2: SEÑAL PERDIDA — pantallas de Crying Obsidian parpadeando
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.3f)

                val displays = mutableListOf<org.bukkit.entity.BlockDisplay>()

                for (i in 0 until 4) {
                    val angle = (i * Math.PI * 2) / 4
                    val x = 1.8 * cos(angle)
                    val z = 1.8 * sin(angle)
                    val bd = world.spawn(loc.clone().add(x, 0.5, z), org.bukkit.entity.BlockDisplay::class.java) {
                        it.block = Material.CRYING_OBSIDIAN.createBlockData()
                        it.transformation = Transformation(
                            JomlVector3f(-0.4f, -0.4f, -0.4f),
                            Quaternionf(),
                            JomlVector3f(0.8f, 0.8f, 0.8f),
                            Quaternionf()
                        )
                        it.teleportDuration = 2
                    }
                    displays.add(bd)
                }

                // Parpadeo: subir y bajar
                var blink = 0
                plugin.server.regionScheduler.runAtFixedRate(plugin, loc, Consumer { task ->
                    if (blink >= 6) { task.cancel(); return@Consumer }
                    displays.forEach { bd ->
                        if (bd.isValid) {
                            val offset = if (blink % 2 == 0) 0.5 else -0.5
                            bd.teleport(bd.location.add(0.0, offset, 0.0))
                        }
                    }
                    world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, loc.clone().add(0.0, 1.5, 0.0), 5, 1.0, 0.5, 1.0, 0.05)
                    blink++
                }, 5L, 5L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_ENDERMAN_DEATH, 1f, 0.5f)
                    world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, loc, 60, 1.5, 1.5, 1.5, 0.05)
                    displays.forEach { if (it.isValid) it.remove() }
                }, 35L)
            }

            2 -> {
                // FINISHER 3: MELODÍA FINAL — lluvia de notas musicales + humo ascendente
                world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HARP, 2f, 0.5f)
                world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 1f, 0.3f)

                var ticks = 0
                plugin.server.regionScheduler.runAtFixedRate(plugin, loc, Consumer { task ->
                    if (ticks >= 30) { task.cancel(); return@Consumer }

                    val angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2)
                    val r = ThreadLocalRandom.current().nextDouble(0.3, 1.8)
                    world.spawnParticle(
                        org.bukkit.Particle.NOTE,
                        loc.clone().add(r * cos(angle), ticks * 0.12, r * sin(angle)),
                        1, 0.0, 0.0, 0.0, 1.0
                    )
                    world.spawnParticle(
                        org.bukkit.Particle.CAMPFIRE_COSY_SMOKE,
                        loc.clone().add(0.0, ticks * 0.1, 0.0),
                        2, 0.2, 0.0, 0.2, 0.02
                    )
                    if (ticks % 6 == 0) {
                        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 0.5f + (ticks * 0.05f))
                    }
                    ticks++
                }, 1L, 2L)
            }
        }
    }

    // ─── TRAIL VISUAL ───────────────────────────────────────────────────────

    override fun showPhysicalTrail(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.isKiller(player)) { limpiarVisuales(uuid); return }

        // Si cambió de mundo, recrear orbitadores
        if (orbitadores[uuid]?.firstOrNull()?.world != player.world) limpiarVisuales(uuid)

        val entidades = orbitadores.getOrPut(uuid) {
            mutableListOf<VirtualItemDisplay>().apply {
                orbitMaterials.forEach { mat ->
                    add(PacketFactory.displays.buildItemDisplay(player.sessionViewers(), player.location) { id ->
                        id.setItemStack(ItemStack(mat))
                        id.transformation = Transformation(
                            JomlVector3f(0f, 0f, 0f),
                            Quaternionf(),
                            JomlVector3f(0.45f, 0.45f, 0.45f),
                            Quaternionf()
                        )
                        id.teleportDuration = 3
                        id.interpolationDuration = 3
                    })
                }
            }
        }

        val anguloActual = angulos.getOrDefault(uuid, 0.0)
        val radio = 1.4
        val step = (2 * Math.PI) / entidades.size
        val playerLoc = player.location

        for (i in entidades.indices) {
            val display = entidades[i]
            if (display.isValid) {
                val currentAngle = anguloActual + (step * i)
                val x = radio * cos(currentAngle)
                val z = radio * sin(currentAngle)
                val y = 1.2 + (0.18 * sin(currentAngle * 2))

                val targetLoc = playerLoc.clone().add(x, y, z)
                targetLoc.yaw = (currentAngle * 110).toFloat() % 360
                display.teleport(targetLoc)
            }
        }
        angulos[uuid] = anguloActual + 0.10

        // Activar aura pasiva mientras el killer se mueve
        aplicarAuraPasiva(player)
    }

    override fun showTrail(player: Player) {
        val l = player.location.add(0.0, 1.2, 0.0)
        val pos = Vector3d(l.x, l.y, l.z)
        val mgr = PacketEvents.getAPI().playerManager
        val packet = WrapperPlayServerParticle(
            Particle(ParticleTypes.WITCH), false,
            pos,
            Vector3f(0.18f, 0.18f, 0.18f),
            0.02f,
            1
        )
        l.world.players.forEach { p ->
            if (p != player && p.location.distanceSquared(l) < 625.0) mgr.sendPacket(p, packet)
        }
    }

    // ─── LIMPIEZA ───────────────────────────────────────────────────────────

    private fun limpiarVisuales(uuid: UUID) {
        orbitadores.remove(uuid)?.forEach { it.remove() }
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarVisuales(it.uniqueId)
            enSigilo.remove(it.uniqueId)
            marcados.remove(it.uniqueId)
            it.isGlowing = false
            lastKillEffect.remove(it.uniqueId)
        }
        pasiveAura.clear()
    }

    override fun clearGlobalData() {
        pasiveAura.clear()
        marcados.clear()
        enSigilo.clear()
    }
}
