-- ════════════════════════════════════════════════════════
-- NullAsesino Script (LUA)
-- ════════════════════════════════════════════════════════

-- Declaración del killer
local killer = {
    id = "null",
    model = "mistaken:null_model"
}

-- Estado interno (opcional)
local orbes_activados = false
local orbes_handle = nil

function on_equip(player)
    player:log_info("Null equipado")
    player:set_scale(1.2, 1.2)
end

function on_unequip(player)
    player:reset_scale()
    if orbes_handle then
        orbes_handle:stop()
        orbes_handle = nil
    end
end

-- ──────────── Habilidad 1: Radar de Proximidad ────────────
function on_skill_1(player)
    local players = player:nearby_valid_targets(30.0)
    
    if #players == 0 then
        player:send_message("§cNo hay jugadores cercanos.")
        player:sound("BLOCK_NOTE_BLOCK_BASS", 1.0, 0.5)
        return
    end

    player:send_message("§aSe detectaron " .. #players .. " jugadores.")
    player:sound("BLOCK_NOTE_BLOCK_BELL", 1.0, 1.0)
    
    for i, target in ipairs(players) do
        -- Marcar a los jugadores detectados (glowing o mensaje)
        player:send_message("§7- " .. target:name())
        target:apply_effect("GLOWING", 0, 100) -- 5 segundos
    end
end

-- ──────────── Habilidad 2: Orbes de Daño (Orbit) ────────────
function on_skill_2(player)
    if orbes_activados then
        player:send_message("§cLos orbes ya están activados.")
        return
    end
    
    orbes_activados = true
    player:sound("ENTITY_ILLUSIONER_PREPARE_BLINDNESS", 1.0, 1.0)
    player:send_message("§dOrbes de Vacío activados.")

    orbes_handle = player:orbit()
        :count(3)
        :virtual_item("OBSIDIAN", "COAL_BLOCK", "CRYING_OBSIDIAN")
        :radius(1.5)
        :rotation_speed(0.2)
        :wobble(0.3, 3.0)
        :duration(200) -- 10 segundos
        :show()
        
    -- Checkear daño cada segundo
    player:scheduler():run_timer(20, 20, 10, function()
        if not orbes_activados then return false end
        
        local targets = player:nearby_valid_targets(2.0)
        for i, t in ipairs(targets) do
            t:damage()
            t:sound("ENTITY_PLAYER_HURT", 1.0, 1.0)
        end
        return true
    end)
    
    player:scheduler():run_delayed(205, function()
        orbes_activados = false
        player:send_message("§cOrbes desactivados.")
    end)
end

-- ──────────── Habilidad 3: Atrape Oscuro (Line Spawn) ────────────
function on_skill_3(player)
    player:sound("ENTITY_EVOKER_CAST_SPELL", 1.0, 0.8)
    
    player:line_spawn()
        :count(12)
        :spacing(1.2)
        :delay(1.5)
        :angles(-15, 0, 15) -- Triple línea
        :on_hit(function(victim)
            victim:apply_effect("BLINDNESS", 0, 80)
            victim:apply_effect("SLOWNESS", 2, 80)
            player:sound("ENTITY_ZOMBIE_VILLAGER_CURE", 1.0, 2.0)
        end)
        :launch()
end

-- ──────────── Habilidad 4: Rayo del Vacío (RayTrace) ────────────
function on_skill_4(player)
    local target = player:ray_trace_player(20.0)
    
    if target then
        player:sound("ENTITY_ENDER_DRAGON_SHOOT", 1.0, 1.5)
        target:damage()
        target:apply_effect("WITHER", 1, 100)
        
        player:trail()
            :particle("SQUID_INK")
            :count(15)
            :offset(0.2, 0.2, 0.2)
            :duration(20)
            :show()
            
        -- Line of particles to target (simplified via trail on target or teleporting)
        -- Since we don't have a direct line particle yet, we just impact the target
        target:sound("ENTITY_GENERIC_EXPLODE", 0.5, 2.0)
    else
        player:sound("BLOCK_FIRE_EXTINGUISH", 1.0, 1.0)
    end
end

-- ──────────── on_trigger ────────────
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

-- ──────────── on_kill ────────────
function on_kill(player, victim)
    player:send_message("§0El vacío consume a " .. victim:name())
    player:sound("ENTITY_WITHER_AMBIENT", 1.0, 0.5)
    
    -- Curar al Null cuando mata
    player:apply_effect("REGENERATION", 1, 60)
end

return killer
