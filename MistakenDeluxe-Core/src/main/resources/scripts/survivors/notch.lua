-- --------------------------------------------------------
-- Notch Script (LUA)
-- --------------------------------------------------------

local survivor = {
    id = "notch"
}

function on_equip(player)
end

function on_unequip(player)
end

function on_trigger(player, trigger_id)

    -- Habilidad 1: Salto Creativo (Slot 0)
    if trigger_id == "skill_0" then
        dash(player, 1.2, 1.1)
        
        player:play_sound("ENTITY_FIREWORK_ROCKET_LAUNCH", 1.5, 0.8)
        player:play_sound("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0, 0.5)
        
        particle_burst(player, "CLOUD", 15, 0.3, 0.1, 0.3, 0.05)
        particle_burst(player, "WAX_OFF", 10, 0.5, 0.5, 0.5, 0.0)
        
        sequence(player, player)
            :delay(15)
            :on_execute(function(p, loc)
                if p:is_online() then
                    apply_effect(p, "SLOW_FALLING", 60, 0)
                end
            end)
            :play()
    end
    
    -- Habilidad 2: Muro Admin (Slot 1)
    if trigger_id == "skill_1" then
        player:play_sound("BLOCK_ANVIL_LAND", 0.8, 0.5)
        player:play_sound("BLOCK_PISTON_EXTEND", 1.0, 0.5)
        
        -- Partículas de BEDROCK
        -- Usamos el nuevo sistema de materials
        local p_table = particle_burst(player)
        p_table:type("BLOCK")
        p_table:count(40)
        p_table:offset(3.0, 0.5, 3.0)
        p_table:material("BEDROCK")
        p_table:show()
        
        particle_burst(player, "ENCHANT", 30, 2.0, 2.0, 2.0, 1.0)
        
        local targets = get_nearby_players(player, 6.0, 6.0, 6.0)
        for i, target in ipairs(targets) do
            if target:is_killer() then
                push_from_location(target, player, 2.5, 0.4)
                send_translated(target, "survivors.notch.habilidades.denied")
                target:play_sound("ENTITY_VILLAGER_NO", 1.0, 0.8)
            end
        end
    end
    
    -- Habilidad 3: Manzana del Creador (Slot 2)
    if trigger_id == "skill_2" then
        player:play_sound("ITEM_TOTEM_USE", 1.0, 1.2)
        player:play_sound("ENTITY_GENERIC_EAT", 1.0, 1.0)
        
        particle_burst(player, "TOTEM_OF_UNDYING", 40, 0.5, 0.5, 0.5, 0.3)
        particle_burst(player, "FIREWORK", 1, 0.0, 1.0, 0.0, 0.0)
        
        apply_effect(player, "REGENERATION", 100, 2)
        apply_effect(player, "RESISTANCE", 100, 1)
        apply_effect(player, "SPEED", 100, 1)
        
        local current_health = player:health()
        local max_health = player:max_health()
        local new_health = current_health + 6.0
        if new_health > max_health then
            new_health = max_health
        end
        player:set_health(new_health)
    end
end

return survivor