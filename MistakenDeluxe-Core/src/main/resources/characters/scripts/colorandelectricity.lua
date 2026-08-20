local killer = {
    id = "colorandelectricity"
}

-- helper para knockback matemático
local function apply_knockback(player, victim, power, y_vel)
    local dx = victim:location():x() - player:location():x()
    local dz = victim:location():z() - player:location():z()
    
    local length = math.sqrt(dx * dx + dz * dz)
    if length == 0 then length = 1 end
    
    local vx = (dx / length) * power
    local vz = (dz / length) * power
    
    victim:velocity_add(vx, y_vel, vz)
end

local function music_loop(player)
    local nearby = player:world():get_players()
    for _, victim in pairs(nearby) do
        play_entity_sound(victim, "mistaken:colorsito", player, 2.0, 1.0)
    end
    
    delay_ticks(1480, function()
        if player:is_valid() then
            music_loop(player)
        end
    end)
end

function on_equip(player)
    orbit(player)
        :materials("PURPLE_WOOL", "BLUE_WOOL", "LIGHT_BLUE_WOOL", "LIME_WOOL", "YELLOW_WOOL", "ORANGE_WOOL", "RED_WOOL")
        :radius(1.4)
        :rotation_speed(0.15)
        :show()
        
    trail(player)
        :particle("DUST", {r=1.0, g=0.0, b=0.5}, 1.1)
        :offset(0.1, 0.1, 0.1)
        :view_radius(25)
        :show()
        
    music_loop(player)
end

function on_unequip(player)
    local nearby = player:world():get_players()
    for _, victim in pairs(nearby) do
        stop_sound(victim, "mistaken:colorsito")
    end
end

function on_skill_1(player)
    -- Vivid Trace
    sound(player, "BLOCK_AMETHYST_BLOCK_CHIME", 1.0, 2.0)
    
    dash(player)
        :speed(1.8)
        :max_ticks(10)
        :hit_radius(2.5)
        :trail_particle("ELECTRIC_SPARK", 10)
        :on_hit(function(victim)
            victim:damage()
            apply_knockback(player, victim, 1.5, 0.4)
            sound(victim, "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1.0, 1.5)
            screen_tint(victim, 0, 255, 255, 0.4, 15)
        end)
        :start()
        
    spawn_tracking_hitbox(player, 2.5, 2.5, 2.5, "CYAN_STAINED_GLASS", 10)
end

function on_skill_2(player)
    -- Color Drain
    sound(player, "BLOCK_CONDUIT_ATTACK_TARGET", 1.0, 1.8)
    draw_instant_hitbox(player:location(), 8.0, 8.0, 8.0, 15, "PURPLE_STAINED_GLASS")
    
    spawn_particle(player:location(), "SQUID_INK", 4.0, 1.0, 4.0, 0.1, 100)
    spawn_particle(player:location(), "WITCH", 4.0, 1.0, 4.0, 0.5, 50)
    
    local nearby = player:world():get_players()
    for _, victim in pairs(nearby) do
        if victim:id() ~= player:id() and player:location():distance_squared(victim:location()) < 64 then
            victim:add_potion_effect("DARKNESS", 0, 100)
            victim:add_potion_effect("BLINDNESS", 0, 100)
            victim:add_potion_effect("SLOWNESS", 2, 100)
            victim:send_message("<color:#aaaaaa>Dame tus colores...</color>")
            screen_tint(victim, 128, 128, 128, 0.6, 100)
        end
    end
end

function on_skill_3(player)
    -- Pulse Static
    spawn_tracking_hitbox(player, 6.0, 6.0, 6.0, "YELLOW_STAINED_GLASS", 20)
    
    local function pulse_loop(ticks)
        if ticks >= 4 then return end
        
        spawn_particle(player:location(), "ELECTRIC_SPARK", 2.0, 2.0, 2.0, 0.05, 20)
        
        local nearby = player:world():get_players()
        for _, victim in pairs(nearby) do
            if victim:id() ~= player:id() and player:location():distance_squared(victim:location()) < 36 then
                victim:damage()
                apply_knockback(player, victim, 0.8, 0.3)
                screen_tint(victim, 255, 255, 0, 0.4, 5)
            end
        end
        
        delay_ticks(5, function()
            pulse_loop(ticks + 1)
        end)
    end
    
    pulse_loop(0)
end

function on_skill_4(player)
    -- Shikisai End
    local loc = player:location()
    draw_instant_hitbox(loc, 15.0, 15.0, 15.0, 20, "ORANGE_STAINED_GLASS")
    
    local target = nil
    local nearby = player:world():get_players()
    for _, victim in pairs(nearby) do
        if victim:id() ~= player:id() and loc:distance_squared(victim:location()) < 225 then
            target = victim
            break
        end
    end
    
    if target ~= nil then
        player:teleport(target:location())
        
        delay_ticks(1, function()
            spawn_particle(player:location(), "TOTEM_OF_UNDYING", 2.0, 2.0, 2.0, 0.5, 200)
            spawn_particle(player:location(), "END_ROD", 2.0, 2.0, 2.0, 0.1, 100)
            
            screenshake(target, 1.5, 30)
            screen_tint(target, 255, 0, 255, 0.5, 30)
            
            target:send_message("<color:#ff00ff>¡SOBRECARGA CROMÁTICA!</color>")
            target:velocity_add(0, 1.2, 0)
        end)
    end
end

function on_finisher(player, victim)
    -- ColorAndElectricity doesn't have any custom finisher logic in Kotlin.
    -- I'll leave it empty.
end

return killer
