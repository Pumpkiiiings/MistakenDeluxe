local killer = {
    id = "herobrine"
}

local function loop_wings(player)
    draw_wings(player, "SOUL_FIRE_FLAME")
    delay_ticks(5, function()
        if player:is_valid() then
            loop_wings(player)
        end
    end)
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
            victim:damage()
            victim:damage()
            victim:damage()
            sound(victim, "ENTITY_WITHER_BREAK_BLOCK", 1.0, 0.8)
        end)
        :on_block_hit(function()
            player:damage()
            player:damage()
            player:damage()
            sound(player, "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1.0, 0.5)
        end)
        :start()
end

function on_skill_2(player)
    -- Salto Dimensional
    local loc = player:location()
    draw_vortex(loc, "REVERSE_PORTAL", 5.0, 3.0)
    sound(player, "ITEM_CHORUS_FRUIT_TELEPORT", 1.0, 0.5)
    
    -- Teleporting to a forward destination
    local dir = loc:direction()
    dir:multiply(15.0)
    local dest = loc:clone():add(dir)
    player:teleport(dest)
    
    local newLoc = player:location()
    draw_vortex(newLoc, "REVERSE_PORTAL", 5.0, 3.0)
    sound(player, "ITEM_CHORUS_FRUIT_TELEPORT", 1.0, 0.5)
end

function on_skill_3(player)
    -- Estrella Wither
    launch_wither_skull(player, 1.5, 100, function(victim)
        victim:damage()
        victim:damage()
        victim:add_potion_effect("WITHER", 3, 100)
    end)
    sound(player, "ENTITY_WITHER_SHOOT", 1.0, 1.0)
end

function on_skill_4(player)
    -- Error de Mundo
    sound(player, "ENTITY_ENDER_DRAGON_GROWL", 1.0, 0.5)
    
    local targets = player:world():get_players()
    apply_glowing_team(player, targets, "DARK_PURPLE", 100)
    
    for _, victim in pairs(targets) do
        if victim:id() ~= player:id() then
            victim:add_potion_effect("DARKNESS", 1, 100)
            victim:add_potion_effect("BLINDNESS", 1, 100)
            victim:add_potion_effect("SLOWNESS", 2, 100)
            sound(victim, "ENTITY_WARDEN_HEARTBEAT", 1.0, 0.5)
            screenshake(victim, 0.05, 100)
        end
    end
end

function on_finisher(player, victim)
    local type = math.random(1, 3)
    local loc = victim:location()
    
    if type == 1 then
        -- Cruz de obsidiana
        sound(player, "ENTITY_LIGHTNING_BOLT_THUNDER", 1.0, 0.5)
        spawn_temp_block(loc, "OBSIDIAN", 0.0, 1.0, 0.0, 1.0, 3.0, 1.0, 60)
        spawn_temp_block(loc, "OBSIDIAN", 0.0, 2.0, 0.0, 3.0, 1.0, 1.0, 60)
        draw_shockwave(loc, "SONIC_BOOM", 5.0)
        
    elseif type == 2 then
        -- Falsa AscensiÃ³n
        sound(player, "ENTITY_BAT_AMBIENT", 1.0, 1.5)
        sound(player, "ENTITY_WITHER_SPAWN", 1.0, 0.5)
        spawn_fake_swarm(loc, 15, 60)
        draw_tornado(loc, "CAMPFIRE_COSY_SMOKE", 6.0, 3.0)
        
    else
        -- Altar del VacÃ­o
        sound(player, "BLOCK_PORTAL_TRIGGER", 1.0, 0.8)
        spawn_temp_block(loc, "CRYING_OBSIDIAN", 0.0, -1.0, 0.0, 3.0, 1.0, 3.0, 60)
        spawn_temp_block(loc, "LODESTONE", 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 60)
        draw_dna_helix(loc, "SOUL_FIRE_FLAME", 2.0, 5.0)
    end
end

return killer
