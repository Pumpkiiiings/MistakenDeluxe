local killer = {
    id = "romeo"
}

function on_equip(player)
    player:set_scale(1.0)
    
    orbit(player)
        :materials("COMMAND_BLOCK")
        :radius(1.5)
        :rotation_speed(0.12)
        :show()
end

function on_unequip(player)
    player:reset_scale()
end

function on_skill_1(player)
    -- Admin Dash
    sound(player, "ENTITY_FIREWORK_ROCKET_LAUNCH", 1.0, 0.5)
    
    dash(player)
        :speed(1.4)
        :max_ticks(100)
        :trail_particle("ELECTRIC_SPARK", 5)
        :stop_on_block(true)
        :on_hit(function(victim)
            damage(victim)
            victim:velocity_add(0, 0.4, 0)
            sound(player, "ENTITY_PLAYER_ATTACK_CRIT", 1.2, 0.8)
            
            -- Glitch effect
            sound(victim, "ENTITY_ENDERMAN_TELEPORT", 1.0, 0.1)
            screen_tint(victim):color(0, 255, 100):alpha(0.4):duration(15):show()
            screen_shake(victim):intensity(1.0):duration(10):show()
        end)
        :on_block_hit(function()
            damage(player)
            sound(player, "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1.0, 0.5)
        end)
        :start()
end

function on_skill_2(player)
    -- Admin Vision
    screen_tint(player):color(0, 255, 0):alpha(0.2):duration(20):show()
    
    local targets = player:world():get_players()
    reveal_targets(player)
    
    for _, victim in pairs(targets) do
        if victim:id() ~= player:id() and player:location():distance_squared(victim:location()) < 10000 then
            apply_effect(victim, "GLOWING", 0, 200)
            spawn_particle(victim:location():clone():add(0, 1, 0), "SONIC_BOOM", 0, 0, 0, 0, 1)
        end
    end
end

function on_skill_3(player)
    -- Triple Colmillo
    local loc = player:location()
    
    local function spawn_fangs(offset_angle)
        local dir = loc:direction():rotate_y(offset_angle)
        dir:set_y(0)
        dir:normalize()
        
        local currentLoc = loc:clone()
        local function fang_step(step)
            if step > 15 then return end
            
            currentLoc:add(dir)
            local spawn_loc = currentLoc:clone()
            
            if not spawn_loc:is_solid() then
                spawn_evoker_fang(spawn_loc)
                spawn_particle(spawn_loc, "PORTAL", 0.2, 0.5, 0.2, 0.5, 15)
                
                local nearby = player:world():get_players()
                for _, victim in pairs(nearby) do
                    if victim:id() ~= player:id() and spawn_loc:distance_squared(victim:location()) < 2.25 then
                        damage(victim)
                        victim:velocity_add(0, 0.5, 0)
                        apply_effect(victim, "DARKNESS", 0, 40)
                        screen_tint(victim):color(0, 0, 0):alpha(0.8):duration(20):show()
                    end
                end
            end
            
            delay_ticks(2, function()
                fang_step(step + 1)
            end)
        end
        fang_step(1)
    end
    
    spawn_fangs(-0.436332) -- -25 degrees
    spawn_fangs(0.0)       -- 0 degrees
    spawn_fangs(0.436332)  -- 25 degrees
end

function on_skill_4(player)
    -- Nether Star
    projectile(player)
        :item("NETHER_STAR")
        :speed(1.5)
        :max_ticks(40)
        :hit_radius(1.5)
        :trail_particle("END_ROD", 3)
        :on_impact_particle("EXPLOSION_EMITTER")
        :on_hit(function(victim)
            damage(victim)
            sound(victim, "ENTITY_GENERIC_EXPLODE", 2.0, 0.5)
            spawn_particle(victim:location(), "FIREWORK", 1.0, 1.0, 1.0, 0.1, 50)
            screen_shake(victim):intensity(1.2):duration(25):show()
        end)
        :launch()
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
    local type = math.random(1, 3)
    local loc = victim:location()
    
    if type == 1 then
        -- //SET 0 (BORRADO DE CÓDIGO)
        sound(loc, "BLOCK_BEACON_DEACTIVATE", 1.5, 0.5)
        
        shape("cube"):center(loc:clone():add(0, 1, 0)):particle("FLAME"):radius(2.0):draw()
        
        delay_ticks(20, function()
            sound(loc, "ENTITY_ENDERMAN_TELEPORT", 2.0, 2.0)
            spawn_particle(loc:clone():add(0, 1, 0), "FIREWORK", 0.5, 0.5, 0.5, 0.0, 3)
            spawn_particle(loc, "WHITE_ASH", 1.0, 1.0, 1.0, 0.5, 300)
        end)
        
    elseif type == 2 then
        -- JUICIO DEL ADMINISTRADOR
        sinking_block(player, loc)
            :material("COMMAND_BLOCK")
            :sink_ticks(10)
            :duration(30)
            :show()
            
        delay_ticks(11, function()
            sound(loc, "ENTITY_ZOMBIE_BREAK_WOODEN_DOOR", 2.0, 0.1)
            sound(loc, "BLOCK_ANVIL_LAND", 2.0, 0.5)
            spawn_particle(loc, "EXPLOSION", 0, 0, 0, 0, 2)
            spawn_particle(loc, "CAMPFIRE_COSY_SMOKE", 1.5, 0.5, 1.5, 0.1, 50)
        end)
        
    else
        -- ASCENSIÓN A LA TERMINAL
        sound(loc, "BLOCK_PORTAL_TRIGGER", 0.5, 2.0)
        
        local function ascend_loop(ticks)
            if ticks >= 30 then
                sound(loc:clone():add(0, 4, 0), "ENTITY_FIREWORK_ROCKET_BLAST", 1.0, 0.5)
                spawn_particle(loc:clone():add(0, 4, 0), "FIREWORK", 0.5, 0.5, 0.5, 0.2, 100)
                spawn_particle(loc:clone():add(0, 4, 0), "END_ROD", 0.5, 0.5, 0.5, 0.5, 50)
                return
            end
            
            local current_y = (ticks / 30.0) * 4.0
            spawn_particle(loc:clone():add(0, current_y, 0), "END_ROD", 0, 0, 0, 0, 1)
            
            delay_ticks(1, function()
                ascend_loop(ticks + 1)
            end)
        end
        ascend_loop(0)
    end
end

return killer
