local killer = {
    id = "herobrine"
}

local function loop_wings(player)
    -- Deprecated draw_wings removed to prevent memory leaks
end

function on_equip(player)
    player:set_scale(1.05)
    
    orbit(player)
        :materials("NETHERRACK", "NETHER_STAR", "GOLD_BLOCK")
        :radius(1.4)
        :rotation_speed(0.12)
        :show()
        
    trail(player)
        :particle("CLOUD")
        :offset(0.12, 0.12, 0.12)
        :view_radius(20)
        :show()
        
    loop_wings(player)
end

function on_skill_1(player)
    -- Void Dash
    player:spawn_particle("EXPLOSION", 0, 1.0, 0, 5)
    
    dash(player)
        :speed(2.5)
        :max_ticks(12)
        :hit_radius(1.5)
        :stop_on_block(true)
        :trail_particle("SOUL_FIRE_FLAME")
        :on_hit(function(victim)
            damage(victim)
            damage(victim)
            damage(victim)
            sound(victim, "ENTITY_WITHER_BREAK_BLOCK", 1.0, 0.8)
        end)
        :on_block_hit(function()
            damage(player)
            damage(player)
            damage(player)
            sound(player, "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1.0, 0.5)
        end)
        :start()
end

function on_skill_2(player)
    -- Salto Dimensional
    local loc = player:location()
    shape("vortex"):center(loc):particle("REVERSE_PORTAL"):radius(3.0):draw()
    sound(player, "ITEM_CHORUS_FRUIT_TELEPORT", 1.0, 0.5)
    
    -- Teleporting to a forward destination
    local dir = loc:direction()
    dir:multiply(15.0)
    local dest = loc:clone():add(dir)
    player:teleport(dest)
    
    local newLoc = player:location()
    shape("vortex"):center(newLoc):particle("REVERSE_PORTAL"):radius(3.0):draw()
    sound(player, "ITEM_CHORUS_FRUIT_TELEPORT", 1.0, 0.5)
end

function on_skill_3(player)
    -- Estrella Wither
    launch_wither_skull(player, 1.5, 100, function(victim)
        damage(victim)
        damage(victim)
        apply_effect(victim, "WITHER", 3, 100)
    end)
    sound(player, "ENTITY_WITHER_SHOOT", 1.0, 1.0)
end

function on_skill_4(player)
    -- Error de Mundo
    sound(player, "ENTITY_ENDER_DRAGON_GROWL", 1.0, 0.5)
    
    local targets = player:world():get_players()
    reveal_targets(player)
    
    for i = 1, targets.length do
        local victim = targets[i]
        if victim:id() ~= player:id() then
            apply_effect(victim, "DARKNESS", 1, 100)
            apply_effect(victim, "BLINDNESS", 1, 100)
            apply_effect(victim, "SLOWNESS", 2, 100)
            sound(victim, "ENTITY_WARDEN_HEARTBEAT", 1.0, 0.5)
            screen_shake(victim):intensity(0.05):duration(100):show()
        end
    end
end

function on_finisher(player, victim)
    local type = math.random(1, 3)
    local loc = victim:location()
    
    if type == 1 then
        -- Cruz de obsidiana
        sound(player, "ENTITY_LIGHTNING_BOLT_THUNDER", 1.0, 0.5)
        shape("shockwave"):center(loc):particle("SONIC_BOOM"):radius(5.0):draw()
        
    elseif type == 2 then
        -- Falsa Ascensión
        sound(player, "ENTITY_BAT_AMBIENT", 1.0, 1.5)
        sound(player, "ENTITY_WITHER_SPAWN", 1.0, 0.5)
        shape("tornado"):center(loc):particle("CAMPFIRE_COSY_SMOKE"):radius(3.0):draw()
        
    else
        -- Altar del Vacío
        sound(player, "BLOCK_PORTAL_TRIGGER", 1.0, 0.8)
        shape("dna"):center(loc):particle("SOUL_FIRE_FLAME"):radius(2.0):draw()
    end
end


function on_trigger(player, trigger_id)
    if trigger_id == "skill_1" and on_skill_1 then on_skill_1(player)
    elseif trigger_id == "skill_2" and on_skill_2 then on_skill_2(player)
    elseif trigger_id == "skill_3" and on_skill_3 then on_skill_3(player)
    elseif trigger_id == "skill_4" and on_skill_4 then on_skill_4(player)
    end
end

return killer
