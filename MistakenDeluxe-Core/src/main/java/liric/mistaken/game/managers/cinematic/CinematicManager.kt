package liric.mistaken.game.managers.cinematic

import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.profiles.*
import liric.mistaken.roles.killers.Killer
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID
import java.util.function.Consumer
import pumpking.lib.color.ColorTranslator
import liric.mistaken.game.managers.cinematic.CameraStyle
import liric.mistaken.utils.hooks.ObserverHook
import org.bukkit.Sound

class CinematicManager(private val plugin: Mistaken) {

    private val displayManager = DisplayManager(plugin)
    
    private val profiles = mutableMapOf<String, CinematicProfile>()
    private val defaultProfile = DefaultProfile()

    init {
        registerProfile(SlasherProfile())
        registerProfile(CharlieProfile())
        registerProfile(ColorAndElectricityProfile())
        registerProfile(ErrorStaticProfile())
        registerProfile(SowoulProfile())
        registerProfile(PizzanoProfile())
        registerProfile(RomeoProfile())
        registerProfile(Entity303Profile())
        registerProfile(DevestoProfile())
        registerProfile(MikuProfile())
        registerProfile(TetoProfile())
        registerProfile(MariachiProfile())
        registerProfile(CoolkidProfile())
        registerProfile(BendyProfile())
        registerProfile(NullProfile())
        registerProfile(HerobrineProfile())
        
        // Register aliases
        profiles["charlieinferno"] = CharlieProfile()
        profiles["colorsito"] = ColorAndElectricityProfile()
        profiles["romeodebuff"] = RomeoProfile()
        profiles["nullasesino"] = NullProfile()
    }

    private fun registerProfile(profile: CinematicProfile) {
        profiles[profile.id] = profile
    }

    private fun getProfile(id: String): CinematicProfile {
        return profiles[id.lowercase()] ?: defaultProfile
    }


    fun playKillerIntro(killer: Player, asesino: Killer, viewers: List<Player>) {
        val id = asesino.id.lowercase()
        val profile = getProfile(id)
        val duracionTicks = 160
        
        val yOffset = if (id == "charlie") 15.0 else if (profile.isFloating) 2.5 else 0.0
        val centerLoc = killer.location.clone().add(0.0, yOffset, 0.0)

        val visualDummy = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) { dummy ->
            dummy.isInvisible = false
            dummy.setGravity(false)
            dummy.isMarker = true
            dummy.setArms(true)
            dummy.setBasePlate(false)
            profile.applyPose(dummy, isIntro = true)
            profile.applyEquipment(killer, dummy, isIntro = true)
        }

        val titlePair = profile.getIntroTexts(plugin, asesino.nombre)
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(6), Duration.ofMillis(1000))

        val cameras = mutableListOf<VirtualCamera>()
        viewers.forEach { p ->
            p.showTitle(Title.title(titlePair.first, titlePair.second, times))
            if (p == killer) p.isInvisible = true
            
            val cam = VirtualCamera(p)
            // Initial position fallback, we will update it immediately in the task
            cam.startSpectating(centerLoc.clone().add(5.0, 1.5, 0.0))
            cameras.add(cam)
        }

        val fxLoc = centerLoc.clone(); fxLoc.y -= yOffset
        
        // Play visual effects
        profile.playEffects(plugin, fxLoc, visualDummy, isIntro = true, displayManager)

        // Híbrido: Efectos globales de Cinemática
        viewers.forEach { p ->
            ObserverHook.playScreenTint(p, 0, 0, 0, 0.7f, 60)
            ObserverHook.playScreenshake(p, 1.0f, 40)
            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.5f)
        }

        // Start cinematic orbit and dialogs
        val dialogos = profile.getDialogs(isIntro = true)
        var ticks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= duracionTicks || !visualDummy.isValid) {
                task.cancel()
                visualDummy.remove()
                killer.isInvisible = false
                cameras.forEach { it.stopSpectating() }
                return@Consumer
            }
            
            // Dialog logic
            if (dialogos.isNotEmpty()) {
                val index = (ticks / 40) % dialogos.size
                if (ticks < dialogos.size * 40) {
                    val msg = ColorTranslator.translate(dialogos[index])
                    viewers.forEach { it.sendActionBar(msg) }
                }
            }

            // Cinematic Dynamic Camera
            val progress = ticks.toDouble() / duracionTicks.toDouble()
            val camLoc = getCameraLocation(profile.introCameraStyle, centerLoc, progress, true)
            cameras.forEach { it.updatePosition(camLoc) }

            ticks++
        }, 1L, 1L)
    }

    fun playKillerOutro(killer: Player, asesino: Killer, viewers: List<Player>) {
        val id = asesino.id.lowercase()
        val profile = getProfile(id)
        val duracionTicks = 200
        
        val centerLoc = killer.location.clone().add(0.0, if (profile.isFloating) 2.5 else 0.0, 0.0)

        val visualDummy = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) { dummy ->
            dummy.isInvisible = false
            dummy.setGravity(false)
            dummy.isMarker = true
            dummy.setArms(true)
            dummy.setBasePlate(false)
            profile.applyPose(dummy, isIntro = false)
            profile.applyEquipment(killer, dummy, isIntro = false)
        }

        val titlePair = profile.getOutroTexts(plugin, asesino.nombre)
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(8), Duration.ofMillis(1000))

        val cameras = mutableListOf<VirtualCamera>()
        viewers.forEach { p ->
            p.showTitle(Title.title(titlePair.first, titlePair.second, times))
            if (p == killer) p.isInvisible = true
            
            val cam = VirtualCamera(p)
            cam.startSpectating(centerLoc.clone().add(3.0, 1.5, 0.0))
            cameras.add(cam)
        }

        // Play visual effects
        profile.playEffects(plugin, centerLoc, visualDummy, isIntro = false, displayManager)

        // Híbrido: Efectos globales de Outro
        viewers.forEach { p ->
            ObserverHook.playScreenTint(p, 100, 0, 0, 0.6f, 80) // Rojo oscuro trágico
            ObserverHook.playScreenshake(p, 0.8f, 30)
            p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.8f)
        }

        // Start cinematic orbit and dialogs
        val dialogos = profile.getDialogs(isIntro = false)
        var ticks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= duracionTicks || !visualDummy.isValid) {
                task.cancel()
                visualDummy.remove()
                killer.isInvisible = false
                cameras.forEach { it.stopSpectating() }
                return@Consumer
            }
            
            // Dialog logic
            if (dialogos.isNotEmpty()) {
                val index = (ticks / 40) % dialogos.size
                if (ticks < dialogos.size * 40) {
                    val msg = ColorTranslator.translate(dialogos[index])
                    viewers.forEach { it.sendActionBar(msg) }
                }
            }

            // Cinematic Dynamic Camera
            val progress = ticks.toDouble() / duracionTicks.toDouble()
            val camLoc = getCameraLocation(profile.outroCameraStyle, centerLoc, progress, false)
            cameras.forEach { it.updatePosition(camLoc) }

            ticks++
        }, 1L, 1L)
    }

    private fun getCameraLocation(style: CameraStyle, centerLoc: Location, progress: Double, isIntro: Boolean): Location {
        var radius = 0.0
        var yOffsetCam = 0.0
        var angle = 0.0
        var lookAtY = 1.2

        val effectiveProgress = if (isIntro) progress else (1.0 - progress * 0.7)

        when (style) {
            CameraStyle.ORBIT_ZOOM_IN -> {
                angle = progress * Math.PI * (if (isIntro) 2.0 else 1.0)
                radius = if (isIntro) 5.0 - (progress * 2.5) else 2.5 + (progress * 6.0)
                yOffsetCam = if (isIntro) 0.5 + (progress * 2.0) else 1.0 + (progress * 4.0)
            }
            CameraStyle.PAN_UP_REVEAL -> {
                angle = if (effectiveProgress < 0.7) 0.0 else (effectiveProgress - 0.7) * Math.PI * 1.5
                radius = 3.0 + (if (!isIntro) progress * 2.0 else 0.0)
                yOffsetCam = 0.1 + (effectiveProgress * 1.5)
                lookAtY = 0.5 + (effectiveProgress * 1.0)
            }
            CameraStyle.JUMPSCARE_RUSH -> {
                angle = if (isIntro) 0.0 else progress * Math.PI
                if (isIntro) {
                    if (progress < 0.8) {
                        radius = 8.0 - (progress * 3.0)
                    } else {
                        radius = 1.0 + ((1.0 - progress) * 20.0)
                    }
                } else {
                    radius = 2.0 + (progress * 8.0)
                }
                yOffsetCam = 1.5
                lookAtY = 1.5
            }
            CameraStyle.DRONE_SPIRAL -> {
                angle = effectiveProgress * Math.PI * 4.0
                radius = 8.0 * (1.0 - effectiveProgress) + 1.5
                yOffsetCam = 8.0 * (1.0 - effectiveProgress) + 1.0
                lookAtY = 1.2
            }
            CameraStyle.ZIG_ZAG_GLITCH -> {
                val step = (effectiveProgress * 5).toInt()
                angle = step * Math.PI / 2.0 + (effectiveProgress * 0.5)
                radius = 5.0 - (step * 0.7) + (if (!isIntro) progress * 3.0 else 0.0)
                yOffsetCam = 1.0 + (step * 0.2)
                
                if (Math.random() > 0.8) {
                    angle += (Math.random() - 0.5) * 0.2
                    yOffsetCam += (Math.random() - 0.5) * 0.3
                }
            }
        }

        val forward = centerLoc.direction.setY(0.0)
        if (forward.lengthSquared() < 0.01) {
            forward.setX(1.0).setZ(0.0)
        }
        forward.normalize()
        
        // Rotate the forward vector by `angle` radians
        val rx = forward.x * kotlin.math.cos(angle) - forward.z * kotlin.math.sin(angle)
        val rz = forward.x * kotlin.math.sin(angle) + forward.z * kotlin.math.cos(angle)

        val camX = centerLoc.x + rx * radius
        val camZ = centerLoc.z + rz * radius
        val camY = centerLoc.y + yOffsetCam
        
        val camLoc = Location(centerLoc.world, camX, camY, camZ)
        val lookAt = centerLoc.clone().add(0.0, lookAtY, 0.0)
        
        if (style == CameraStyle.ZIG_ZAG_GLITCH && Math.random() > 0.9) {
            lookAt.add((Math.random() - 0.5), (Math.random() - 0.5), (Math.random() - 0.5))
        }

        if (camLoc.distanceSquared(lookAt) > 0.001) {
            camLoc.direction = lookAt.toVector().subtract(camLoc.toVector())
        }
        return camLoc
    }
}
