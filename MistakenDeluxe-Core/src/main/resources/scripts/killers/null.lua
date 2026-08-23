--[[ Generated with https://github.com/TypeScriptToLua/TypeScriptToLua ]]
---
-- @noSelfInFile
killer = {id = "null"}
function on_equip(player)
    player:set_scale(1.1)
    orbit(player):materials("BEACON", "ENDER_EYE", "NETHER_STAR"):radius(1.5):rotation_speed(0.12):show()
    trail(player):particle("WITCH"):offset(0.2, 0.2, 0.2):view_radius(25):show()
end
function on_unequip(player)
    player:reset_scale()
end
function on_skill_1(player)
    sound(player, "BLOCK_GLASS_BREAK", 1, 0.5)
    local loc = player:location()
    loc:add(0, 1, 0)
    particle_burst(loc):type("FIREWORK"):count(3):offset(0.5, 0.5, 0.5):spread(0):show()
    local targets = nearby_valid_targets(player, 12)
    for i = 1, targets.length do
        local target = targets[i]
        apply_effect(target, "DARKNESS", 0, 200)
        apply_effect(target, "BLINDNESS", 0, 200)
        send_translated(target, "infection-hit", "messages")
    end
end
function on_skill_2(player)
    local loc = player:location()
    bait_trap(player, loc):marker_item("BEACON"):orbit_particle("END_ROD"):trigger_radius(3.5):max_ticks(400):on_trigger(function(victim)
        damage(victim)
        sound(victim, "ENTITY_ENDERMAN_SCREAM", 1, 0.1)
    end):spawn()
end
function on_skill_3(player)
    local target = ray_trace_player(player, 15)
    if target then
        apply_effect(target, "SLOWNESS", 10, 100)
        sound(target, "BLOCK_CHAIN_PLACE", 1, 0.5)
    end
end
function on_skill_4(player)
    line_spawn(player):count(15):spacing(1):delay_ticks(1):snap_to_ground(true):on_hit(function(victim)
        damage(victim)
        apply_effect(victim, "DARKNESS", 0, 40)
    end):start()
end
function on_trigger(player, trigger_id)
    if trigger_id == "skill_1" then
        on_skill_1(player)
    elseif trigger_id == "skill_2" then
        on_skill_2(player)
    elseif trigger_id == "skill_3" then
        on_skill_3(player)
    elseif trigger_id == "skill_4" then
        on_skill_4(player)
    end
end
function on_finisher(player, victim)
    local loc = victim:location()
    local choice = math.floor(math.random() * 3)
    if choice == 0 then
        sound(loc, "ENTITY_ENDERMAN_STARE", 1.5, 0.1)
        spiral_particle(player, loc):particle_1("SQUID_INK"):particle_2("SCULK_SOUL"):duration(40):on_finish(function(final_loc)
            sound(final_loc, "ENTITY_WARDEN_SONIC_BOOM", 1, 1.5)
        end):start()
    elseif choice == 1 then
        sound(loc, "BLOCK_RESPAWN_ANCHOR_SET_SPAWN", 1.5, 0.5)
        sinking_block(player, loc):material("CRYING_OBSIDIAN"):sink_ticks(20):duration(30):on_remove(function(final_loc)
            sound(final_loc, "BLOCK_GLASS_BREAK", 2, 0.1)
        end):show()
    elseif choice == 2 then
        sound(loc, "AMBIENT_CAVE", 2, 0.5)
        formation(player, loc):shape("triangle"):count(3):material("BEACON"):radius(2):duration(30):on_expire(function(final_loc)
            sound(final_loc, "ENTITY_WITHER_DEATH", 1, 1)
        end):show()
    end
end
return killer
