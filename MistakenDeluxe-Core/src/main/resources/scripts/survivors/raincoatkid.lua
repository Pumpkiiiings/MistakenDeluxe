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

        -- amp=1 (SPEED II), ticks=100
        apply_effect(player, "SPEED", 1, 100)
        particle_burst(player, "CLOUD", 5, 0.2, 0.1, 0.2, 0.05)

        sequence(player, player:location())
            :delay(100, function()
                if player:is_online() then
                    -- amp=0 (SLOWNESS I), ticks=60
                    apply_effect(player, "SLOWNESS", 0, 60)
                    player:play_sound("ENTITY_PLAYER_BREATH", 1.0, 0.8)
                    send_action_bar_translated(player, "survivors.raincoatkid.habilidades.jadeo", "survivors_info")
                end
            end)
            :play()
    end

    -- Habilidad 2: Dash (Slot 1)
    if trigger_id == "skill_1" then
        player:play_sound("ENTITY_BAT_TAKEOFF", 1.0, 1.0)

        dash(player)
            :speed(1.8)
            :max_ticks(10)
            :start()

        player:play_sound("ITEM_TRIDENT_RIPTIDE_1", 1.0, 1.2)

        -- Leve slowness post-dash: amp=0 (SLOWNESS I), ticks=60
        sequence(player, player:location())
            :delay(10, function()
                if player:is_online() then
                    apply_effect(player, "SLOWNESS", 0, 60)
                end
            end)
            :play()
    end
end

-- ------------ on_melee_attack ------------
function on_melee_attack(attacker, victim, slot)
    -- Habilidad 3: Palo (Slot 2)
    if slot == 2 then
        -- amp=2 (SLOWNESS III), ticks=100; amp=0 (BLINDNESS I), ticks=100
        apply_effect(victim, "SLOWNESS", 2, 100)
        apply_effect(victim, "BLINDNESS", 0, 100)
        victim:play_sound("ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR", 1.0, 0.5)
        particle_burst(victim, "CRIT", 10, 0.5, 0.5, 0.5, 0.1)

        attacker:send_message("<green><bold>BAM!</bold> <gray>Killer aturdido.")
        attacker:play_sound("ENTITY_FIREWORK_ROCKET_BLAST", 0.5, 1.2)
    end
end

return survivor