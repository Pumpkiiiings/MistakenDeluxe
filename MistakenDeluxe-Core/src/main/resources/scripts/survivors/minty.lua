-- ════════════════════════════════════════════════════════
-- Minty Script (LUA)
-- ════════════════════════════════════════════════════════

local survivor = {
    id = "minty"
}

-- ──────────── on_equip / on_unequip ────────────
function on_equip(player)
    -- No special setup needed
end

function on_unequip(player)
    -- Cleanup is handled automatically by effect bindings
end

-- ──────────── on_trigger (Skills) ────────────
function on_trigger(player, trigger_id)

    -- Habilidad 1: Sarpazo (Slot 0)
    if trigger_id == "skill_0" then
        player:play_sound("ENTITY_PLAYER_ATTACK_SWEEP", 1.0, 1.5)
        
        -- Raytrace para buscar a un Killer
        local target = ray_trace_players(player, 4.0)
        
        -- Generar partículas de Sweep Attack
        particle_burst(player, "SWEEP_ATTACK", 1, 0.0, 1.2, 1.5, 0.0)
        
        if target ~= nil and target:is_killer() then
            apply_effect(target, "BLINDNESS", 60, 0)
            apply_effect(target, "SLOWNESS", 100, 2)
            player:play_sound("ENTITY_WOLF_GROWL", 1.0, 0.8)
            target:play_sound("ENTITY_PLAYER_ATTACK_CRIT", 1.0, 0.5)
        end
    end
    
    -- Habilidad 2: Embestida (Slot 1)
    if trigger_id == "skill_1" then
        apply_effect(player, "SPEED", 100, 1)
        dash(player, 1.3, 0.3)
        
        -- Trail de nubes por 100 ticks (5 seg) a cada 5 ticks
        trail(player)
            :particle("CLOUD")
            :duration(100)
            :rate(5)
            :start()
    end
    
    -- Habilidad 3: Aullido Feroz (Slot 2)
    if trigger_id == "skill_2" then
        particle_burst(player, "SONIC_BOOM", 1, 0.0, 1.5, 0.0, 0.0)
        
        local targets = get_nearby_players(player, 8.0, 8.0, 8.0)
        for i, target in ipairs(targets) do
            if target:is_killer() then
                apply_effect(target, "BLINDNESS", 60, 0)
                apply_effect(target, "SLOWNESS", 60, 1)
                apply_effect(target, "DARKNESS", 40, 0)
                
                launch_entity(target, 0.0, 0.4, 0.0)
                target:damage(0.0)
            end
        end
    end
end

return survivor