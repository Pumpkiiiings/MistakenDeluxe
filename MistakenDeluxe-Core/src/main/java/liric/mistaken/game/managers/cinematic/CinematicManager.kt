package liric.mistaken.game.managers.cinematic

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
    private val cinematicCamera = CinematicCamera(plugin)
    
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

    fun playKillerIntro(killer: Player, asesino: Killer) {
        val id = asesino.id.lowercase()
        val profile = getProfile(id)
        
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

        val cameraAnchor = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) {
            it.isInvisible = true; it.setGravity(false); it.isMarker = true
        }

        val titlePair = profile.getIntroTexts(plugin, asesino.nombre)
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(6), Duration.ofMillis(1000))

        val locacionesOriginales = mutableMapOf<UUID, org.bukkit.Location>()

        plugin.server.onlinePlayers.forEach { p ->
            locacionesOriginales[p.uniqueId] = p.location.clone()

            p.gameMode = GameMode.SPECTATOR
            val targetTp = centerLoc.clone()
            targetTp.y -= yOffset

            p.teleportAsync(targetTp).thenAccept {
                p.scheduler.runDelayed(plugin, Consumer { _ ->
                    if (p.isOnline) {
                        cinematicCamera.safeSetSpectatorTarget(p, cameraAnchor)
                        p.showTitle(Title.title(titlePair.first, titlePair.second, times))
                    }
                }, null, 3L)
            }

            if (p == killer) p.isInvisible = true
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

        // Start camera
        cinematicCamera.iniciarCamaraDinamica(killer, cameraAnchor, visualDummy, fxLoc, profile, isIntro = true, 160, displayManager) {
            plugin.server.onlinePlayers.forEach { p ->
                locacionesOriginales[p.uniqueId]?.let { origLoc ->
                    p.teleportAsync(origLoc)
                }
            }
        }
    }

    fun playKillerOutro(killer: Player, asesino: Killer) {
        val id = asesino.id.lowercase()
        val profile = getProfile(id)
        
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

        val cameraAnchor = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) {
            it.isInvisible = true; it.setGravity(false); it.isMarker = true
        }

        val titlePair = profile.getOutroTexts(plugin, asesino.nombre)
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(8), Duration.ofMillis(1000))

        plugin.server.onlinePlayers.forEach { p ->
            p.gameMode = GameMode.SPECTATOR
            p.teleportAsync(centerLoc).thenAccept {
                p.scheduler.runDelayed(plugin, Consumer { _ ->
                    if (p.isOnline) {
                        cinematicCamera.safeSetSpectatorTarget(p, cameraAnchor)
                        p.showTitle(Title.title(titlePair.first, titlePair.second, times))
                    }
                }, null, 3L)
            }

            if (p == killer) p.isInvisible = true
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

        // Start camera
        cinematicCamera.iniciarCamaraDinamica(killer, cameraAnchor, visualDummy, centerLoc, profile, isIntro = false, 200, displayManager) {}
    }
}
