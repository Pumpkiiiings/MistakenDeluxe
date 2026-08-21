local killer = {
    id = "entity303"
}

local function loop_wings(player)
    -- Deprecated draw_wings removed to prevent memory leaks
end

function on_equip(player)
    player:set_scale(1.1)
    
    orbit(player)
        :materials("REDSTONE_BLOCK", "MAGMA_BLOCK", "OBSERVER", "NETHER_WART_BLOCK", "CRIMSON_HYPHAE")
        :radius(1.6)
        :rotation_speed(0.15)
        :show()
        
    trail(player)
        :particle("DUST")
        :offset(0.2, 0.2, 0.2)
        :color(1.0, 0.0, 0.0, 1.0)
        :view_radius(20)
        :show()
        
    loop_wings(player)
end

function on_skill_1(player)
    -- Dash Codigo
    sound(player, "ENTITY_GHAST_SHOOT", 1.0, 1.5)
    
    dash(player)
        :speed(2.0)
        :max_ticks(10)
        :hit_radius(2.0)
        :on_hit(function(victim)
            damage(victim)
            sound(victim, "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1.0, 0.5)
        end)
        :start()
end

function on_skill_2(player)
    -- Infeccion Sistema
    projectile(player)
        :model("item", "NETHER_STAR")
        :speed(1.5)
        :max_ticks(40)
        :hit_radius(1.2)
        :trail_particle("ENCHANT", 5)
        :on_impact_particle("EXPLOSION")
        :on_hit(function(victim)
            shape("shockwave"):center(victim:location()):particle("EXPLOSION"):radius(3.0):draw()
            damage(victim)
            apply_effect(victim, "SLOWNESS", 2, 60)
            apply_effect(victim, "HUNGER", 1, 100)
        end)
        :launch()
end

function on_skill_3(player)
    -- Protocolo Vuelo
    temp_fly(player)
        :duration(100)
        :start_sound("BLOCK_BEACON_ACTIVATE")
        :end_sound("BLOCK_BEACON_DEACTIVATE")
        :on_end(function(p)
            apply_effect(p, "WEAKNESS", 0, 60)
        end)
        :start()
end

function on_skill_4(player)
    -- Crash Pantalla
    local targets = player:world():get_players()
    for _, victim in pairs(targets) do
        if victim:id() ~= player:id() then
            if player:location():distance_squared(victim:location()) < 1600 then
                apply_effect(victim, "BLINDNESS", 0, 100)
                sound(victim, "BLOCK_GLASS_BREAK", 1.0, 0.1)
                victim:send_message("<color:#ff0000>[ERROR] SYSTEM_ERROR: CONNECTION LOST</color>")
            end
        end
    end
end

function on_finisher(player, victim)
    local type = math.random(1, 3)
    local loc = victim:location()
    
    if type == 1 then
        -- DELETE
        sound(player, "ENTITY_ILLUSIONER_PREPARE_BLINDNESS", 1.5, 0.5)
        
        local function delete_loop(ticks)
            if ticks >= 30 then
                sound(loc, "ENTITY_GENERIC_EXPLODE", 1.0, 2.0)
                -- Simulating draw_star with a simple explosion since draw_star was missing
                spawn_particle(loc, "EXPLOSION", 0, 0, 0, 0, 1)
                return
            end
            
            spawn_particle(loc, "DUST", 1.0, 0.0, 0.0, 1.0, 10)
            spawn_particle(loc, "DUST", 0.0, 1.0, 1.0, 1.0, 10)
            
            delay_ticks(1, function()
                delete_loop(ticks + 1)
            end)
        end
        
        delete_loop(0)
        
    elseif type == 2 then
        -- CRASH.DUMP
        sound(loc, "BLOCK_ANVIL_LAND", 2.0, 0.1)
        spawn_particle(loc, "EXPLOSION", 0, 0, 0, 0, 1)
        
        delay_ticks(40, function()
            sound(loc, "BLOCK_GLASS_BREAK", 1.0, 0.5)
            shape("tornado"):center(loc):particle("CRIT"):radius(4.0):draw()
        end)
        
    else
        -- FORCE EXIT
        sound(loc, "ENTITY_TNT_PRIMED", 1.0, 1.0)
        
        sinking_block(player, loc)
            :material("TNT")
            :sink_ticks(21)
            :duration(21)
            :show()
        
        delay_ticks(21, function()
            sound(loc, "ENTITY_GENERIC_EXPLODE", 2.0, 1.0)
            spawn_particle(loc:clone():add(0, 3, 0), "EXPLOSION", 0, 0, 0, 0, 2)
        end)
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
