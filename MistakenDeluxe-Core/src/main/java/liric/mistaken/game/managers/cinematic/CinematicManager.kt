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
                    val msg = pumpking.lib.color.ColorTranslator.translate(dialogos[index])
                    viewers.forEach { it.sendActionBar(msg) }
                }
            }

            // Cinematic Epic Orbit - Starts far and orbits while getting closer
            val progress = ticks.toDouble() / duracionTicks.toDouble()
            val angle = progress * Math.PI * 2.0 // One full rotation
            val radius = 5.0 - (progress * 2.5) // Radius shrinks from 5.0 to 2.5 blocks
            val yOffsetCam = 0.5 + (progress * 2.0) // Camera rises from +0.5 to +2.5
            
            val camX = centerLoc.x + radius * kotlin.math.cos(angle)
            val camZ = centerLoc.z + radius * kotlin.math.sin(angle)
            val camY = centerLoc.y + yOffsetCam
            
            val camLoc = Location(centerLoc.world, camX, camY, camZ)
            
            // Look directly at the center of the entity (Y + 1.2 approx)
            val lookAt = centerLoc.clone().add(0.0, 1.2, 0.0)
            camLoc.direction = lookAt.toVector().subtract(camLoc.toVector())
            
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
                    val msg = pumpking.lib.color.ColorTranslator.translate(dialogos[index])
                    viewers.forEach { it.sendActionBar(msg) }
                }
            }

            // Cinematic Epic Outro - Starts close and zooms out slowly while panning up
            val progress = ticks.toDouble() / duracionTicks.toDouble()
            val angle = progress * Math.PI // Half rotation
            val radius = 2.5 + (progress * 6.0) // Radius expands from 2.5 to 8.5 blocks
            val yOffsetCam = 1.0 + (progress * 4.0) // Camera rises from +1.0 to +5.0
            
            val camX = centerLoc.x + radius * kotlin.math.cos(angle)
            val camZ = centerLoc.z + radius * kotlin.math.sin(angle)
            val camY = centerLoc.y + yOffsetCam
            
            val camLoc = Location(centerLoc.world, camX, camY, camZ)
            
            // Look directly at the center of the entity (Y + 1.2 approx)
            val lookAt = centerLoc.clone().add(0.0, 1.2, 0.0)
            camLoc.direction = lookAt.toVector().subtract(camLoc.toVector())
            
            cameras.forEach { it.updatePosition(camLoc) }

            ticks++
        }, 1L, 1L)
    }
}
