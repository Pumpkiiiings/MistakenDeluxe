package liric.mistaken.roles.asesinos.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import liric.mistaken.Mistaken
import liric.mistaken.roles.asesinos.Asesino
import liric.mistaken.utils.hooks.CraftEngineHook
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

/**
 *[LIRIC-MISTAKEN 2.0]
 * Romeo: El Administrador del Mundo.
 * FIX: Adaptado a Folia y concurrencia segura.
 */
class Romeo : Asesino(
    "romeo",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.romeo.nombre", "<gradient:#ff0000:#ffff00><b>ROMEO</b></gradient>", "asesinos_info")
), Listener {

    private val pathBase = "asesinos.romeo"
    private val orbitadores = ConcurrentHashMap<UUID, BlockDisplay>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    private val lastKillEffect = ConcurrentHashMap<UUID, Long>()

    init {
        preLoadKit()
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armorMap = mapOf(
            "casco" to Material.NETHERITE_HELMET,
            "pechera" to Material.NETHERITE_CHESTPLATE,
            "pantalones" to Material.NETHERITE_LEGGINGS,
            "botas" to Material.NETHERITE_BOOTS
        )

        armorMap.forEach { (key, fallbackMat) ->
            val id = config.getString("$pathBase.armadura.$key") ?: "none"
            if (id != "none" && id.isNotBlank()) {
                val item = CraftEngineHook.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: fallbackMat)
            }
        }

        listOf("arma", "habilidad1", "habilidad2", "habilidad3", "habilidad4").forEach { key ->
            val id = config.getString("$pathBase.items.$key") ?: "none"
            if (id != "none" && id.isNotBlank()) {
                val item = CraftEngineHook.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        when (slot) {
            1 -> { habilidadAdminDash(player); reproducirEfectosHabilidad(player, 1) }
            2 -> { habilidadAdminVision(player); reproducirEfectosHabilidad(player, 2) }
            3 -> { habilidadTripleColmillo(player); reproducirEfectosHabilidad(player, 3) }
            4 -> { habilidadNetherStar(player); reproducirEfectosHabilidad(player, 4) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRomeoKill(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val sessionManager = plugin.sessionManager ?: return
        val session = sessionManager.getSession(attacker) ?: return

        if (session.esAsesino(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
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
                plugin.server.regionScheduler.run(plugin, loc, { _ ->
                    world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f)

                    val jail = world.spawn(loc.clone().add(0.0, 1.0, 0.0), BlockDisplay::class.java) {
                        it.block = Material.RED_STAINED_GLASS.createBlockData()
                        it.transformation = Transformation(JomlVector3f(-1f, -1f, -1f), Quaternionf(), JomlVector3f(2f, 2f, 2f), Quaternionf())
                        it.isGlowing = true
                    }

                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 2f)
                        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3, 0.5, 0.5, 0.5, 0.0)
                        world.spawnParticle(Particle.WHITE_ASH, loc, 300, 1.0, 1.0, 1.0, 0.5)
                        jail.remove()
                    }, 20L)
                })
            }
            1 -> {
                plugin.server.regionScheduler.run(plugin, loc, { _ ->
                    val dropLoc = loc.clone().add(0.0, 10.0, 0.0)
                    val block = world.spawn(dropLoc, BlockDisplay::class.java) {
                        it.block = Material.COMMAND_BLOCK.createBlockData()
                        it.transformation = Transformation(JomlVector3f(-1.5f, -1.5f, -1.5f), Quaternionf(), JomlVector3f(3f, 3f, 3f), Quaternionf())
                        it.teleportDuration = 10
                    }

                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ -> block.teleport(loc) }, 1L)

                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                        world.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2f, 0.1f)
                        world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 2f, 0.5f)
                        world.spawnParticle(Particle.EXPLOSION, loc, 2)
                        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 1.5, 0.5, 1.5, 0.1)
                    }, 11L)

                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ -> block.remove() }, 30L)
                })
            }
            2 -> {
                plugin.server.regionScheduler.run(plugin, loc, { _ ->
                    world.playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 2f)
                    val star = world.spawn(loc.clone().add(0.0, 0.5, 0.0), ItemDisplay::class.java) {
                        it.setItemStack(ItemStack(Material.NETHER_STAR))
                        it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(1f, 1f, 1f), Quaternionf())
                        it.teleportDuration = 30
                        it.interpolationDuration = 30
                    }

                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                        star.teleport(loc.clone().add(0.0, 4.0, 0.0))
                        val t = star.transformation
                        t.leftRotation.rotateY(5f)
                        star.transformation = t
                    }, 1L)

                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                        world.playSound(loc.clone().add(0.0, 4.0, 0.0), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.5f)
                        world.spawnParticle(Particle.FIREWORK, loc.clone().add(0.0, 4.0, 0.0), 100, 0.5, 0.5, 0.5, 0.2)
                        world.spawnParticle(Particle.END_ROD, loc.clone().add(0.0, 4.0, 0.0), 50, 0.5, 0.5, 0.5, 0.5)
                        star.remove()
                    }, 31L)
                })
            }
        }
    }

    private fun habilidadAdminDash(player: Player) {
        player.scheduler.run(plugin, { _ ->
            player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f)
            var duration = 100

            val task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (duration <= 0 || !player.isOnline) {
                    task.cancel()
                    return@Consumer
                }

                player.scheduler.run(plugin, { _ ->
                    val dir = player.location.direction.normalize().multiply(1.4)
                    player.velocity = dir

                    val checkLoc = player.location.clone().add(dir.clone().multiply(0.8))
                    if (checkLoc.block.type.isSolid) {
                        plugin.combatManager?.takeDamage(player)
                        player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                        task.cancel()
                        return@run
                    }

                    player.world.getNearbyPlayers(player.location, 2.0).firstOrNull { esObjetivoValido(player, it) }?.let { victim ->
                        victim.scheduler.run(plugin, { _ ->
                            plugin.combatManager?.takeDamage(victim)
                            victim.velocity = player.location.direction.normalize().multiply(1.5).setY(0.4)
                            player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f)
                        }, null)
                        duration = 0
                    }
                }, null)
                duration--
            }, 1L, 1L)
            trackTask(task)
        }, null)
    }

    private fun habilidadAdminVision(player: Player) {
        val teamName = "admin_glow"
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("AdminTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.NONE
        )

        player.world.getNearbyPlayers(player.location, 100.0).forEach { victim ->
            if (esObjetivoValido(player, victim)) {
                val createTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, listOf(victim.name))
                PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)
                val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
                PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(victim.entityId, metadata))
            }
        }

        player.scheduler.runDelayed(plugin, { _ ->
            if (player.isOnline) {
                val removeTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, null, 200L)
    }

    private fun habilidadTripleColmillo(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val startLoc = player.location
            val angles = listOf(-25.0, 0.0, 25.0)

            angles.forEach { offset ->
                val direction = startLoc.direction.clone().rotateAroundY(Math.toRadians(offset)).setY(0.0).normalize()
                val currentLoc = startLoc.clone()

                for (i in 0 until 15) {
                    currentLoc.add(direction)
                    val locToSpawn = currentLoc.clone()
                    plugin.server.regionScheduler.runDelayed(plugin, locToSpawn, { _ ->
                        if (!locToSpawn.block.type.isSolid) {
                            locToSpawn.world.spawn(locToSpawn, EvokerFangs::class.java)
                            locToSpawn.world.getNearbyPlayers(locToSpawn, 1.5).forEach { victim ->
                                if (esObjetivoValido(player, victim)) {
                                    victim.scheduler.run(plugin, { _ ->
                                        plugin.combatManager?.takeDamage(victim)
                                        victim.velocity = Vector(0.0, 0.5, 0.0)
                                        victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                                    }, null)
                                }
                            }
                        }
                    }, (i * 2 + 1).toLong())
                }
            }
        }, null)
    }

    private fun habilidadNetherStar(player: Player) {
        val startLoc = player.eyeLocation
        val direction = player.location.direction.multiply(1.5)

        plugin.server.regionScheduler.run(plugin, startLoc, { _ ->
            val star = startLoc.world.spawn(startLoc, ItemDisplay::class.java) {
                it.setItemStack(ItemStack(Material.NETHER_STAR))
                it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.7f, 0.7f, 0.7f), Quaternionf())
                it.interpolationDuration = 1; it.teleportDuration = 1
            }

            var ticks = 0
            val task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (ticks >= 40 || !star.isValid) {
                    if (star.isValid) star.remove()
                    task.cancel()
                    return@Consumer
                }

                val nextLoc = star.location.add(direction)
                star.teleport(nextLoc)

                val hit = nextLoc.world.getNearbyPlayers(nextLoc, 1.5).firstOrNull { esObjetivoValido(player, it) }

                if (hit != null || nextLoc.block.type.isSolid) {
                    nextLoc.world.spawnParticle(Particle.EXPLOSION_EMITTER, nextLoc, 1)
                    hit?.scheduler?.run(plugin, { _ -> plugin.combatManager?.takeDamage(hit) }, null)
                    star.remove()
                    task.cancel()
                }
                ticks++
            }, 1L, 1L)
            trackTask(task)
        })
    }

    override fun equipar(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val inv = player.inventory
            inv.clear()
            inv.armorContents = arrayOfNulls(4)

            val configMecanica = plugin.configManager.getAsesinos()
            val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")

            fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
                val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
                else configMecanica.getString("$pathBase.items.$key")

                if (id == null || id == "none") return

                val item = CraftEngineHook.getCustomItem(id) ?: run {
                    val matName = id.replace(".*:".toRegex(), "").uppercase()
                    val mat = Material.matchMaterial(matName)
                    if (mat != null) ItemStack(mat) else null
                } ?: return

                val namePath = if (key == "arma") "asesinos.romeo.habilidades_nombres.arma"
                else "asesinos.romeo.habilidades_nombres.$key"

                langInfo.getString(namePath)?.let {
                    item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
                }

                if (isArmor) {
                    when(key) {
                        "casco" -> inv.helmet = item
                        "pechera" -> inv.chestplate = item
                        "pantalones" -> inv.leggings = item
                        "botas" -> inv.boots = item
                    }
                } else inv.setItem(slot, item)
            }

            deliver("casco", 0, true); deliver("pechera", 0, true)
            deliver("pantalones", 0, true); deliver("botas", 0, true)
            deliver("habilidad1", 1); deliver("habilidad2", 2)
            deliver("habilidad3", 3); deliver("habilidad4", 4)
            deliver("arma", 8)

            player.inventory.heldItemSlot = 8
            player.updateInventory()
        }, null)
    }

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (plugin.asesinoManager?.esElAsesino(player) != true) { limpiarVisuales(uuid); return }

        val playerLoc = player.location
        if (orbitadores[uuid]?.world != playerLoc.world) limpiarVisuales(uuid)

        val display = orbitadores.getOrPut(uuid) {
            var bd: BlockDisplay? = null
            plugin.server.regionScheduler.run(plugin, playerLoc, { _ ->
                bd = playerLoc.world.spawn(playerLoc, BlockDisplay::class.java) {
                    it.block = Material.COMMAND_BLOCK.createBlockData()
                    it.transformation = Transformation(
                        JomlVector3f(-0.2f, -0.2f, -0.2f),
                        Quaternionf(),
                        JomlVector3f(0.4f, 0.4f, 0.4f),
                        Quaternionf()
                    )
                    it.teleportDuration = 3
                    it.interpolationDuration = 3
                }
            })
            bd ?: return // Retorna este tick, al siguiente ya estará instanciado
        }

        if (!display.isValid) return

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val radio = 1.5

        val x = radio * cos(angulo)
        val z = radio * sin(angulo)
        val y = 1.2 + (0.2 * sin(angulo * 2))

        val targetLoc = playerLoc.clone().add(x, y, z)
        targetLoc.yaw = (angulo * 120).toFloat() % 360
        targetLoc.pitch = (angulo * 60).toFloat() % 360

        plugin.server.regionScheduler.run(plugin, targetLoc, { _ ->
            display.teleport(targetLoc)
        })
        angulos[uuid] = angulo
    }

    override fun mostrarTrail(player: Player) {}

    private fun limpiarVisuales(uuid: UUID) {
        val bd = orbitadores.remove(uuid) ?: return
        plugin.server.regionScheduler.run(plugin, bd.location, { _ -> bd.remove() })
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiarVisuales(it.uniqueId)
            lastKillEffect.remove(it.uniqueId)
        }
    }
}
