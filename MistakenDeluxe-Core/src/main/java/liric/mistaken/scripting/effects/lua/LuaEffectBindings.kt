package liric.mistaken.scripting.effects.lua

import liric.mistaken.scripting.api.HasLocation
import liric.mistaken.scripting.adapter.BukkitPlayerAdapter
import liric.mistaken.scripting.effects.EffectHandle
import liric.mistaken.scripting.effects.EffectRegistry
import liric.mistaken.scripting.effects.dash.DashEffect
import liric.mistaken.scripting.effects.gameplay.ProximityTrapEffect
import liric.mistaken.scripting.effects.gameplay.GameplayFunctions
import liric.mistaken.scripting.effects.orbit.OrbitEffect
import liric.mistaken.scripting.effects.projectile.ProjectileEffect
import liric.mistaken.scripting.effects.trail.TrailEffect
import liric.mistaken.scripting.effects.music.AmbientMusicEffect
import liric.mistaken.scripting.effects.gameplay.FinisherEngine
import liric.mistaken.scripting.effects.gameplay.PlayerStateRegistry
import liric.mistaken.scripting.effects.gameplay.ChatInterceptorRegistry
import liric.mistaken.roles.killers.triggers.traps.WorldTrapRegistry
import liric.mistaken.roles.killers.triggers.traps.TrapDefinition
import liric.mistaken.scripting.effects.gameplay.LineSpawnEffect
import liric.mistaken.scripting.effects.gameplay.SequenceEffect
import liric.mistaken.scripting.effects.gameplay.BaitTrapEffect
import liric.mistaken.scripting.effects.gameplay.FormationEffect
import liric.mistaken.scripting.effects.gameplay.SinkingBlockEffect
import liric.mistaken.scripting.effects.gameplay.SpiralParticleEffect
import liric.mistaken.scripting.effects.gameplay.TempFlyEffect
import liric.mistaken.scripting.effects.gameplay.RevealTargetsEffect
import liric.mistaken.scripting.adapter.BukkitLocationAdapter
import liric.mistaken.utils.hooks.ObserverHook
import liric.mistaken.utils.visuals.ParticleShapesUtils
import liric.mistaken.scripting.services.SkillService
import liric.mistaken.utils.misc.HitboxVisualizer
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.util.function.Consumer
import io.papermc.paper.threadedregions.scheduler.ScheduledTask

/**
 * Registra TODAS las funciones DSL en el sandbox Globals de un script.
 * Cada función recibe ScriptPlayer (userdata) y devuelve LuaTable builders con sintaxis ':'.
 *
 * SEGURIDAD: Lua nunca ve Player/Location/World directos. Los builders unwrapean
 * internamente via BukkitPlayerAdapter.getPlayer() (internal).
 *
 * FOLIA: Cada efecto posee su propio ScheduledTask. Los callbacks de on_hit
 * se ejecutan en el entity scheduler de la víctima.
 *
 * CONVENCIÓN DE BUILDERS:
 * - Builders atados a player (orbit, trail, dash, projectile, line_spawn,
 *   temp_fly, reveal_targets, ambient_music): reciben solo (player), porque
 *   el efecto se mueve con el player y su lifecycle está atado al entity scheduler.
 * - Builders de world (bait_trap, formation, sinking_block, spiral_particle,
 *   place_trap, proximity_trap, sequence): reciben (player, loc) como los dos
 *   primeros argumentos, para ownership — player provee scriptId/ownerUuid para
 *   registrarse en EffectRegistry con cleanup automático (quit/death/reload),
 *   y loc define la posición fija del efecto en el world.
 * - Funciones globales de ubicación (sound, particle_burst): reciben cualquier
 *   objeto que implemente HasLocation como primer argumento (player, location,
 *   o cualquier wrapper futuro con sentido de ubicación).
 */
object LuaEffectBindings {

    fun install(globals: Globals, scriptId: String) {
        
        // ──────────── on_chat(callback) ────────────
        globals.set("on_chat", object : OneArgFunction() {
            override fun call(callbackArg: LuaValue): LuaValue {
                if (callbackArg.isfunction()) {
                    ChatInterceptorRegistry.registerCallback(scriptId) { player, message ->
                        val result = callbackArg.call(CoerceJavaToLua.coerce(BukkitPlayerAdapter(player)), LuaValue.valueOf(message))
                        if (result.isstring()) result.tojstring() else null
                    }
                }
                return LuaValue.NIL
            }
        })
        
        // ──────────── player_state_set(player, key, value) ────────────
        globals.set("player_state_set", object : ThreeArgFunction() {
            override fun call(playerArg: LuaValue, keyArg: LuaValue, valArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val key = keyArg.optjstring("")
                val value = valArg.optjstring("")
                PlayerStateRegistry.set(scriptId, player.uniqueId, key!!, value!!)
                return LuaValue.NIL
            }
        })

        // ──────────── player_state_get(player, key) ────────────
        globals.set("player_state_get", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, keyArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val key = keyArg.optjstring("")
                val value = PlayerStateRegistry.get(scriptId, player.uniqueId, key!!)
                return if (value != null) LuaValue.valueOf(value) else LuaValue.NIL
            }
        })

        // ──────────── player_state_clear(player, key) ────────────
        globals.set("player_state_clear", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, keyArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val key = keyArg.optjstring("")
                PlayerStateRegistry.clear(scriptId, player.uniqueId, key!!)
                return LuaValue.NIL
            }
        })

        // ──────────── get_nearby_players(player, radius) ────────────
        globals.set("get_nearby_players", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, radiusArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val radius = radiusArg.checkdouble()
                val nearby = player.getNearbyEntities(radius, radius, radius)
                    .filterIsInstance<org.bukkit.entity.Player>()
                    .filter { it.uniqueId != player.uniqueId } // Exclude self
                return playersToLuaTable(nearby)
            }
        })

        // ──────────── ray_trace_player(player, distance) ────────────
        globals.set("ray_trace_player", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, distArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val distance = distArg.checkdouble()
                val result = player.world.rayTraceEntities(player.eyeLocation, player.location.direction, distance) {
                    it is org.bukkit.entity.Player && it.uniqueId != player.uniqueId
                }
                val targetPlayer = result?.hitEntity as? org.bukkit.entity.Player
                return if (targetPlayer != null) CoerceJavaToLua.coerce(liric.mistaken.scripting.adapter.BukkitPlayerAdapter(targetPlayer)) else LuaValue.NIL
            }
        })



        // ──────────── draw_star(player, hex_color, radius, points) ────────────
        globals.set("draw_star", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val player = unwrapPlayer(args.arg(1)) ?: return LuaValue.NIL
                val hex = args.checkjstring(2)
                val radius = args.checkdouble(3)
                val points = args.checkint(4)
                
                val rgb = Integer.parseInt(hex.replace("#", ""), 16)
                val color = org.bukkit.Color.fromRGB(rgb)
                
                SkillService.drawStar(player, color, radius, points)
                return LuaValue.NIL
            }
        })

        // ──────────── visual_hitbox(player, x, y, z, ticks, material_name) ────────────
        globals.set("visual_hitbox", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val player = unwrapPlayer(args.arg(1)) ?: return LuaValue.NIL
                val x = args.checkdouble(2)
                val y = args.checkdouble(3)
                val z = args.checkdouble(4)
                val ticks = args.checkint(5).toLong()
                val matName = args.checkjstring(6)
                
                val material = org.bukkit.Material.matchMaterial(matName.uppercase()) ?: org.bukkit.Material.RED_STAINED_GLASS
                HitboxVisualizer.drawInstantHitbox(liric.mistaken.Mistaken.instance, player.location, x, y, z, ticks, material)
                return LuaValue.NIL
            }
        })

        // ──────────── draw_dna_helix(location, particle_type, radius, height) ────────────
        globals.set("draw_dna_helix", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val p = org.bukkit.Particle.valueOf(args.checkjstring(2).uppercase())
                val radius = args.checkdouble(3)
                val height = args.checkdouble(4)
                liric.mistaken.utils.visuals.ParticleShapesUtils.drawDnaHelix(loc, p, radius, height)
                return LuaValue.NIL
            }
        })
        
        // ──────────── draw_shockwave(location, particle_type, max_radius) ────────────
        globals.set("draw_shockwave", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val p = org.bukkit.Particle.valueOf(args.checkjstring(2).uppercase())
                val r = args.checkdouble(3)
                liric.mistaken.utils.visuals.ParticleShapesUtils.drawShockwave(loc, p, r)
                return LuaValue.NIL
            }
        })
        
        // ──────────── draw_vortex(location, particle_type, radius, height) ────────────
        globals.set("draw_vortex", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val p = org.bukkit.Particle.valueOf(args.checkjstring(2).uppercase())
                val r = args.checkdouble(3)
                val h = args.checkdouble(4)
                liric.mistaken.utils.visuals.ParticleShapesUtils.drawVortex(loc, p, r, h)
                return LuaValue.NIL
            }
        })
        
        // ──────────── draw_tornado(location, particle_type, height, max_radius) ────────────
        globals.set("draw_tornado", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val p = org.bukkit.Particle.valueOf(args.checkjstring(2).uppercase())
                val h = args.checkdouble(3)
                val r = args.checkdouble(4)
                liric.mistaken.utils.visuals.ParticleShapesUtils.drawTornado(loc, p, h, r)
                return LuaValue.NIL
            }
        })
        
        // ──────────── draw_wings(player, particle_type) ────────────
        globals.set("draw_wings", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, pArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val p = org.bukkit.Particle.valueOf(pArg.checkjstring().uppercase())
                liric.mistaken.utils.visuals.ParticleShapesUtils.drawWings(player, p)
                return LuaValue.NIL
            }
        })
        
        // ──────────── spawn_fake_swarm(location, count, duration) ────────────
        globals.set("spawn_fake_swarm", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val count = args.checkint(2)
                val duration = args.checkint(3).toLong()
                liric.mistaken.scripting.services.SkillService.spawnFakeSwarm(loc, count, duration)
                return LuaValue.NIL
            }
        })
        

        // ──────────── spawn_temp_item(location, material_name, scale_x, scale_y, scale_z, glow_color, duration_ticks) ────────────
        globals.set("spawn_temp_item", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val mat = org.bukkit.Material.matchMaterial(args.checkjstring(2).uppercase()) ?: return LuaValue.NIL
                val sx = args.checkdouble(3).toFloat()
                val sy = args.checkdouble(4).toFloat()
                val sz = args.checkdouble(5).toFloat()
                val glowColor = args.checkjstring(6)
                val duration = args.checkint(7).toLong()
                liric.mistaken.scripting.services.SkillService.spawnTempItemDisplay(loc, mat, sx, sy, sz, glowColor, duration)
                return LuaValue.NIL
            }
        })
        
        // ──────────── spawn_spinning_tnt(location, duration_ticks) ────────────
        globals.set("spawn_spinning_tnt", object : TwoArgFunction() {
            override fun call(locArg: LuaValue, durationArg: LuaValue): LuaValue {
                val locAdapter = locArg.checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val duration = durationArg.checkint().toLong()
                liric.mistaken.scripting.services.SkillService.spawnSpinningTnt(loc, duration)
                return LuaValue.NIL
            }
        })
        
        // ──────────── spawn_evoker_fang(location) ────────────
        globals.set("spawn_evoker_fang", object : OneArgFunction() {
            override fun call(locArg: LuaValue): LuaValue {
                val locAdapter = locArg.checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                liric.mistaken.scripting.services.SkillService.spawnEvokerFang(loc)
                return LuaValue.NIL
            }
        })
        
        // ──────────── spawn_blinking_ritual(location, material_name, count, radius, duration_ticks) ────────────
        globals.set("spawn_blinking_ritual", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val mat = org.bukkit.Material.matchMaterial(args.checkjstring(2).uppercase()) ?: return LuaValue.NIL
                val count = args.checkint(3)
                val radius = args.checkdouble(4)
                val duration = args.checkint(5).toLong()
                liric.mistaken.scripting.services.SkillService.spawnBlinkingRitual(loc, mat, count, radius, duration)
                return LuaValue.NIL
            }
        })
        
        // ──────────── play_entity_sound(player, sound_name, source_entity, volume, pitch) ────────────
        globals.set("play_entity_sound", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val viewer = (args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitPlayerAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitPlayerAdapter).getPlayer()
                val sound = args.checkjstring(2)
                val source = (args.arg(3).checkuserdata(liric.mistaken.scripting.adapter.BukkitPlayerAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitPlayerAdapter).getPlayer()
                val volume = args.checkdouble(4).toFloat()
                val pitch = args.checkdouble(5).toFloat()
                liric.mistaken.utils.hooks.ObserverHook.playEntitySound(viewer, sound, source, volume, pitch)
                return LuaValue.NIL
            }
        })
        
        // ──────────── stop_sound(player, sound_name) ────────────
        globals.set("stop_sound", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, soundArg: LuaValue): LuaValue {
                val viewer = (playerArg.checkuserdata(liric.mistaken.scripting.adapter.BukkitPlayerAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitPlayerAdapter).getPlayer()
                val sound = soundArg.checkjstring()
                liric.mistaken.utils.hooks.ObserverHook.stopSound(viewer, sound)
                return LuaValue.NIL
            }
        })
        

        // ──────────── draw_instant_hitbox(location, sx, sy, sz, duration, material) ────────────
        globals.set("draw_instant_hitbox", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val sx = args.checkdouble(2)
                val sy = args.checkdouble(3)
                val sz = args.checkdouble(4)
                val duration = args.checkint(5).toLong()
                val mat = org.bukkit.Material.matchMaterial(args.checkjstring(6).uppercase()) ?: org.bukkit.Material.RED_STAINED_GLASS
                liric.mistaken.scripting.services.SkillService.drawInstantHitbox(loc, sx, sy, sz, duration, mat)
                return LuaValue.NIL
            }
        })
        
        // ──────────── spawn_tracking_hitbox(player, sx, sy, sz, material, duration) ────────────
        globals.set("spawn_tracking_hitbox", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val player = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitPlayerAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitPlayerAdapter
                val sx = args.checkdouble(2)
                val sy = args.checkdouble(3)
                val sz = args.checkdouble(4)
                val mat = org.bukkit.Material.matchMaterial(args.checkjstring(5).uppercase()) ?: org.bukkit.Material.RED_STAINED_GLASS
                val duration = args.checkint(6).toLong()
                liric.mistaken.scripting.services.SkillService.spawnTrackingHitbox(player, sx, sy, sz, mat, duration)
                return LuaValue.NIL
            }
        })
        
        // ──────────── spawn_temp_block(location, material, tx, ty, tz, sx, sy, sz, duration) ────────────
        globals.set("spawn_temp_block", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val locAdapter = args.arg(1).checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                val mat = org.bukkit.Material.matchMaterial(args.checkjstring(2).uppercase()) ?: return LuaValue.NIL
                val tx = args.checkdouble(3).toFloat()
                val ty = args.checkdouble(4).toFloat()
                val tz = args.checkdouble(5).toFloat()
                val sx = args.checkdouble(6).toFloat()
                val sy = args.checkdouble(7).toFloat()
                val sz = args.checkdouble(8).toFloat()
                val duration = args.checkint(9).toLong()
                liric.mistaken.scripting.services.SkillService.spawnVirtualTempBlock(loc, mat, tx, ty, tz, sx, sy, sz, duration)
                return LuaValue.NIL
            }
        })
        
        // ──────────── apply_glowing_team(player, targets_table, color_hex, duration) ────────────
        globals.set("apply_glowing_team", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val player = unwrapPlayer(args.arg(1)) ?: return LuaValue.NIL
                val targetsTable = args.checktable(2)
                val colorHex = args.checkjstring(3)
                val duration = args.checkint(4).toLong()
                val targets = mutableListOf<Player>()
                var i = 1
                while (true) {
                    val tArg = targetsTable.get(i)
                    if (tArg.isnil()) break
                    val t = unwrapPlayer(tArg)
                    if (t != null) targets.add(t)
                    i++
                }
                liric.mistaken.scripting.services.SkillService.applyGlowingTeam(player, targets, colorHex, duration)
                return LuaValue.NIL
            }
        })
        
        // ──────────── launch_wither_skull(player, yield, max_ticks, on_hit_callback) ────────────
        globals.set("launch_wither_skull", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val player = unwrapPlayer(args.arg(1)) ?: return LuaValue.NIL
                val yield = args.checkdouble(2).toFloat()
                val maxTicks = args.checkint(3)
                val onHitCallback = if (args.narg() >= 4 && args.arg(4).isfunction()) args.arg(4).checkfunction() else null
                
                val skull = player.launchProjectile(org.bukkit.entity.WitherSkull::class.java)
                skull.yield = yield
                var life = 0
                val plugin = liric.mistaken.Mistaken.instance
                skull.scheduler.runAtFixedRate(plugin, Consumer<ScheduledTask> { task ->
                    if (life >= maxTicks || !skull.isValid) {
                        task.cancel()
                        return@Consumer
                    }
                    skull.world.spawnParticle(org.bukkit.Particle.WITCH, skull.location, 3, 0.05, 0.05, 0.05, 0.01)
                    val hit = skull.world.getNearbyPlayers(skull.location, 1.2).firstOrNull { liric.mistaken.scripting.effects.gameplay.GameplayFunctions.isValidTarget(player, it) }
                    if (hit != null) {
                        if (onHitCallback != null) {
                            hit.scheduler.run(plugin, Consumer<ScheduledTask> { _ ->
                                onHitCallback.call(org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(liric.mistaken.scripting.adapter.BukkitPlayerAdapter(hit)))
                            }, null)
                        }
                        skull.remove()
                        task.cancel()
                    }
                    life++
                }, null, 1L, 1L)
                return LuaValue.NIL
            }
        })
        
        // ──────────── dash(player) ────────────
        globals.set("dash", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildDashTable(scriptId, player)
            }
        })

        // ──────────── place_trap(player, location) ────────────
        globals.set("place_trap", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, locArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val locAdapter = locArg.checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                return buildTrapTable(scriptId, player, loc)
            }
        })
        // ──────────── orbit(player) ────────────
        globals.set("orbit", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildOrbitTable(scriptId, player)
            }
        })
        
        // ──────────── proximity_trap(player, location) ────────────
        globals.set("proximity_trap", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, locArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val locAdapter = locArg.checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                return buildProximityTrapTable(scriptId, player, loc)
            }
        })
        
        // ──────────── sequence(player, location) ────────────
        globals.set("sequence", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, locArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val loc = unwrapLocation(locArg) ?: return LuaValue.NIL
                return buildSequenceTable(scriptId, player, loc)
            }
        })

        // ──────────── trail(player) ────────────
        globals.set("trail", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildTrailTable(scriptId, player)
            }
        })

        // ──────────── projectile(player) ────────────
        globals.set("projectile", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildProjectileTable(scriptId, player)
            }
        })

        // ──────────── dash(player) ────────────
        globals.set("dash", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildDashTable(scriptId, player)
            }
        })

        // ──────────── damage(victim) ────────────
        globals.set("damage", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val victimArg = args.arg(1)
                val victim = unwrapPlayer(victimArg) ?: return LuaValue.NIL
                val amount = if (args.narg() >= 2) args.checkdouble(2) else 3.0
                GameplayFunctions.damage(victim, amount, scriptId)
                return LuaValue.NIL
            }
        })

        // ──────────── apply_effect(victim, name, amplifier, ticks) ────────────
        globals.set("apply_effect", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val victim = unwrapPlayer(args.arg(1)) ?: return LuaValue.NIL
                val name = args.arg(2).checkjstring()
                val amp = args.arg(3).optint(0)
                val ticks = args.arg(4).optint(60)
                GameplayFunctions.applyEffect(victim, name, amp, ticks)
                return LuaValue.NIL
            }
        })

        // ──────────── knockback(victim, source, hForce, vForce) ────────────
        globals.set("knockback", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val victim = unwrapPlayer(args.arg(1)) ?: return LuaValue.NIL
                val source = unwrapPlayer(args.arg(2)) ?: return LuaValue.NIL
                val hForce = args.arg(3).optdouble(1.0)
                val vForce = args.arg(4).optdouble(0.4)
                GameplayFunctions.knockback(victim, source, hForce, vForce)
                return LuaValue.NIL
            }
        })

        // ──────────── is_valid_target(player, victim) → bool ────────────
        globals.set("is_valid_target", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, victimArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.FALSE
                val victim = unwrapPlayer(victimArg) ?: return LuaValue.FALSE
                return LuaValue.valueOf(GameplayFunctions.isValidTarget(player, victim))
            }
        })

        // ------------- spawn_particle(location, name, offsetX, offsetY, offsetZ, speed, count) -------------
        globals.set("spawn_particle", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val loc = unwrapLocation(args.arg(1)) ?: return LuaValue.NIL
                val particleName = args.arg(2).checkjstring()
                val offsetX = args.arg(3).optdouble(0.0)
                val offsetY = args.arg(4).optdouble(0.0)
                val offsetZ = args.arg(5).optdouble(0.0)
                val speed = args.arg(6).optdouble(0.0)
                val count = args.arg(7).optint(1)
                
                GameplayFunctions.spawnParticleBurst(loc, particleName, count, offsetX, offsetY, offsetZ, speed)
                return LuaValue.NIL
            }
        })

        // ──────────── nearby_players(player, radius) → table ────────────
        globals.set("nearby_players", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, radiusArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaTable()
                val radius = radiusArg.optdouble(10.0)
                val players = GameplayFunctions.nearbyPlayers(player, radius)
                return playersToLuaTable(players)
            }
        })

        // ──────────── nearby_valid_targets(player, radius) → table ────────────
        globals.set("nearby_valid_targets", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, radiusArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaTable()
                val radius = radiusArg.optdouble(10.0)
                val targets = GameplayFunctions.nearbyValidTargets(player, radius)
                return playersToLuaTable(targets)
            }
        })

        // ──────────── sound(has_location, id, volume, pitch) ────────────
        // Acepta cualquier objeto que implemente HasLocation (player, location, etc.)
        globals.set("sound", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val loc = unwrapHasLocation(args.arg(1)) ?: return LuaValue.NIL
                val soundName = args.arg(2).checkjstring()
                val vol = args.arg(3).optdouble(1.0).toFloat()
                val pitch = args.arg(4).optdouble(1.0).toFloat()
                GameplayFunctions.playSoundAt(loc, soundName, vol, pitch)
                return LuaValue.NIL
            }
        })

        // ──────────── play_animation(player, animation_id, priority) ────────────
        globals.set("play_animation", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val player = unwrapPlayer(args.arg(1)) ?: return LuaValue.NIL
                val animName = args.arg(2).optjstring("") ?: ""
                if (animName.isEmpty()) return LuaValue.NIL
                
                val priority = args.arg(3).optint(80)
                
                val killer = liric.mistaken.Mistaken.instance.killerManager.getKillerOfPlayer(player)
                if (killer is liric.mistaken.roles.killers.BaseKiller) {
                    val character = killer.getCharacter(player)
                    if (character != null) {
                        val state = object : liric.mistaken.characters.states.CharacterState {
                            override val id = animName
                            override val priority = priority
                            override val defaultAnimation = animName
                        }
                        character.getComponent(liric.mistaken.characters.components.StateComponent::class.java)?.transitionTo(state, force = true)
                    }
                }
                return LuaValue.NIL
            }
        })

        // ──────────── send_translated(player, key) ────────────
        // Resuelve la key contra PumpkingServiceManager.messages.getComponent
        globals.set("send_translated", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, keyArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val key = keyArg.checkjstring()
                GameplayFunctions.sendTranslated(player, key)
                return LuaValue.NIL
            }
        })

        // ──────────── particle_burst(has_location) ────────────
        // Builder para explosión puntual de partículas. Acepta HasLocation.
        globals.set("particle_burst", object : OneArgFunction() {
            override fun call(locArg: LuaValue): LuaValue {
                val loc = unwrapHasLocation(locArg) ?: return LuaValue.NIL
                return buildParticleBurstTable(loc)
            }
        })
        // ──────────── ambient_music(player) ────────────
        globals.set("ambient_music", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildAmbientMusicTable(scriptId, player)
            }
        })

        // ──────────── line_spawn(player) ────────────
        globals.set("line_spawn", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildLineSpawnTable(scriptId, player)
            }
        })

        // ──────────── temp_fly(player) ────────────
        globals.set("temp_fly", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildTempFlyTable(scriptId, player)
            }
        })

        // ──────────── bait_trap(player, location) ────────────
        globals.set("bait_trap", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, locArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val loc = unwrapLocation(locArg) ?: return LuaValue.NIL
                return buildBaitTrapTable(scriptId, player, loc)
            }
        })

        // ──────────── formation(player, location) ────────────
        globals.set("formation", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, locArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val loc = unwrapLocation(locArg) ?: return LuaValue.NIL
                return buildFormationTable(scriptId, player, loc)
            }
        })

        // ──────────── sinking_block(player, location) ────────────
        globals.set("sinking_block", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, locArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val loc = unwrapLocation(locArg) ?: return LuaValue.NIL
                return buildSinkingBlockTable(scriptId, player, loc)
            }
        })

        // ──────────── spiral_particle(player, location) ────────────
        globals.set("spiral_particle", object : TwoArgFunction() {
            override fun call(playerArg: LuaValue, locArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                val loc = unwrapLocation(locArg) ?: return LuaValue.NIL
                return buildSpiralParticleTable(scriptId, player, loc)
            }
        })

        // ──────────── reveal_targets(player) ────────────
        globals.set("reveal_targets", object : OneArgFunction() {
            override fun call(playerArg: LuaValue): LuaValue {
                val player = unwrapPlayer(playerArg) ?: return LuaValue.NIL
                return buildRevealTargetsTable(scriptId, player)
            }
        })

        // ──────────── screen_tint(victim) ────────────
        globals.set("screen_tint", object : OneArgFunction() {
            override fun call(victimArg: LuaValue): LuaValue {
                val victim = unwrapPlayer(victimArg) ?: return LuaValue.NIL
                return buildScreenTintTable(victim)
            }
        })

        // ──────────── screen_shake(victim) ────────────
        globals.set("screen_shake", object : OneArgFunction() {
            override fun call(victimArg: LuaValue): LuaValue {
                val victim = unwrapPlayer(victimArg) ?: return LuaValue.NIL
                return buildScreenShakeTable(victim)
            }
        })

        // ──────────── shape(type) ────────────
        globals.set("shape", object : OneArgFunction() {
            override fun call(typeArg: LuaValue): LuaValue {
                return buildShapeTable(typeArg.checkjstring())
            }
        })

        // ──────────── on_finisher(callback) ────────────
        globals.set("on_finisher", object : OneArgFunction() {
            override fun call(callbackArg: LuaValue): LuaValue {
                if (callbackArg.isfunction()) {
                    FinisherEngine.registerCallback(scriptId) { victim ->
                        val adapter = BukkitPlayerAdapter(victim)
                        callbackArg.call(CoerceJavaToLua.coerce(adapter))
                    }
                }
                return LuaValue.NIL
            }
        })
    }

    // ═══════════════════════════════════════════════════════
    // BUILDERS
    // ═══════════════════════════════════════════════════════

    private fun buildOrbitTable(scriptId: String, player: Player): LuaTable {
        val t = LuaTable()
        var count = 3; var materialNames = mutableListOf<String>(); var isItem = false; var radius = 1.5; var height = 1.3
        var rotSpeed = 0.15; var wobbleAmp = 0.2; var wobbleFreq = 2.0; var glow = false
        var duration: Int? = null

        // All builder methods accept (self, value) because of ':' syntax
        t.set("count", TwoArg(t) { _, v -> count = v.checkint().coerceIn(1, 20) })
        t.set("material", TwoArg(t) { _, v -> materialNames.clear(); materialNames.add(v.checkjstring()); isItem = false })
        t.set("materials", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                isItem = false
                materialNames.clear()
                for (i in 2..args.narg()) {
                    val mat = args.arg(i).optjstring(null)
                    if (mat != null) materialNames.add(mat)
                }
                return args.arg(1)
            }
        })
        t.set("virtual_item", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                isItem = true
                for (i in 2..args.narg()) {
                    val mat = args.arg(i).optjstring(null)
                    if (mat != null) materialNames.add(mat)
                }
                return args.arg(1)
            }
        })
        t.set("radius", TwoArg(t) { _, v -> radius = v.checkdouble().coerceIn(0.1, 10.0) })
        t.set("height", TwoArg(t) { _, v -> height = v.checkdouble().coerceIn(0.0, 5.0) })
        t.set("rotation_speed", TwoArg(t) { _, v -> rotSpeed = v.checkdouble().coerceIn(0.01, 1.0) })
        t.set("wobble", ThreeArg(t) { _, a, f -> wobbleAmp = a.checkdouble().coerceIn(0.0, 2.0); wobbleFreq = f.checkdouble().coerceIn(0.0, 10.0) })
        t.set("glow", TwoArg(t) { _, v -> glow = v.optboolean(true) })
        t.set("duration", TwoArg(t) { _, v -> duration = if (v.isnil()) null else v.checkint().coerceIn(1, 6000) })

        // Terminal method
        t.set("show", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val effect = OrbitEffect(scriptId, player.uniqueId, player, count, materialNames, isItem, radius, height, rotSpeed, wobbleAmp, wobbleFreq, glow, duration)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildTrailTable(scriptId: String, player: Player): LuaTable {
        val t = LuaTable()
        var particleName = "END_ROD"; var dustR: Float? = null; var dustG: Float? = null; var dustB: Float? = null
        var dustSize = 1.0f; var offsetX = 0.1f; var offsetY = 0.1f; var offsetZ = 0.1f
        var viewRadius = 25.0; var onlyWhenMoving = false; var particleCount = 2; var duration: Int? = null

        t.set("particle", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                // :particle("DUST", {r=1, g=0, b=0.5}, 1.1) or :particle("WITCH")
                particleName = args.arg(2).checkjstring()
                if (args.narg() >= 3 && args.arg(3).istable()) {
                    val colorTable = args.arg(3).checktable()
                    dustR = colorTable.get("r").optdouble(1.0).toFloat()
                    dustG = colorTable.get("g").optdouble(0.0).toFloat()
                    dustB = colorTable.get("b").optdouble(0.0).toFloat()
                    if (args.narg() >= 4) dustSize = args.arg(4).optdouble(1.0).toFloat()
                }
                return args.arg(1) // return self
            }
        })
        t.set("offset", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                offsetX = args.arg(2).optdouble(0.1).toFloat()
                offsetY = args.arg(3).optdouble(0.1).toFloat()
                offsetZ = args.arg(4).optdouble(0.1).toFloat()
                return args.arg(1)
            }
        })
        t.set("view_radius", TwoArg(t) { _, v -> viewRadius = v.checkdouble().coerceIn(5.0, 100.0) })
        t.set("only_when_moving", TwoArg(t) { _, v -> onlyWhenMoving = v.optboolean(true) })
        t.set("count", TwoArg(t) { _, v -> particleCount = v.checkint().coerceIn(1, 20) })
        t.set("duration", TwoArg(t) { _, v -> duration = if (v.isnil()) null else v.checkint().coerceIn(1, 6000) })

        t.set("show", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val effect = TrailEffect(scriptId, player.uniqueId, player, particleName, dustR, dustG, dustB, dustSize, offsetX, offsetY, offsetZ, viewRadius, onlyWhenMoving, particleCount, duration)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildProjectileTable(scriptId: String, player: Player): LuaTable {
        val t = LuaTable()
        var material = "NETHER_STAR"; var isBlock = false; var speed = 1.5; var maxTicks = 40
        var hitRadius = 1.5; var trailParticle: String? = null; var trailCount = 3
        var impactParticle: String? = null; var impactSound: String? = null
        var onHitLuaFunc: LuaValue = LuaValue.NIL

        t.set("model", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val type = args.arg(2).checkjstring() // "item" or "block"
                material = args.arg(3).checkjstring()
                isBlock = type.equals("block", ignoreCase = true)
                return args.arg(1)
            }
        })
        t.set("item", TwoArg(t) { _, v -> material = v.checkjstring(); isBlock = false })
        t.set("speed", TwoArg(t) { _, v -> speed = v.checkdouble().coerceIn(0.1, 5.0) })
        t.set("max_ticks", TwoArg(t) { _, v -> maxTicks = v.checkint().coerceIn(1, 200) })
        t.set("hit_radius", TwoArg(t) { _, v -> hitRadius = v.checkdouble().coerceIn(0.5, 15.0) })
        t.set("trail_particle", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                trailParticle = args.arg(2).checkjstring()
                if (args.narg() >= 3) trailCount = args.arg(3).optint(3)
                return args.arg(1)
            }
        })
        t.set("on_hit", TwoArg(t) { _, v -> onHitLuaFunc = v })
        t.set("on_impact_particle", TwoArg(t) { _, v -> impactParticle = v.checkjstring() })
        t.set("on_impact_sound", TwoArg(t) { _, v -> impactSound = v.checkjstring() })

        t.set("launch", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val hitCallback: ((Player) -> Unit)? = if (onHitLuaFunc.isfunction()) {
                    { victim: Player ->
                        val adapter = BukkitPlayerAdapter(victim)
                        onHitLuaFunc.call(CoerceJavaToLua.coerce(adapter))
                    }
                } else null

                val effect = ProjectileEffect(scriptId, player.uniqueId, player, material, isBlock, speed, maxTicks, hitRadius, trailParticle, trailCount, hitCallback, impactParticle, impactSound)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildDashTable(scriptId: String, player: Player): LuaTable {
        val t = LuaTable()
        var speed = 1.4; var maxTicks = 10; var hitRadius = 2.0; var stopOnBlock = true
        var trailParticle: String? = null
        var onHitLuaFunc: LuaValue = LuaValue.NIL
        var onBlockHitLuaFunc: LuaValue = LuaValue.NIL

        t.set("speed", TwoArg(t) { _, v -> speed = v.checkdouble().coerceIn(0.1, 5.0) })
        t.set("max_ticks", TwoArg(t) { _, v -> maxTicks = v.checkint().coerceIn(1, 200) })
        t.set("hit_radius", TwoArg(t) { _, v -> hitRadius = v.checkdouble().coerceIn(0.5, 15.0) })
        t.set("stop_on_block", TwoArg(t) { _, v -> stopOnBlock = v.optboolean(true) })
        t.set("trail_particle", TwoArg(t) { _, v -> trailParticle = v.checkjstring() })
        t.set("on_hit", TwoArg(t) { _, v -> onHitLuaFunc = v })
        t.set("on_block_hit", TwoArg(t) { _, v -> onBlockHitLuaFunc = v })

        t.set("start", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val hitCallback: ((Player) -> Unit)? = if (onHitLuaFunc.isfunction()) {
                    { victim: Player ->
                        val adapter = BukkitPlayerAdapter(victim)
                        onHitLuaFunc.call(CoerceJavaToLua.coerce(adapter))
                    }
                } else null

                val blockCallback: (() -> Unit)? = if (onBlockHitLuaFunc.isfunction()) {
                    { onBlockHitLuaFunc.call() }
                } else null

                val effect = DashEffect(scriptId, player.uniqueId, player, speed, maxTicks, hitRadius, stopOnBlock, trailParticle, hitCallback, blockCallback)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildAmbientMusicTable(scriptId: String, player: Player): LuaTable {
        val t = LuaTable()
        var sound = "music_disc_11"; var interval = 1200L; var volume = 1.0f

        t.set("sound", TwoArg(t) { _, v -> sound = v.checkjstring() })
        t.set("interval", TwoArg(t) { _, v -> interval = v.checklong().coerceAtLeast(20L) })
        t.set("volume", TwoArg(t) { _, v -> volume = v.checkdouble().toFloat() })

        t.set("start", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val effect = AmbientMusicEffect(scriptId, player.uniqueId, player, sound, interval, volume)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildLineSpawnTable(scriptId: String, player: Player): LuaTable {
        val t = LuaTable()
        var count = 10; var spacing = 1.0; var delayTicks = 1L
        val angles = mutableListOf<Double>(0.0)
        var snapToGround = false
        var onHitLuaFunc: LuaValue = LuaValue.NIL

        t.set("count", TwoArg(t) { _, v -> count = v.checkint().coerceIn(1, 50) })
        t.set("spacing", TwoArg(t) { _, v -> spacing = v.checkdouble().coerceIn(0.1, 5.0) })
        t.set("delay_ticks", TwoArg(t) { _, v -> delayTicks = v.checklong().coerceIn(0L, 20L) })
        t.set("snap_to_ground", TwoArg(t) { _, v -> snapToGround = v.checkboolean() })
        t.set("angles", TwoArg(t) { _, v ->
            if (v.istable()) {
                angles.clear()
                val table = v.checktable()
                for (i in 1..table.length()) {
                    angles.add(table.get(i).checkdouble())
                }
            }
        })
        t.set("on_hit", TwoArg(t) { _, v -> onHitLuaFunc = v })

        t.set("start", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val hitCallback: ((Player) -> Unit)? = if (onHitLuaFunc.isfunction()) {
                    { victim: Player ->
                        val adapter = BukkitPlayerAdapter(victim)
                        onHitLuaFunc.call(CoerceJavaToLua.coerce(adapter))
                    }
                } else null

                val effect = LineSpawnEffect(scriptId, player.uniqueId, player, count, spacing, delayTicks, angles, snapToGround, hitCallback)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildTempFlyTable(scriptId: String, player: Player): LuaTable {
        val t = LuaTable()
        var duration = 100; var speed = 0.05f

        t.set("duration", TwoArg(t) { _, v -> duration = v.checkint().coerceIn(20, 1200) })
        t.set("speed", TwoArg(t) { _, v -> speed = v.checkdouble().toFloat().coerceIn(0.01f, 1.0f) })

        t.set("start", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val effect = TempFlyEffect(scriptId, player.uniqueId, player, duration, speed)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildRevealTargetsTable(scriptId: String, player: Player): LuaTable {
        val t = LuaTable()
        var radius = 50.0; var duration = 200; var color = "GREEN"

        t.set("radius", TwoArg(t) { _, v -> radius = v.checkdouble().coerceIn(5.0, 200.0) })
        t.set("duration", TwoArg(t) { _, v -> duration = v.checkint().coerceIn(20, 1200) })
        t.set("color", TwoArg(t) { _, v -> color = v.checkjstring() })

        t.set("start", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val effect = RevealTargetsEffect(scriptId, player.uniqueId, player, radius, duration, color)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildScreenTintTable(victim: Player): LuaTable {
        val t = LuaTable()
        var r = 0; var g = 0; var b = 0; var alpha = 0.5f; var duration = 40

        t.set("color", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                r = args.arg(2).checkint().coerceIn(0, 255)
                g = args.arg(3).checkint().coerceIn(0, 255)
                b = args.arg(4).checkint().coerceIn(0, 255)
                return args.arg(1)
            }
        })
        t.set("alpha", TwoArg(t) { _, v -> alpha = v.checkdouble().toFloat().coerceIn(0.0f, 1.0f) })
        t.set("duration", TwoArg(t) { _, v -> duration = v.checkint().coerceIn(10, 600) })

        t.set("show", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                ObserverHook.playScreenTint(victim, r, g, b, alpha, duration)
                return LuaValue.NIL
            }
        })
        return t
    }

    private fun buildScreenShakeTable(victim: Player): LuaTable {
        val t = LuaTable()
        var intensity = 1.0f; var duration = 40

        t.set("intensity", TwoArg(t) { _, v -> intensity = v.checkdouble().toFloat().coerceIn(0.1f, 5.0f) })
        t.set("duration", TwoArg(t) { _, v -> duration = v.checkint().coerceIn(10, 600) })

        t.set("show", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                ObserverHook.playScreenshake(victim, intensity, duration)
                return LuaValue.NIL
            }
        })
        return t
    }

    private fun buildShapeTable(type: String): LuaTable {
        val t = LuaTable()
        var center: Location? = null
        var particle = Particle.SOUL_FIRE_FLAME
        var radius = 1.0; var height = 3.0

        t.set("center", TwoArg(t) { _, v ->
            if (v.isuserdata(BukkitLocationAdapter::class.java)) {
                center = (v.checkuserdata(BukkitLocationAdapter::class.java) as BukkitLocationAdapter).getBukkitLocation()
            }
        })
        t.set("particle", TwoArg(t) { _, v ->
            try { particle = Particle.valueOf(v.checkjstring().uppercase()) } catch (_: Exception) {}
        })
        t.set("radius", TwoArg(t) { _, v -> radius = v.checkdouble().coerceIn(0.1, 20.0) })
        t.set("height", TwoArg(t) { _, v -> height = v.checkdouble().coerceIn(0.1, 20.0) })

        t.set("draw", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val loc = center ?: return LuaValue.NIL
                when (type.lowercase()) {
                    "dna", "dna_helix" -> ParticleShapesUtils.drawDnaHelix(loc, particle, radius, height)
                    "shockwave" -> ParticleShapesUtils.drawShockwave(loc, particle, radius)
                    "vortex" -> ParticleShapesUtils.drawVortex(loc, particle, radius, height)
                    "infinity" -> ParticleShapesUtils.drawSphere(loc, particle, radius)
                    "sphere" -> ParticleShapesUtils.drawSphere(loc, particle, radius)
                    "heart" -> ParticleShapesUtils.drawHeart(loc, particle, radius)
                    "star" -> ParticleShapesUtils.drawStar(loc, particle, radius)
                    "tornado" -> ParticleShapesUtils.drawTornado(loc, particle, radius, height)
                    "wings" -> {
                        // Wings in ParticleShapesUtils require a Player, not a Location
                        // We will just do a sphere as fallback if called with Location
                        ParticleShapesUtils.drawSphere(loc, particle, radius)
                    }
                }
                return LuaValue.NIL
            }
        })
        return t
    }

    private fun buildTrapTable(scriptId: String, player: Player, loc: Location): LuaTable {
        val table = LuaTable()
        
        table.set("on_trigger", object : TwoArgFunction() {
            override fun call(self: LuaValue, funcArg: LuaValue): LuaValue {
                if (funcArg.isfunction()) {
                    self.set("_callback", funcArg)
                }
                return self
            }
        })
        
        table.set("register", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val funcArg = self.get("_callback")
                if (funcArg.isfunction()) {
                    val trap = TrapDefinition(
                        ownerUuid = player.uniqueId,
                        killerId = scriptId,
                        location = loc
                    ) { p, l ->
                        funcArg.invoke(
                            CoerceJavaToLua.coerce(BukkitPlayerAdapter(p)),
                            CoerceJavaToLua.coerce(BukkitLocationAdapter(l))
                        )
                    }
                    WorldTrapRegistry.registerTrap(trap)
                }
                return LuaValue.NIL
            }
        })
        
        return table
    }

    // ═══════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════

    /** Builds a Lua handle table with stop() and remove() */
    private fun buildHandle(effect: EffectHandle): LuaTable {
        val h = LuaTable()
        h.set("stop", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue { effect.stop(); return LuaValue.NIL }
        })
        h.set("remove", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue { effect.remove(); return LuaValue.NIL }
        })
        h.set("is_alive", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue = LuaValue.valueOf(effect.isAlive)
        })
        return h
    }



    private fun buildBaitTrapTable(scriptId: String, player: Player, location: Location): LuaTable {
        val t = LuaTable()
        var markerItem: String? = null
        var orbitParticle: String? = null
        var triggerRadius = 3.5
        var maxTicks = 400
        var onTriggerLuaFunc: LuaValue = LuaValue.NIL

        t.set("marker_item", TwoArg(t) { _, v -> markerItem = v.checkjstring() })
        t.set("orbit_particle", TwoArg(t) { _, v -> orbitParticle = v.checkjstring() })
        t.set("trigger_radius", TwoArg(t) { _, v -> triggerRadius = v.checkdouble().coerceIn(0.5, 10.0) })
        t.set("max_ticks", TwoArg(t) { _, v -> maxTicks = v.checkint().coerceIn(20, 1200) })
        t.set("on_trigger", TwoArg(t) { _, v -> onTriggerLuaFunc = v })

        t.set("spawn", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val triggerCallback: ((Player) -> Unit)? = if (onTriggerLuaFunc.isfunction()) {
                    { victim: Player ->
                        val adapter = BukkitPlayerAdapter(victim)
                        onTriggerLuaFunc.call(CoerceJavaToLua.coerce(adapter))
                    }
                } else null

                val effect = BaitTrapEffect(scriptId, player.uniqueId, player, location, markerItem, orbitParticle, triggerRadius, maxTicks, triggerCallback)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildFormationTable(scriptId: String, player: Player, location: Location): LuaTable {
        val t = LuaTable()
        var shape = "circle"
        var count = 3
        var material = "BEACON"
        var radius = 2.0
        var durationTicks = 30
        var onExpireLuaFunc: LuaValue = LuaValue.NIL

        t.set("shape", TwoArg(t) { _, v -> shape = v.checkjstring() })
        t.set("count", TwoArg(t) { _, v -> count = v.checkint().coerceIn(1, 20) })
        t.set("material", TwoArg(t) { _, v -> material = v.checkjstring() })
        t.set("radius", TwoArg(t) { _, v -> radius = v.checkdouble().coerceIn(0.1, 10.0) })
        t.set("duration", TwoArg(t) { _, v -> durationTicks = v.checkint().coerceIn(1, 6000) })
        t.set("on_expire", TwoArg(t) { _, v -> onExpireLuaFunc = v })

        t.set("show", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val expireCallback: ((Location) -> Unit)? = if (onExpireLuaFunc.isfunction()) {
                    { loc: Location ->
                        val adapter = BukkitLocationAdapter(loc)
                        onExpireLuaFunc.call(CoerceJavaToLua.coerce(adapter))
                    }
                } else null

                val effect = FormationEffect(scriptId, player.uniqueId, location, shape, count, material, radius, durationTicks, expireCallback)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildSinkingBlockTable(scriptId: String, player: Player, location: Location): LuaTable {
        val t = LuaTable()
        var material = "OBSIDIAN"
        var sinkTicks = 20
        var durationTicks = 40
        var onRemoveLuaFunc: LuaValue = LuaValue.NIL

        t.set("material", TwoArg(t) { _, v -> material = v.checkjstring() })
        t.set("sink_ticks", TwoArg(t) { _, v -> sinkTicks = v.checkint().coerceIn(1, 200) })
        t.set("duration", TwoArg(t) { _, v -> durationTicks = v.checkint().coerceIn(1, 6000) })
        t.set("on_remove", TwoArg(t) { _, v -> onRemoveLuaFunc = v })

        t.set("show", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val removeCallback: ((Location) -> Unit)? = if (onRemoveLuaFunc.isfunction()) {
                    { loc: Location ->
                        val adapter = BukkitLocationAdapter(loc)
                        onRemoveLuaFunc.call(CoerceJavaToLua.coerce(adapter))
                    }
                } else null

                val effect = SinkingBlockEffect(scriptId, player.uniqueId, location, material, sinkTicks, durationTicks, removeCallback)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildSpiralParticleTable(scriptId: String, player: Player, location: Location): LuaTable {
        val t = LuaTable()
        var particle1 = "SQUID_INK"
        var particle2 = "SCULK_SOUL"
        var maxTicks = 40
        var onFinishLuaFunc: LuaValue = LuaValue.NIL

        t.set("particle_1", TwoArg(t) { _, v -> particle1 = v.checkjstring() })
        t.set("particle_2", TwoArg(t) { _, v -> particle2 = v.checkjstring() })
        t.set("duration", TwoArg(t) { _, v -> maxTicks = v.checkint().coerceIn(1, 6000) })
        t.set("on_finish", TwoArg(t) { _, v -> onFinishLuaFunc = v })

        t.set("start", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val finishCallback: ((Location) -> Unit)? = if (onFinishLuaFunc.isfunction()) {
                    { loc: Location ->
                        val adapter = BukkitLocationAdapter(loc)
                        onFinishLuaFunc.call(CoerceJavaToLua.coerce(adapter))
                    }
                } else null

                val effect = SpiralParticleEffect(scriptId, player.uniqueId, location, particle1, particle2, maxTicks, finishCallback)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildParticleBurstTable(location: Location): LuaTable {
        val t = LuaTable()
        var particleName = "FIREWORK"
        var count = 3
        var offsetX = 0.5; var offsetY = 0.5; var offsetZ = 0.5
        var speed = 0.0

        t.set("type", TwoArg(t) { _, v -> particleName = v.checkjstring() })
        t.set("count", TwoArg(t) { _, v -> count = v.checkint().coerceIn(1, 100) })
        t.set("offset", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                offsetX = args.arg(2).optdouble(0.5).coerceIn(0.0, 10.0)
                offsetY = args.arg(3).optdouble(0.5).coerceIn(0.0, 10.0)
                offsetZ = args.arg(4).optdouble(0.5).coerceIn(0.0, 10.0)
                return args.arg(1)
            }
        })
        t.set("spread", TwoArg(t) { _, v -> speed = v.checkdouble().coerceIn(0.0, 5.0) })

        t.set("show", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                GameplayFunctions.spawnParticleBurst(location, particleName, count, offsetX, offsetY, offsetZ, speed)
                return LuaValue.NIL
            }
        })
        return t
    }

    /** Unwraps BukkitPlayerAdapter userdata → Player */
    private fun unwrapPlayer(luaVal: LuaValue): Player? {
        if (luaVal.isnil()) return null
        return try {
            val adapter = luaVal.checkuserdata(BukkitPlayerAdapter::class.java) as BukkitPlayerAdapter
            adapter.getPlayer()
        } catch (_: Exception) { null }
    }

    /** Unwraps BukkitLocationAdapter userdata → Location */
    private fun unwrapLocation(luaVal: LuaValue): Location? {
        if (luaVal.isnil()) return null
        return try {
            val adapter = luaVal.checkuserdata(BukkitLocationAdapter::class.java) as BukkitLocationAdapter
            adapter.getBukkitLocation()
        } catch (_: Exception) { null }
    }

    /**
     * Unwraps cualquier userdata que implemente HasLocation → Location.
     * Funciona con BukkitPlayerAdapter, BukkitLocationAdapter, y cualquier
     * wrapper futuro que implemente la interfaz.
     */
    private fun unwrapHasLocation(luaVal: LuaValue): Location? {
        if (luaVal.isnil()) return null
        return try {
            val obj = luaVal.checkuserdata(HasLocation::class.java) as HasLocation
            obj.bukkitLocation()
        } catch (_: Exception) { null }
    }

    /** Wraps a list of Players as a Lua table of BukkitPlayerAdapter userdata */
    private fun playersToLuaTable(players: List<Player>): LuaTable {
        val t = LuaTable()
        players.forEachIndexed { i, p ->
            t.set(i + 1, CoerceJavaToLua.coerce(BukkitPlayerAdapter(p)))
        }
        return t
    }

    // ═══════════════════════════════════════════════════════
    private fun buildProximityTrapTable(scriptId: String, player: Player, location: org.bukkit.Location): LuaTable {
        val t = LuaTable()
        var model: String? = null
        var particle: String? = null
        var particleRadius = 1.0
        var triggerRadius = 3.5
        var durationTicks = 400
        var onTriggerLuaFunc: LuaValue = LuaValue.NIL

        t.set("model", TwoArg(t) { _, v -> model = v.checkjstring() })
        t.set("particle", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                particle = args.arg(2).checkjstring()
                if (args.narg() >= 3) particleRadius = args.arg(3).optdouble(1.0)
                return args.arg(1)
            }
        })
        t.set("radius", TwoArg(t) { _, v -> triggerRadius = v.checkdouble().coerceIn(0.5, 15.0) })
        t.set("duration", TwoArg(t) { _, v -> durationTicks = v.checkint().coerceIn(1, 1200) })
        t.set("on_trigger", TwoArg(t) { _, v -> onTriggerLuaFunc = v })

        t.set("register", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val triggerCallback: ((Player) -> Unit)? = if (onTriggerLuaFunc.isfunction()) {
                    { victim: Player ->
                        val adapter = liric.mistaken.scripting.adapter.BukkitPlayerAdapter(victim)
                        onTriggerLuaFunc.call(CoerceJavaToLua.coerce(adapter))
                    }
                } else null

                val effect = ProximityTrapEffect(scriptId, player.uniqueId, player, location, model, particle, particleRadius, triggerRadius, durationTicks, triggerCallback)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    private fun buildSequenceTable(scriptId: String, player: Player, location: org.bukkit.Location): LuaTable {
        val t = LuaTable()
        val steps = mutableListOf<Pair<Long, () -> Unit>>()

        t.set("delay", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val ticks = args.arg(2).checklong()
                val func = args.arg(3).checkfunction()
                steps.add(ticks to { func.call() })
                return args.arg(1)
            }
        })

        t.set("play", object : OneArgFunction() {
            override fun call(self: LuaValue): LuaValue {
                val effect = liric.mistaken.scripting.effects.gameplay.SequenceEffect(scriptId, player.uniqueId, location, steps)
                effect.start()
                EffectRegistry.register(effect)
                return buildHandle(effect)
            }
        })
        return t
    }

    // Builder method helpers for ':' syntax
    // ═══════════════════════════════════════════════════════



    /** Two-arg function that returns self (the builder table) */
    private class TwoArg(
        private val self: LuaTable,
        private val action: (LuaValue, LuaValue) -> Unit
    ) : TwoArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
            action(arg1, arg2)
            return self
        }
    }

    /** Three-arg function that returns self */
    private class ThreeArg(
        private val self: LuaTable,
        private val action: (LuaValue, LuaValue, LuaValue) -> Unit
    ) : ThreeArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
            action(arg1, arg2, arg3)
            return self
        }
    }
}
