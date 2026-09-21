local warden = {}

-- Variables para el combo de ataques
warden.combo_step = 0
warden.last_attack = 0

function warden.on_skill(player, skill_slot)
    local uuid = player:get_uuid()

    if skill_slot == 1 then
        -- SLAM
        api:play_animation(player, "slam", 80)
        
        api:leap(player, 1.5, 1.0, function()
            api:play_animation(player, "idle", 80)
            local loc = player:get_location()
            api:sound(loc, "ENTITY_GENERIC_EXPLODE", 2.0, 0.5)
            api:spawn_particle(loc, "EXPLOSION", 5)
            
            -- Matar cercanos (radio 4 = 16 distance squared aprox)
            local victims = api:nearby_valid_targets(player, 4.0)
            for _, victim in ipairs(victims) do
                api:kill_player(victim)
            end

            -- Efecto de shockwave expansivo (0 a 10 bloques)
            api:run_timer(1, 2, 11, function(radius)
                local r = radius:toint()
                local cx = loc:get_x()
                local cy = loc:get_y()
                local cz = loc:get_z()
                local world = loc:get_world()

                for x = -r, r do
                    for z = -r, r do
                        if math.max(math.abs(x), math.abs(z)) == r then
                            local bLoc = api:location(world, cx + x, cy - 1, cz + z)
                            -- Spawnea un bloque cayendo (DIRT como ejemplo por defecto si no podemos sacar el tipo dinamicamente)
                            api:spawn_falling_block(bLoc, "DEEPSLATE", 0.0, 0.4, 0.0, 20)
                        end
                    end
                end
                api:sound(loc, "BLOCK_STONE_BREAK", 1.0, 0.5)
            end)
        end)
    
    elseif skill_slot == 2 then
        -- RAGE
        api:play_animation(player, "rage", 80)
        api:sound(player:get_location(), "ENTITY_WARDEN_ANGRY", 1.5, 1.0)
        api:add_potion_effect(player, "SPEED", 300, 1)
        api:add_potion_effect(player, "STRENGTH", 300, 1)
        api:add_potion_effect(player, "RESISTANCE", 300, 0)
    
    elseif skill_slot == 3 then
        -- SNIFF WALK
        api:play_animation(player, "sniff_walk", 90)
        api:sound(player:get_location(), "ENTITY_WARDEN_SNIFF", 1.5, 1.0)
        api:set_frozen(player, 100)
        
        api:delay(100, function()
            api:play_animation(player, "idle", 80)
            api:sound(player:get_location(), "ENTITY_WARDEN_HEARTBEAT", 2.0, 1.0)
            
            local victims = api:nearby_valid_targets(player, 50.0)
            for _, victim in ipairs(victims) do
                api:add_potion_effect(victim, "GLOWING", 300, 0)
                api:send_message(victim, "<red>¡El Warden te ha olfateado!")
            end
        end)
    
    elseif skill_slot == 4 then
        -- ATTACK COMBO
        local now = api:get_time()
        if (now - warden.last_attack) > 1500 then
            warden.combo_step = 0
        end
        
        local anim_name = "swipe_1"
        if warden.combo_step == 1 then anim_name = "swipe_2"
        elseif warden.combo_step == 2 then anim_name = "swipe_3"
        end
        
        api:play_animation(player, anim_name, 50)
        
        -- Lógica del bloque de luz
        local eye_loc = player:get_eye_location()
        api:send_fake_block(player, eye_loc, "LIGHT", 20)
        
        warden.last_attack = now
        warden.combo_step = (warden.combo_step + 1) % 3
    end
end

return warden
