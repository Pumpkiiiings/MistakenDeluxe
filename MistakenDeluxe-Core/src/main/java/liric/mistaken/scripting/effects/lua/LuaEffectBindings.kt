package liric.mistaken.scripting.effects.lua

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
import liric.mistaken.scripting.effects.gameplay.TempFlyEffect
import liric.mistaken.scripting.effects.gameplay.RevealTargetsEffect
import liric.mistaken.scripting.adapter.BukkitLocationAdapter
import liric.mistaken.utils.hooks.ObserverHook
import liric.mistaken.utils.visuals.ParticleShapesUtils
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

/**
 * Registra TODAS las funciones DSL en el sandbox Globals de un script.
 * Cada función recibe ScriptPlayer (userdata) y devuelve LuaTable builders con sintaxis ':'.
 *
 * SEGURIDAD: Lua nunca ve Player/Location/World directos. Los builders unwrapean
 * internamente via BukkitPlayerAdapter.getPlayer() (internal).
 *
 * FOLIA: Cada efecto posee su propio ScheduledTask. Los callbacks de on_hit
 * se ejecutan en el entity scheduler de la víctima.
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
        
        // ──────────── sequence(location) ────────────
        globals.set("sequence", object : OneArgFunction() {
            override fun call(locArg: LuaValue): LuaValue {
                val locAdapter = locArg.checkuserdata(liric.mistaken.scripting.adapter.BukkitLocationAdapter::class.java) as liric.mistaken.scripting.adapter.BukkitLocationAdapter
                val loc = locAdapter.getBukkitLocation()
                return buildSequenceTable(scriptId, loc)
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
        globals.set("damage", object : OneArgFunction() {
            override fun call(victimArg: LuaValue): LuaValue {
                val victim = unwrapPlayer(victimArg) ?: return LuaValue.NIL
                GameplayFunctions.damage(victim)
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

        // ──────────── sound(player, id, volume, pitch) ────────────
        globals.set("sound", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val player = unwrapPlayer(args.arg(1)) ?: return LuaValue.NIL
                val soundName = args.arg(2).checkjstring()
                val vol = args.arg(3).optdouble(1.0).toFloat()
                val pitch = args.arg(4).optdouble(1.0).toFloat()
                GameplayFunctions.playSound(player, soundName, vol, pitch)
                return LuaValue.NIL
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
        var onHitLuaFunc: LuaValue = LuaValue.NIL

        t.set("count", TwoArg(t) { _, v -> count = v.checkint().coerceIn(1, 50) })
        t.set("spacing", TwoArg(t) { _, v -> spacing = v.checkdouble().coerceIn(0.1, 5.0) })
        t.set("delay_ticks", TwoArg(t) { _, v -> delayTicks = v.checklong().coerceIn(0L, 20L) })
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

                val effect = LineSpawnEffect(scriptId, player.uniqueId, player, count, spacing, delayTicks, angles, hitCallback)
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

    /** Unwraps BukkitPlayerAdapter userdata → Player */
    private fun unwrapPlayer(luaVal: LuaValue): Player? {
        if (luaVal.isnil()) return null
        return try {
            val adapter = luaVal.checkuserdata(BukkitPlayerAdapter::class.java) as BukkitPlayerAdapter
            adapter.getPlayer()
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

    private fun buildSequenceTable(scriptId: String, location: org.bukkit.Location): LuaTable {
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
                val effect = liric.mistaken.scripting.effects.gameplay.SequenceEffect(scriptId, java.util.UUID.randomUUID(), location, steps)
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
