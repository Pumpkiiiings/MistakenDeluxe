local killer = {}

function killer.model_id()
    return "slasher"
end

function killer.on_load(context)
    context:log_info("Slasher Lua script loaded successfully!")
    -- context:scheduler():run_timer(20, 20, "slasher_tick")
end

function killer.on_equip(player)
    player:send_message("<red>You are now the Slasher! (Lua Engine)")
    player:set_health(40.0)
    player:play_sound("entity.wither.spawn", 1.0, 0.5)
end

function killer.on_unequip(player)
    player:send_message("<gray>You are no longer the Slasher.")
    player:set_health(20.0)
end

function killer.on_tick()
    -- Called every tick for the killer instance
end

function killer.on_entity_damage_by_entity(event)
    local victim = event:victim()
    local attacker = event:attacker()
    
    if attacker ~= nil and attacker:is_valid() then
        -- Incrementar daño un 20%
        local new_damage = event:original_damage() * 1.2
        event:set_damage(new_damage)
        
        -- Partículas y sonido
        victim:location():add(0.0, 1.0, 0.0)
        attacker:play_sound("entity.player.attack.crit", 1.0, 1.0)
    end
end

function killer.on_disable()
    -- Cleanup if needed
end

return killer
