package liric.mistaken.game.managers.visual

import liric.mistaken.Mistaken
import liric.mistaken.roles.asesinos.Asesino
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.EulerAngle
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class CinematicManager(private val plugin: Mistaken) {

    private val activeDisplays = ConcurrentHashMap.newKeySet<Entity>()

    // 🔥 EL ESCUDO ANTI-SPAM DE CONSOLA 🔥
    // Esta función evita que Bukkit llore si intenta mover la cámara de alguien que no está en espectador.
    private fun safeSetSpectatorTarget(player: Player, target: Entity?) {
        if (!player.isOnline) return
        if (player.gameMode == GameMode.SPECTATOR) {
            try {
                if (player.spectatorTarget != target) {
                    player.spectatorTarget = target
                }
            } catch (ignored: Exception) {
                // Silencioso. Evita el spam masivo de "Player must be in spectator mode"
            }
        }
    }

    // =========================================================================================
    // =                                   INTRO DEL ASESINO                                   =
    // =========================================================================================

    fun playKillerIntro(killer: Player, asesino: Asesino) {
        val id = asesino.id.lowercase()
        val isFloating = id in listOf("romeo", "romeodebuff", "miku", "entity303", "devesto")
        val yOffset = if (id == "charlie") 15.0 else if (isFloating) 2.5 else 0.0
        val centerLoc = killer.location.clone().add(0.0, yOffset, 0.0)

        val visualDummy = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) { dummy ->
            dummy.isInvisible = false
            dummy.setGravity(false)
            dummy.isMarker = true
            dummy.setArms(true)
            dummy.setBasePlate(false)
            aplicarPose(dummy, id, isIntro = true)
            aplicarEquipamientoCustom(killer, dummy, id, isIntro = true)
        }

        val cameraAnchor = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) {
            it.isInvisible = true; it.setGravity(false); it.isMarker = true
        }

        val titlePair = obtenerTextosIntro(id, asesino.nombre)
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(6), Duration.ofMillis(1000))

        val locacionesOriginales = mutableMapOf<UUID, Location>()

        plugin.server.onlinePlayers.forEach { p ->
            locacionesOriginales[p.uniqueId] = p.location.clone()

            p.gameMode = GameMode.SPECTATOR
            val targetTp = centerLoc.clone()
            targetTp.y -= yOffset

            p.teleportAsync(targetTp).thenAccept {
                p.scheduler.runDelayed(plugin, Consumer { _ ->
                    if (p.isOnline) {
                        safeSetSpectatorTarget(p, cameraAnchor)
                        p.showTitle(Title.title(titlePair.first, titlePair.second, times))
                    }
                }, null, 3L)
            }

            if (p == killer) p.isInvisible = true
        }

        val fxLoc = centerLoc.clone(); fxLoc.y -= yOffset
        ejecutarEfectos(asesino, fxLoc, visualDummy, isIntro = true)

        iniciarCamaraDinamica(killer, cameraAnchor, visualDummy, fxLoc, id, isIntro = true, duracionTicks = 160) {
            plugin.server.onlinePlayers.forEach { p ->
                locacionesOriginales[p.uniqueId]?.let { origLoc ->
                    p.teleportAsync(origLoc)
                }
            }
        }
    }

    // =========================================================================================
    // =                                   OUTRO DEL ASESINO                                   =
    // =========================================================================================

    fun playKillerOutro(killer: Player, asesino: Asesino) {
        val id = asesino.id.lowercase()
        val isFloating = id in listOf("romeo", "romeodebuff", "miku", "entity303", "devesto")
        val centerLoc = killer.location.clone().add(0.0, if (isFloating) 2.5 else 0.0, 0.0)

        val visualDummy = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) { dummy ->
            dummy.isInvisible = false
            dummy.setGravity(false)
            dummy.isMarker = true
            dummy.setArms(true)
            dummy.setBasePlate(false)
            aplicarPose(dummy, id, isIntro = false)
            aplicarEquipamientoCustom(killer, dummy, id, isIntro = false)
        }

        val cameraAnchor = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) {
            it.isInvisible = true; it.setGravity(false); it.isMarker = true
        }

        val titlePair = obtenerTextosOutro(id, asesino.nombre)
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(8), Duration.ofMillis(1000))

        plugin.server.onlinePlayers.forEach { p ->
            p.gameMode = GameMode.SPECTATOR
            p.teleportAsync(centerLoc).thenAccept {
                p.scheduler.runDelayed(plugin, Consumer { _ ->
                    if (p.isOnline) {
                        safeSetSpectatorTarget(p, cameraAnchor)
                        p.showTitle(Title.title(titlePair.first, titlePair.second, times))
                    }
                }, null, 3L)
            }

            if (p == killer) p.isInvisible = true
        }

        ejecutarEfectos(asesino, centerLoc, visualDummy, isIntro = false)

        iniciarCamaraDinamica(killer, cameraAnchor, visualDummy, centerLoc, id, isIntro = false, duracionTicks = 200) {}
    }

    // =========================================================================================
    // =                           MOVIMIENTOS DE CÁMARA DINÁMICOS                             =
    // =========================================================================================

    private fun iniciarCamaraDinamica(killer: Player, anchor: ArmorStand, dummy: ArmorStand, center: Location, id: String, isIntro: Boolean, duracionTicks: Int, onFinish: () -> Unit) {
        var ticks = 0

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= duracionTicks || !killer.isOnline || !anchor.isValid) {
                plugin.server.onlinePlayers.forEach {
                    safeSetSpectatorTarget(it, null)
                    it.removePotionEffect(PotionEffectType.NAUSEA)
                    it.removePotionEffect(PotionEffectType.BLINDNESS)
                    if (it == killer) it.isInvisible = false
                }
                anchor.remove()
                dummy.remove()
                activeDisplays.forEach { if (it.isValid) it.remove() }
                activeDisplays.clear()

                onFinish()
                task.cancel()
                return@Consumer
            }

            // ANTI-SHIFT SEGURO
            plugin.server.onlinePlayers.forEach { p ->
                safeSetSpectatorTarget(p, anchor)
            }

            val camLoc = center.clone()

            // 🔥 FIX SET DIRECTION
            when (id) {
                "slasher" -> {
                    val dist = 3.0
                    camLoc.add(0.0, 0.2, dist)
                    camLoc.setDirection(center.clone().add(0.0, 1.8, 0.0).toVector().subtract(camLoc.toVector()))
                    // 🔥 FIX SET ROTATION
                    if (!isIntro) dummy.setRotation((ticks * 0.5f) % 360, dummy.location.pitch)
                }

                "charlie" -> {
                    if (isIntro) {
                        val dropY = Math.max(0.0, 15.0 - (ticks * 0.5))
                        dummy.teleport(center.clone().add(0.0, dropY, 0.0))
                        camLoc.add(4.0, dropY + 1.0, 4.0)
                        camLoc.setDirection(
                            dummy.location.clone().add(0.0, 1.0, 0.0).toVector().subtract(camLoc.toVector())
                        )
                    } else {
                        val dist = 3.0 + (ticks * 0.05)
                        camLoc.add(0.0, 1.5 + (ticks * 0.01), dist)
                        camLoc.setDirection(center.clone().add(0.0, 1.0, 0.0).toVector().subtract(camLoc.toVector()))
                    }
                }

                "error_estatico" -> {
                    if (!isIntro && ticks > 100) {
                        val dist = Math.max(0.5, 4.0 - ((ticks - 100) * 0.3))
                        camLoc.add(0.0, 1.5, dist)
                        camLoc.setDirection(center.clone().add(0.0, 1.5, 0.0).toVector().subtract(camLoc.toVector()))

                        if (ticks == 115) plugin.server.onlinePlayers.forEach {
                            it.addPotionEffect(
                                PotionEffect(
                                    PotionEffectType.BLINDNESS,
                                    100,
                                    0,
                                    false,
                                    false,
                                    false
                                )
                            )
                        }
                    } else {
                        camLoc.add(0.0, 1.5, 4.0)
                        camLoc.setDirection(center.clone().add(0.0, 1.5, 0.0).toVector().subtract(camLoc.toVector()))
                    }
                }

                "colorandelectricity", "colorsito" -> {
                    if (!isIntro) {
                        val angulo = ticks * 0.08
                        val radio = 3.0 + sin(ticks * 0.1) * 2.0
                        camLoc.add(radio * cos(angulo), 2.0 + sin(ticks * 0.2), radio * sin(angulo))
                        camLoc.setDirection(center.clone().add(0.0, 1.0, 0.0).toVector().subtract(camLoc.toVector()))

                        if (ticks == 10) plugin.server.onlinePlayers.forEach {
                            it.addPotionEffect(
                                PotionEffect(
                                    PotionEffectType.NAUSEA,
                                    200,
                                    2,
                                    false,
                                    false,
                                    false
                                )
                            )
                        }
                    } else {
                        val angulo = ticks * 0.04
                        val radio = 4.0
                        camLoc.add(radio * cos(angulo), 1.5, radio * sin(angulo))
                        camLoc.setDirection(center.clone().add(0.0, 1.0, 0.0).toVector().subtract(camLoc.toVector()))
                    }
                }

                "devesto" -> {
                    if (isIntro) {
                        camLoc.add(0.0, 1.0 + (ticks * 0.02), 5.0)
                        camLoc.setDirection(center.clone().add(0.0, 2.5, 0.0).toVector().subtract(camLoc.toVector()))
                    } else {
                        val altura = Math.min(10.0, 4.0 + (ticks * 0.1))
                        camLoc.add(0.0, altura, 1.0)
                        camLoc.setDirection(center.clone().add(0.0, 2.5, 0.0).toVector().subtract(camLoc.toVector()))
                    }
                }

                "miku", "teto" -> {
                    val angulo = Math.PI / 2 + sin(ticks * 0.02) * 2.0
                    val radio = 6.0
                    camLoc.add(radio * cos(angulo), 2.5, radio * sin(angulo))
                    camLoc.setDirection(center.clone().add(0.0, 2.5, 0.0).toVector().subtract(camLoc.toVector()))
                }

                "null", "nullasesino" -> {
                    val dist = 5.0 - (ticks * 0.02)
                    camLoc.add(0.0, 1.0, dist)
                    camLoc.setDirection(center.clone().add(0.0, 1.0, 0.0).toVector().subtract(camLoc.toVector()))
                    if (!isIntro && ticks > 120) plugin.server.onlinePlayers.forEach {
                        it.addPotionEffect(
                            PotionEffect(
                                PotionEffectType.BLINDNESS,
                                100,
                                0
                            )
                        )
                    }
                }

                else -> {
                    val angulo = ticks * 0.04
                    val radio = 6.5
                    camLoc.add(radio * cos(angulo), 2.5 + (ticks * 0.005), radio * sin(angulo))
                    camLoc.setDirection(center.clone().add(0.0, 1.2, 0.0).toVector().subtract(camLoc.toVector()))
                }
            }

            anchor.teleport(camLoc)
            ticks++
        }, 1L, 1L)
    }

    // =========================================================================================
    // =                                POSES Y EQUIPAMIENTO                                   =
    // =========================================================================================

    private fun aplicarPose(dummy: ArmorStand, id: String, isIntro: Boolean) {
        when (id) {
            "sowoul" -> {
                if (isIntro) {
                    dummy.leftArmPose = EulerAngle(Math.toRadians(-60.0), Math.toRadians(-45.0), 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-60.0), Math.toRadians(45.0), 0.0)
                } else {
                    dummy.bodyPose = EulerAngle(Math.toRadians(20.0), 0.0, 0.0)
                    dummy.headPose = EulerAngle(Math.toRadians(30.0), 0.0, 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(-45.0), 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(45.0), 0.0, 0.0)
                }
            }
            "slasher" -> {
                if (isIntro) {
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
                } else {
                    dummy.headPose = EulerAngle(Math.toRadians(-20.0), 0.0, 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-160.0), 0.0, 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(-160.0), 0.0, 0.0)
                }
            }
            "colorandelectricity", "colorsito" -> {
                if (isIntro) {
                    dummy.headPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-120.0), Math.toRadians(-30.0), 0.0)
                } else {
                    dummy.headPose = EulerAngle(Math.toRadians(45.0), 0.0, 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-140.0), Math.toRadians(-45.0), 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(-140.0), Math.toRadians(45.0), 0.0)
                }
            }
            "charlie", "charlieinferno" -> {
                if (isIntro) {
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-180.0), Math.toRadians(45.0), 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(-180.0), Math.toRadians(-45.0), 0.0)
                    dummy.rightLegPose = EulerAngle(Math.toRadians(20.0), 0.0, Math.toRadians(20.0))
                    dummy.leftLegPose = EulerAngle(Math.toRadians(20.0), 0.0, Math.toRadians(-20.0))
                } else {
                    dummy.headPose = EulerAngle(Math.toRadians(40.0), 0.0, 0.0)
                    dummy.bodyPose = EulerAngle(Math.toRadians(15.0), 0.0, 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
                }
            }
            "error_estatico" -> {
                if (!isIntro) {
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-90.0), 0.0, 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(-90.0), 0.0, 0.0)
                }
            }
            "romeo", "romeodebuff", "devesto" -> {
                dummy.rightArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(-45.0), 0.0)
                dummy.leftArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(45.0), 0.0)
            }
            "mariachi" -> {
                dummy.rightArmPose = EulerAngle(Math.toRadians(-60.0), Math.toRadians(-30.0), 0.0)
                dummy.leftArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(45.0), 0.0)
            }
            "miku", "teto" -> {
                dummy.rightArmPose = EulerAngle(Math.toRadians(-120.0), Math.toRadians(-20.0), 0.0)
                dummy.leftArmPose = EulerAngle(Math.toRadians(-30.0), Math.toRadians(30.0), 0.0)
            }
            else -> {
                dummy.leftArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
                dummy.rightArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
            }
        }
    }

    private fun aplicarEquipamientoCustom(killer: Player, dummy: ArmorStand, id: String, isIntro: Boolean) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)

        when (id) {
            "slasher" -> {
                if (!isIntro) {
                    dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.FLINT))
                    dummy.setItem(EquipmentSlot.OFF_HAND, ItemStack(Material.IRON_INGOT))
                } else {
                    dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.DIAMOND_AXE))
                }
            }
            "colorandelectricity", "colorsito" -> {
                if (isIntro) dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.TRIDENT))
            }
            "error_estatico" -> {
                if (isIntro) dummy.isInvisible = true
                else dummy.setItem(EquipmentSlot.HAND, inv.itemInMainHand)
            }
            "mariachi" -> dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.NOTE_BLOCK))
            "teto" -> dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.BREAD))
            "coolkid" -> dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.STICK))
            "devesto" -> dummy.setItem(EquipmentSlot.HAND, ItemStack(Material.WOODEN_AXE))
            else -> {
                if (id != "sowoul" && id != "pizzano") dummy.setItem(EquipmentSlot.HAND, inv.itemInMainHand)
            }
        }
    }

    // =========================================================================================
    // =                                  EFECTOS VISUALES                                     =
    // =========================================================================================

    private fun ejecutarEfectos(asesino: Asesino, loc: Location, dummy: ArmorStand, isIntro: Boolean) {
        val world = loc.world ?: return
        val id = asesino.id.lowercase()

        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)
        val dialogos = obtenerDialogos(id, isIntro)

        var tickDial = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!dummy.isValid) {
                task.cancel(); return@Consumer
            }
            val index = (tickDial / 40) % dialogos.size
            if (tickDial < dialogos.size * 40) {
                plugin.server.onlinePlayers.forEach { it.sendActionBar(plugin.mm.deserialize(dialogos[index])) }
            }
            tickDial++
        }, 1L, 1L)

        when (id) {
            "charlie", "charlieinferno" -> {
                if (isIntro) {
                    world.playSound(loc, Sound.ENTITY_GHAST_SCREAM, 2f, 0.5f)
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) {
                            task.cancel(); return@Consumer
                        }
                        world.spawnParticle(Particle.FLAME, dummy.location.add(0.0, 1.0, 0.0), 20, 0.5, 1.0, 0.5, 0.0)
                    }, 1L, 2L)
                } else {
                    world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 2f, 1f)
                    world.playSound(loc, Sound.MUSIC_DISC_11, 1f, 0.5f)
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) {
                            task.cancel(); return@Consumer
                        }
                        val angle = Math.random() * Math.PI * 2
                        val radius = Math.random() * 5 + 2
                        val pLoc = loc.clone().add(radius * cos(angle), 0.0, radius * sin(angle))
                        world.spawnParticle(Particle.FLAME, pLoc, 10, 0.2, 2.0, 0.2, 0.1)
                        world.spawnParticle(Particle.LAVA, pLoc, 2)
                    }, 1L, 2L)
                }
            }
            "colorandelectricity", "colorsito" -> {
                if (isIntro) {
                    world.playSound(loc, Sound.ENTITY_PLAYER_BURP, 2f, 1f)
                    val colors = listOf(Color.RED, Color.YELLOW, Color.AQUA, Color.LIME)
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) {
                            task.cancel(); return@Consumer
                        }
                        world.spawnParticle(
                            Particle.DUST,
                            dummy.location.clone().add(0.0, 1.5, 0.0),
                            10,
                            0.2,
                            0.2,
                            0.2,
                            Particle.DustOptions(colors.random(), 1.5f)
                        )
                    }, 1L, 5L)
                } else {
                    world.playSound(loc, Sound.ENTITY_GHAST_WARN, 1f, 0.5f)
                    world.playSound(loc, Sound.ENTITY_WITCH_DRINK, 1f, 0.8f)
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) {
                            task.cancel(); return@Consumer
                        }
                        world.spawnParticle(Particle.SPLASH, dummy.location.add(0.0, 1.6, 0.0), 10, 0.1, 0.0, 0.1, 0.1)
                        world.spawnParticle(
                            Particle.DUST,
                            dummy.location.add(0.0, 1.0, 0.0),
                            20,
                            0.5,
                            0.5,
                            0.5,
                            Particle.DustOptions(Color.RED, 2f)
                        )
                    }, 1L, 2L)
                }
            }
            "slasher" -> {
                if (!isIntro) {
                    world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)
                    spawnRotatingItem(loc.clone().add(-1.0, 2.0, 0.0), Material.IRON_INGOT, 1.5f)
                    spawnRotatingItem(loc.clone().add(1.0, 2.0, 0.0), Material.FLINT, 1.5f)
                } else {
                    world.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f)
                }
            }
            "error_estatico" -> {
                if (isIntro) {
                    world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 0.1f)
                    spawnStaticBlock(loc.clone().add(0.0, 1.0, 0.0), Material.SEA_LANTERN, 1.5f)
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) {
                            task.cancel(); return@Consumer
                        }
                        world.spawnParticle(
                            Particle.DUST,
                            loc.clone().add(0.0, 1.5, 0.0),
                            10,
                            0.5,
                            0.5,
                            0.5,
                            Particle.DustOptions(Color.FUCHSIA, 1f)
                        )
                    }, 1L, 2L)
                } else {
                    world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.1f)
                    spawnGlitchBlock(loc, Material.BLACK_CONCRETE)

                    // 🔥 FIX: runDelayed con el 3er parámetro Long
                    plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
                        world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f)
                    }, 100L)
                }
            }
            "devesto" -> {
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)
                if (!isIntro) {
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) {
                            task.cancel(); return@Consumer
                        }
                        world.spawnParticle(
                            Particle.BLOCK_MARKER,
                            loc.clone().add(Math.random() * 10 - 5, Math.random() * 5, Math.random() * 10 - 5),
                            1,
                            Material.BARRIER.createBlockData()
                        )
                        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.5f, 0.5f)
                    }, 1L, 5L)
                }
            }
            "miku" -> {
                world.playSound(loc, Sound.MUSIC_DISC_5, 1f, 1.5f)
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) {
                        task.cancel(); return@Consumer
                    }
                    world.spawnParticle(
                        Particle.END_ROD,
                        dummy.location.clone().add(0.0, 1.5, 0.0),
                        10,
                        5.0,
                        0.1,
                        5.0,
                        0.0
                    )
                    world.spawnParticle(Particle.DUST, loc, 20, 3.0, 3.0, 3.0, Particle.DustOptions(Color.AQUA, 1.5f))
                }, 1L, 2L)
            }
            "teto" -> {
                world.playSound(loc, Sound.ENTITY_GHAST_SCREAM, 0.5f, 2f)
                spawnRotatingItem(loc.clone().add(0.0, 2.0, 0.0), Material.BREAD, 2.0f)
                world.spawnParticle(Particle.DUST, loc, 200, 3.0, 3.0, 3.0, Particle.DustOptions(Color.RED, 1.5f))
            }
            "mariachi" -> {
                world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 2f, 1f)
                world.spawnParticle(Particle.NOTE, loc.clone().add(0.0, 2.0, 0.0), 100, 2.0, 2.0, 2.0, 1.0)
                if (!isIntro) spawnRotatingItem(loc.clone().add(0.0, 2.5, 0.0), Material.HONEY_BOTTLE, 2f)
            }
            "coolkid" -> {
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f)
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) {
                        task.cancel(); return@Consumer
                    }
                    val codeLoc = loc.clone().add(Math.random() * 6 - 3, Math.random() * 5, Math.random() * 6 - 3)
                    world.spawnParticle(Particle.DUST, codeLoc, 1, Particle.DustOptions(Color.LIME, 1.5f))
                }, 1L, 1L)
            }
            "bendy" -> {
                world.playSound(loc, Sound.ENTITY_SQUID_SQUIRT, 2f, 0.5f)
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) {
                        task.cancel(); return@Consumer
                    }
                    world.spawnParticle(Particle.SQUID_INK, loc.clone().add(0.0, 0.5, 0.0), 10, 2.0, 0.2, 2.0, 0.0)
                    world.spawnParticle(
                        Particle.FALLING_OBSIDIAN_TEAR,
                        loc.clone().add(0.0, 3.0, 0.0),
                        5,
                        0.5,
                        0.5,
                        0.5,
                        0.0
                    )
                }, 1L, 2L)
            }
            "romeo", "romeodebuff" -> {
                world.playSound(loc, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 2f)
                spawnOrbitingBlock(loc, Material.COMMAND_BLOCK, 2.0f, 4.0, 0.05, 1.0)
                spawnOrbitingBlock(loc, Material.BEDROCK, 1.5f, 3.5, -0.06, -1.0)
            }
            "pizzano" -> {
                if (isIntro) {
                    world.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1.5f, 1f)
                    world.spawnParticle(Particle.CLOUD, loc, 100, 1.0, 1.0, 1.0, 0.5)
                } else {
                    world.playSound(loc, Sound.ENTITY_PLAYER_BURP, 1.5f, 0.8f)
                    spawnRotatingItem(loc.clone().add(1.5, 1.0, 0.0), Material.COOKIE, 1.5f)
                    spawnRotatingItem(loc.clone().add(-1.5, 1.5, 0.0), Material.SUGAR, 1.5f)
                }
            }
            "entity303" -> {
                world.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 2f)
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) {
                        task.cancel(); return@Consumer
                    }
                    if (Math.random() > 0.85) {
                        val tntLoc =
                            loc.clone().add(Math.random() * 10 - 5, Math.random() * 6 + 2, Math.random() * 10 - 5)
                        spawnGlitchBlock(tntLoc, Material.TNT)
                        world.playSound(tntLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f)
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, tntLoc, 1)
                    }
                }, 1L, 5L)
            }
            "sowoul" -> {
                if (isIntro) {
                    world.playSound(loc, Sound.ITEM_TRIDENT_RETURN, 2f, 1f)
                    world.spawnParticle(Particle.WITCH, loc, 100, 2.0, 1.0, 2.0, 0.1)
                } else {
                    world.playSound(loc, Sound.ENTITY_VILLAGER_CELEBRATE, 2f, 1f)
                    world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 2f, 1f)
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) {
                            task.cancel(); return@Consumer
                        }
                        val roseLoc = loc.clone().add(Math.random() * 8 - 4, 6.0, Math.random() * 8 - 4)
                        spawnFallingItem(roseLoc, Material.POPPY)
                    }, 1L, 5L)
                }
            }
            "null", "nullasesino" -> {
                world.playSound(loc, Sound.AMBIENT_CAVE, 2f, 0.5f)
                var dTicks = 0
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) {
                        task.cancel(); return@Consumer
                    }
                    world.spawnParticle(
                        Particle.SQUID_INK,
                        loc.clone().add(0.0, dTicks * 0.02, 0.0),
                        20,
                        1.5,
                        0.5,
                        1.5,
                        0.0
                    )
                    world.spawnParticle(Particle.SMOKE, loc, 30, 2.0, 2.0, 2.0, 0.05)
                    if (!isIntro && dTicks == 100) dummy.isInvisible = true
                    dTicks++
                }, 1L, 1L)
            }
            "herobrine" -> {
                world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)
                world.strikeLightningEffect(loc)
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 200, 2.0, 2.0, 2.0, 0.1)
            }
            else -> spawnRotatingItem(loc.clone().add(0.0, 2.0, 0.0), Material.NETHER_STAR, 2.0f)
        }
    }

    // =========================================================================================
    // =                                       TEXTOS                                          =
    // =========================================================================================

    private fun obtenerTextosIntro(id: String, nombreReal: String): Pair<Component, Component> {
        return when (id) {
            "sowoul" -> Pair(plugin.mm.deserialize("<dark_purple>EL MAGO HA LLEGADO"), plugin.mm.deserialize("<light_purple>Que comience el espectáculo..."))
            "charlie", "charlieinferno" -> Pair(plugin.mm.deserialize("<gold>CAÍDO DEL CIELO"), plugin.mm.deserialize("<red>Bienvenido a mi infierno."))
            "colorandelectricity", "colorsito" -> Pair(plugin.mm.deserialize("<aqua>COLOR & ELECTRICITY"), plugin.mm.deserialize("<yellow>Mmm... deliciosos colores..."))
            "error_estatico" -> Pair(plugin.mm.deserialize("<white><obfuscated>||</obfuscated> ERROR <obfuscated>||</obfuscated>"), plugin.mm.deserialize("<gray>S1st3m4 C0rrupt0..."))
            "slasher" -> Pair(plugin.mm.deserialize("<dark_red>LA EJECUCIÓN"), plugin.mm.deserialize("<red>Nadie escapa de White Pumpkin."))
            "miku" -> Pair(plugin.mm.deserialize("<aqua>DOMINACIÓN MUNDIAL</aqua>"), plugin.mm.deserialize("<white>¡El mundo es mío!"))
            "teto" -> Pair(plugin.mm.deserialize("<red>TERRITORY</red>"), plugin.mm.deserialize("<white>¡Eres tan tonto!"))
            "bendy" -> Pair(plugin.mm.deserialize("<black><bold>LA TINTA LLAMA</bold>"), plugin.mm.deserialize("<dark_gray>Él ha sido liberado..."))
            "devesto" -> Pair(plugin.mm.deserialize("<dark_purple><bold>[F3X]</bold>"), plugin.mm.deserialize("<gray>Cargando herramientas de construcción..."))
            "coolkid" -> Pair(plugin.mm.deserialize("<green><bold>CONNECTION ESTABLISHED</bold>"), plugin.mm.deserialize("<gray>Inyectando paquetes malignos..."))
            "mariachi" -> Pair(plugin.mm.deserialize("<red><bold>EL CHARRO NEGRO</bold>"), plugin.mm.deserialize("<gold>¡Ay ay ay! Canta y no llores..."))
            "entity303" -> Pair(plugin.mm.deserialize("<red><bold>ERROR CRÍTICO</bold>"), plugin.mm.deserialize("<dark_red>SYSTEM ERROR: 303 FOUND"))
            "romeo", "romeodebuff" -> Pair(plugin.mm.deserialize("<dark_red>EL ADMINISTRADOR"), plugin.mm.deserialize("<red>Este mundo me pertenece."))
            else -> Pair(plugin.mm.deserialize("<red>LA CAZA COMIENZA"), plugin.mm.deserialize("<gray>El Asesino es: $nombreReal"))
        }
    }

    private fun obtenerTextosOutro(id: String, nombreReal: String): Pair<Component, Component> {
        return when (id) {
            "slasher" -> Pair(plugin.mm.deserialize("<dark_red><bold>¡LO TENGO!</bold>"), plugin.mm.deserialize("<red>Por fin... mi pedernal y acero."))
            "colorandelectricity", "colorsito" -> Pair(plugin.mm.deserialize("<dark_red><bold>¡QUÉ HE HECHO!</bold>"), plugin.mm.deserialize("<red>M-Mi color... todo se deforma..."))
            "charlie", "charlieinferno" -> Pair(plugin.mm.deserialize("<dark_red><bold>CENIZAS</bold>"), plugin.mm.deserialize("<gold>¿Es este el fin de Charlie?"))
            "error_estatico" -> Pair(plugin.mm.deserialize("<dark_aqua><obfuscated>|</obfuscated> <aqua>STATIC</aqua> <dark_aqua><obfuscated>|</obfuscated>"), plugin.mm.deserialize("<gray>T H I S   I S   H O W   I T   S H O U L D   B E."))
            "devesto" -> Pair(plugin.mm.deserialize("<dark_purple><bold>//SET 0</bold>"), plugin.mm.deserialize("<gray>Borrado exitoso."))
            "coolkid" -> Pair(plugin.mm.deserialize("<green><bold>CONNECTION TERMINATED</bold>"), plugin.mm.deserialize("<gray>El servidor ha sido desconectado."))
            "miku" -> Pair(plugin.mm.deserialize("<aqua><bold>CONCIERTO FINAL</bold>"), plugin.mm.deserialize("<white>¡Gracias a todos por venir!"))
            "bendy" -> Pair(plugin.mm.deserialize("<black><bold>CONSUMIDOS</bold>"), plugin.mm.deserialize("<dark_gray>El estudio reclamó lo que es suyo."))
            "mariachi" -> Pair(plugin.mm.deserialize("<gold><bold>¡SALUD!</bold>"), plugin.mm.deserialize("<yellow>*Toma un trago de tequila*"))
            else -> Pair(plugin.mm.deserialize("<dark_red><bold>¡MASCARADA FINAL!</bold>"), plugin.mm.deserialize("<gray><b>$nombreReal</b> <white>ha reclamado todas las almas."))
        }
    }

    private fun obtenerDialogos(id: String, isIntro: Boolean): List<String> {
        return when (id) {
            "slasher" -> if (isIntro) listOf("<dark_red>Más sangre para mi hacha...", "<red>Griten todo lo que quieran.") else listOf("<white>¡JAJAJAJA!", "<white>¡El pedernal y acero por fin es MÍO!")
            "colorandelectricity", "colorsito" -> if (isIntro) listOf("<yellow>*Comiendo colores rápidamente*", "<aqua>Aún tengo hambre...") else listOf("<red>Oh no... m-me comí a uno...", "<dark_red>M-Me estoy volviendo rojo... quiero ser normal...")
            "charlie", "charlieinferno" -> if (isIntro) listOf("<gold>Charlie ha aterrizado.", "<red>¡Y ustedes arderán conmigo!") else listOf("<gold>Todo arde...", "<dark_gray>¿Acaso este es el final del camino?")
            "error_estatico" -> if (isIntro) listOf("<gray>*Estática de Televisión*", "<white>E-E-Error 404.") else listOf("<aqua>Cerrando sistema.", "<dark_gray>This is how it should be.")
            "sowoul" -> if (isIntro) listOf("<light_purple>Damas y caballeros...", "<light_purple>¡Bienvenidos a la función final!") else listOf("<light_purple>¡Muchas gracias audiencia!", "<light_purple>¡Gracias por ver mi gran espectáculo!")
            "pizzano" -> if (isIntro) listOf("<yellow>¡Demasiada energía!", "<gold>¡Corran, corran, corran!") else listOf("<yellow>Ufff... eso fue un buen ejercicio.", "<gold>¿Alguien tiene un poco de azúcar?")
            "romeo", "romeodebuff" -> if (isIntro) listOf("<dark_red>¿Creen que pueden vencerme?", "<red>Yo escribí las reglas de este mundo.") else listOf("<dark_red>Patético.", "<red>Nadie supera a un Admin.")
            "entity303" -> listOf("<gray><obfuscated>xd</obfuscated> D3str0y_W0rld.exe Iniciado...", "<dark_red>Su existencia ha sido borrada.")
            "null", "nullasesino" -> listOf("<dark_gray>...", "<black>...")
            "devesto" -> if (isIntro) listOf("<blue>Cargando esquemas...", "<dark_purple>Listos para purgar.") else listOf("<dark_purple>Control, Alt...", "<blue>DELETE.")
            "miku" -> if (isIntro) listOf("<aqua>¿Están listos para cantar?", "<white>¡Aki Miku-chan!") else listOf("<aqua>¡Miku Miku BEEEEEEAAAM!", "<white>Nos vemos en la próxima función~")
            "teto" -> if (isIntro) listOf("<red>¡Baka baka baka!", "<white>El pan es mío.") else listOf("<red>¿Ves? Te lo dije.", "<white>Nadie me gana.")
            "coolkid" -> if (isIntro) listOf("<green>Iniciando ataque DDoS...", "<green>Ping a 9999ms.") else listOf("<green>Error Fatal 0x000000.", "<dark_green>Host Inalcanzable.")
            "mariachi" -> if (isIntro) listOf("<gold>¡Vamos a darle vuelo a la hilacha!", "<red>¡Yee-haw!") else listOf("<gold>Un buen tequila...", "<yellow>Para las penas del alma.")
            "bendy" -> if (isIntro) listOf("<black>T i n t a . . .", "<dark_gray>V e n  a q u í . . .") else listOf("<black>T o d o   e s   m í o .", "<dark_gray>J a j a j a . . .")
            else -> listOf("<red>No tienen escapatoria.", "<dark_red>Es el fin.")
        }
    }

    // --- FUNCIONES UTILS (Spawn Displays) ---
    private fun spawnStaticBlock(loc: Location, mat: Material, scale: Float): BlockDisplay {
        val display = loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                Vector3f(-scale / 2, 0f, -scale / 2),
                Quaternionf(),
                Vector3f(scale, scale, scale),
                Quaternionf()
            )
        }
        activeDisplays.add(display)
        return display
    }

    private fun spawnOrbitingBlock(center: Location, mat: Material, scale: Float, radius: Double, speed: Double, yOffset: Double) {
        val display = center.world.spawn(center, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(
                Vector3f(-scale / 2, 0f, -scale / 2),
                Quaternionf(),
                Vector3f(scale, scale, scale),
                Quaternionf()
            )
        }
        activeDisplays.add(display)
        var angle = Math.random() * Math.PI * 2
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) {
                task.cancel(); return@Consumer
            }
            angle += speed
            val offsetLoc =
                center.clone().add(radius * cos(angle), 2.0 + yOffset + sin(angle * 2) * 0.5, radius * sin(angle))
            // 🔥 FIX SET DIRECTION
            offsetLoc.setDirection(center.clone().add(0.0, 2.0, 0.0).toVector().subtract(offsetLoc.toVector()))
            display.teleport(offsetLoc)
        }, 1L, 1L)
    }

    private fun spawnRotatingItem(loc: Location, mat: Material, scale: Float) {
        val display = loc.world.spawn(loc.clone().add(0.0, 1.2, 0.0), ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(scale, scale, scale), Quaternionf())
        }
        activeDisplays.add(display)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) {
                task.cancel(); return@Consumer
            }
            val t = display.transformation; t.leftRotation.rotateY(0.1f); display.transformation = t
        }, 1L, 1L)
    }

    private fun spawnFallingItem(loc: Location, mat: Material) {
        val display = loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(0.8f, 0.8f, 0.8f), Quaternionf())
        }
        activeDisplays.add(display)
        var yOffset = 0.0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid || yOffset < -6.0) {
                display.remove(); task.cancel(); return@Consumer
            }
            yOffset -= 0.15
            val t = display.transformation; t.leftRotation.rotateX(0.2f).rotateY(0.1f)
            display.transformation = t; display.teleport(loc.clone().add(0.0, yOffset, 0.0))
        }, 1L, 1L)
    }

    private fun spawnGlitchBlock(loc: Location, mat: Material) {
        val display = loc.world.spawn(loc.clone().add(0.0, 1.2, 0.0), BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation =
                Transformation(Vector3f(-0.5f, 0f, -0.5f), Quaternionf(), Vector3f(1.0f, 1.0f, 1.0f), Quaternionf())
        }
        activeDisplays.add(display)
        var ticks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) {
                task.cancel(); return@Consumer
            }
            ticks++
            val s = if (ticks % 3 == 0) 1.5f else 0.8f
            display.transformation =
                Transformation(Vector3f(-s / 2, 0f, -s / 2), Quaternionf(), Vector3f(s, s, s), Quaternionf())
        }, 1L, 2L)
    }
}