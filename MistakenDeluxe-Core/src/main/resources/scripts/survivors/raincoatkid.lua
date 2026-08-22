-- --------------------------------------------------------
-- RaincoatKid Script (LUA)
-- --------------------------------------------------------

local survivor = {
    id = "raincoatkid"
}

-- ------------ on_equip / on_unequip ------------
function on_equip(player)
    player:set_scale(0.8)
end

function on_unequip(player)
    player:reset_scale()
end

-- ------------ on_trigger (Skills) ------------
function on_trigger(player, trigger_id)

    -- Habilidad 1: Sprint (Slot 0)
    if trigger_id == "skill_0" then
        player:play_sound("ENTITY_BAT_TAKEOFF", 1.0, 1.0)
        
        apply_effect(player, "SPEED", 100, 2)
        particle_burst(player, "CLOUD", 5, 0.2, 0.1, 0.2, 0.05)
        
        sequence(player, location(player))
            :delay(100, function()
                if player:is_online() then
                    apply_effect(player, "SLOWNESS", 60, 0)
                    player:play_sound("ENTITY_PLAYER_BREATH", 1.0, 0.8)
                    send_action_bar_translated(player, "survivors.raincoatkid.habilidades.jadeo", "survivors_info")
                end
            end)
            :play()
    end
    
    -- Habilidad 2: Dash (Slot 1)
    if trigger_id == "skill_1" then
        player:play_sound("ENTITY_BAT_TAKEOFF", 1.0, 1.0)
        
        dash(player, 1.8, 0.4)
        player:play_sound("ITEM_TRIDENT_RIPTIDE_1", 1.0, 1.2)
        apply_effect(player, "SLOWNESS", 60, 0)
    end
end

-- ------------ on_melee_attack ------------
function on_melee_attack(attacker, victim, slot)
    -- Habilidad 3: Palo (Slot 2)
    if slot == 2 then
        apply_effect(victim, "SLOWNESS", 100, 2)
        apply_effect(victim, "BLINDNESS", 100, 0)
        victim:play_sound("ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR", 1.0, 0.5)
        particle_burst(victim, "CRIT", 10, 0.5, 0.5, 0.5, 0.1)
        
        attacker:send_message("<green><bold>BAM!</bold> <gray>Killer aturdido.")
        attacker:play_sound("ENTITY_FIREWORK_ROCKET_BLAST", 0.5, 1.2)
    end
end

return survivor