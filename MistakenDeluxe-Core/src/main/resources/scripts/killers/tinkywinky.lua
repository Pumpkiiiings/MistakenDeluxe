local killer = {
    id = "tinkywinky"
}

-- ─── PASIVA: MELODÍA CORROMPIDA ───
local function pasiva_loop(player)
    local nearby = player:world():get_players()
    for i = 1, nearby.length do
        local victim = nearby[i]
        if victim:id() ~= player:id() and player:location():distance_squared(victim:location()) < 64 then
            apply_effect(victim, "DARKNESS", 0, 30)
            sound(victim, "AMBIENT_CAVE", 0.4, 0.5)
        end
    end
    
    delay_ticks(60, function()
        if player:is_valid() then
            pasiva_loop(player)
        end
    end)
end

function on_equip(player)
    player:set_scale(1.1)
    
    orbit(player)
        :virtual_item("SLIME_BALL", "PURPLE_DYE", "MUSIC_DISC_CAT")
        :radius(1.4)
        :rotation_speed(0.10)
        :show()
        
    trail(player)
        :particle("WITCH")
        :offset(0.18, 0.18, 0.18)
        :view_radius(25)
        :show()
        
    pasiva_loop(player)
end

function on_skill_1(player)
    -- Bolsa Magica
    projectile(player)
        :model("item", "SNOWBALL")
        :speed(1.8)
        :max_ticks(80)
        :hit_radius(1.0)
        :trail_particle("WITCH", 2)
        :on_hit(function(victim)
            apply_effect(victim, "GLOWING", 0, 100)
            apply_effect(victim, "SLOWNESS", 0, 40)
            sound(victim, "ENTITY_ENDERMAN_STARE", 1.0, 0.3)
            victim:send_message("<color:#ff0000>Has sido marcado por Tinky Winky...</color>")
            spawn_particle(victim:location():clone():add(0, 1, 0), "WITCH", 0.5, 0.5, 0.5, 0.1, 30)
        end)
        :launch()
end

function on_skill_2(player)
    -- Paso Silencioso
    apply_effect(player, "INVISIBILITY", 0, 60)
    apply_effect(player, "SPEED", 1, 60)
    sound(player, "ENTITY_ENDERMAN_TELEPORT", 0.8, 1.5)
    spawn_particle(player:location():clone():add(0, 1, 0), "LARGE_SMOKE", 0.3, 0.5, 0.3, 0.02, 20)
    
    delay_ticks(60, function()
        spawn_particle(player:location():clone():add(0, 1, 0), "WITCH", 0.3, 0.5, 0.3, 0.05, 25)
        sound(player, "ENTITY_PHANTOM_AMBIENT", 0.6, 0.5)
    end)
end

function on_skill_3(player)
    -- Senial TV
    local afectados = 0
    local nearby = player:world():get_players()
    for i = 1, nearby.length do
        local victim = nearby[i]
        if victim:id() ~= player:id() and player:location():distance_squared(victim:location()) < 100 then
            apply_effect(victim, "BLINDNESS", 0, 60)
            apply_effect(victim, "NAUSEA", 0, 80)
            apply_effect(victim, "DARKNESS", 0, 80)
            sound(victim, "BLOCK_NOTE_BLOCK_BASS", 2.0, 0.1)
            victim:send_message("<color:#800080>La señal te distorsiona...</color>")
            afectados = afectados + 1
        end
    end
    
    local loc = player:location()
    spawn_particle(loc:clone():add(0, 1, 0), "FIREWORK", 1.0, 1.0, 1.0, 0, 3)
    spawn_particle(loc:clone():add(0, 1.5, 0), "ELECTRIC_SPARK", 1.5, 1.5, 1.5, 0.1, 30)
    sound(loc, "BLOCK_BEACON_AMBIENT", 2.0, 0.1)
    
    if afectados == 0 then
        player:send_message("<color:#aaaaaa>Nadie cerca...</color>")
    end
end

function on_skill_4(player)
    -- Ultima Cancion
    local loc = player:location()
    sound(loc, "ENTITY_WARDEN_SONIC_BOOM", 2.0, 0.3)
    sound(loc, "ENTITY_ENDER_DRAGON_GROWL", 1.0, 0.5)
    
    local function fang_loop(ticks)
        if ticks >= 40 then return end
        
        local angle = ticks * 0.4
        local radius = ticks * 0.25
        local x = radius * math.cos(angle)
        local z = radius * math.sin(angle)
        local spawn_loc = loc:clone():add(x, 0.0, z)
        
        spawn_evoker_fang(spawn_loc)
        spawn_particle(spawn_loc:clone():add(0, 1, 0), "WITCH", 0.1, 0.1, 0.1, 0.01, 3)
        
        local nearby = player:world():get_players()
        for i = 1, nearby.length do
            local victim = nearby[i]
            if victim:id() ~= player:id() and spawn_loc:distance_squared(victim:location()) < 2.25 then
                damage(victim)
                apply_effect(victim, "DARKNESS", 0, 60)
                sound(victim, "ENTITY_ENDERMAN_SCREAM", 1.0, 0.2)
            end
        end
        
        delay_ticks(1, function()
            fang_loop(ticks + 1)
        end)
    end
    
    fang_loop(0)
end

function on_finisher(player, victim)
    local type = math.random(1, 3)
    local loc = victim:location()
    
    if type == 1 then
        -- BOLSA DEL CAOS
        sound(loc, "ENTITY_ENDERMAN_STARE", 1.5, 0.1)
        
        local function chaos_loop(ticks)
            if ticks >= 50 then
                sound(loc, "ENTITY_WARDEN_SONIC_BOOM", 1.0, 1.5)
                spawn_particle(loc:clone():add(0, 1, 0), "SONIC_BOOM", 0, 0, 0, 0, 1)
                return
            end
            
            local angle = ticks * 0.6
            local radius = 3.0 - (ticks * 0.06)
            local x = radius * math.cos(angle)
            local z = radius * math.sin(angle)
            local y = ticks * 0.08
            
            spawn_particle(loc:clone():add(x, y, z), "SCULK_SOUL", 0.05, 0.05, 0.05, 0.0, 2)
            spawn_particle(loc:clone():add(-x, y * 0.5, -z), "WITCH", 0.05, 0.05, 0.05, 0.0, 1)
            
            delay_ticks(1, function()
                chaos_loop(ticks + 1)
            end)
        end
        
        chaos_loop(0)
        
    elseif type == 2 then
        -- SEÑAL PERDIDA
        sound(loc, "BLOCK_BEACON_ACTIVATE", 1.5, 0.3)
        shape("vortex"):center(loc):particle("WITCH"):radius(4.0):draw()
        
        delay_ticks(35, function()
            sound(loc, "ENTITY_ENDERMAN_DEATH", 1.0, 0.5)
            spawn_particle(loc, "LARGE_SMOKE", 1.5, 1.5, 1.5, 0.05, 60)
        end)
        
    else
        -- MELODIA FINAL
        sound(loc, "BLOCK_NOTE_BLOCK_HARP", 2.0, 0.5)
        sound(loc, "ENTITY_WITHER_AMBIENT", 1.0, 0.3)
        
        local function melody_loop(ticks)
            if ticks >= 30 then return end
            
            local angle = math.random() * math.pi * 2
            local r = 0.3 + math.random() * 1.5
            
            spawn_particle(loc:clone():add(r * math.cos(angle), ticks * 0.12, r * math.sin(angle)), "NOTE", 0, 0, 0, 1.0, 1)
            spawn_particle(loc:clone():add(0.0, ticks * 0.1, 0.0), "CAMPFIRE_COSY_SMOKE", 0.2, 0.0, 0.2, 0.02, 2)
            
            if ticks % 6 == 0 then
                sound(loc, "BLOCK_NOTE_BLOCK_HARP", 1.0, 0.5 + (ticks * 0.05))
            end
            
            delay_ticks(2, function()
                melody_loop(ticks + 2)
            end)
        end
        
        melody_loop(0)
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
