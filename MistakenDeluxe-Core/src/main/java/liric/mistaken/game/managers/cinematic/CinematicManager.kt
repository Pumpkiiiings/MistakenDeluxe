package liric.mistaken.game.managers.cinematic

import com.pumpkiiiings.pkcinematics.api.PkCinematics
import com.pumpkiiiings.pkcinematics.model.Cinematic
import com.pumpkiiiings.pkcinematics.model.timeline.CameraKeyframe
import liric.mistaken.Mistaken
import liric.mistaken.game.managers.cinematic.profiles.*
import liric.mistaken.roles.killers.Killer
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
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

    private fun generateDynamicCinematic(id: String, centerLoc: org.bukkit.Location, duracionTicks: Int, radius: Double = 5.0): Cinematic {
        val cinematic = Cinematic(id)
        val worldName = centerLoc.world.name
        
        val numKeyframes = duracionTicks / 10
        val angleStep = (2 * Math.PI) / numKeyframes
        
        for (i in 0..numKeyframes) {
            val tick = i * 10
            val angle = i * angleStep
            
            val x = centerLoc.x + radius * kotlin.math.cos(angle)
            val z = centerLoc.z + radius * kotlin.math.sin(angle)
            val y = centerLoc.y + 1.5
            
            val dx = centerLoc.x - x
            val dz = centerLoc.z - z
            val yaw = (Math.toDegrees(Math.atan2(dz, dx)) - 90.0).toFloat()
            val pitch = 10f
            
            cinematic.timeline.cameraTrack.addKeyframe(CameraKeyframe(
                tick, worldName, x, y, z, yaw, pitch, 70f, "LINEAR"
            ))
        }
        
        cinematic.timeline.calculateDuration()
        return cinematic
    }

    fun playKillerIntro(killer: Player, asesino: Killer) {
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

        val cinematicId = "intro_$id" + "_" + UUID.randomUUID().toString().take(6)
        val dynamicCinematic = generateDynamicCinematic(cinematicId, centerLoc, duracionTicks)

        plugin.server.onlinePlayers.forEach { p ->
            p.showTitle(Title.title(titlePair.first, titlePair.second, times))
            if (p == killer) p.isInvisible = true
            PkCinematics.getApi().playbackManager.play(p, dynamicCinematic)
        }

        val fxLoc = centerLoc.clone(); fxLoc.y -= yOffset
        
        // Start dialogs
        val dialogos = profile.getDialogs(isIntro = true)
        var tickDial = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!visualDummy.isValid) {
                task.cancel(); return@Consumer
            }
            if (dialogos.isNotEmpty()) {
                val index = (tickDial / 40) % dialogos.size
                if (tickDial < dialogos.size * 40) {
                    plugin.server.onlinePlayers.forEach { it.sendActionBar(pumpking.lib.color.ColorTranslator.translate(dialogos[index])) }
                }
            }
            tickDial++
        }, 1L, 1L)

        // Play visual effects
        profile.playEffects(plugin, fxLoc, visualDummy, isIntro = true, displayManager)

        // Cleanup after duracionTicks
        plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
            visualDummy.remove()
            killer.isInvisible = false
            plugin.server.onlinePlayers.forEach { p -> PkCinematics.getApi().playbackManager.stop(p) }
        }, duracionTicks.toLong())
    }

    fun playKillerOutro(killer: Player, asesino: Killer) {
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

        val cinematicId = "outro_$id" + "_" + UUID.randomUUID().toString().take(6)
        val dynamicCinematic = generateDynamicCinematic(cinematicId, centerLoc, duracionTicks)

        plugin.server.onlinePlayers.forEach { p ->
            p.showTitle(Title.title(titlePair.first, titlePair.second, times))
            if (p == killer) p.isInvisible = true
            PkCinematics.getApi().playbackManager.play(p, dynamicCinematic)
        }

        // Start dialogs
        val dialogos = profile.getDialogs(isIntro = false)
        var tickDial = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!visualDummy.isValid) {
                task.cancel(); return@Consumer
            }
            if (dialogos.isNotEmpty()) {
                val index = (tickDial / 40) % dialogos.size
                if (tickDial < dialogos.size * 40) {
                    plugin.server.onlinePlayers.forEach { it.sendActionBar(pumpking.lib.color.ColorTranslator.translate(dialogos[index])) }
                }
            }
            tickDial++
        }, 1L, 1L)

        // Play visual effects
        profile.playEffects(plugin, centerLoc, visualDummy, isIntro = false, displayManager)

        // Cleanup after duracionTicks
        plugin.server.globalRegionScheduler.runDelayed(plugin, Consumer { _ ->
            visualDummy.remove()
            killer.isInvisible = false
            plugin.server.onlinePlayers.forEach { p -> PkCinematics.getApi().playbackManager.stop(p) }
        }, duracionTicks.toLong())
    }
}
