local survivor = {
    id = "civil"
}

-- Lifecycle
function on_load(context)
    context:log_info("Survivor Civilian cargado exitosamente.")
end

function on_equip(player)
end

function on_unequip(player)
    player_state_clear(player, "invisibility_active")
end

function on_disable()
end

-- Abilities
function on_skill_1(player)
    -- Adrenalina
    apply_effect(player, "SPEED", 1, 100)
    sequence(player, player):delay(100):execute(function()
        apply_effect(player, "SLOWNESS", 0, 60)
        sound(player, "ENTITY_HORSE_BREATHE", 0.8, 0.6)
    end):start()
    sound(player, "ENTITY_PLAYER_BREATH", 1.0, 1.0)
    send_translated(player, "supervivientes.civil.habilidades_mensajes.skill1")
end

function on_skill_2(player)
    -- Invisibilidad
    apply_effect(player, "INVISIBILITY", 0, 100)
    sequence(player, player):delay(100):execute(function()
        send_translated(player, "supervivientes.civil.habilidades_mensajes.skill2_fin")
        sound(player, "BLOCK_BEACON_DEACTIVATE", 0.5, 1.5)
    end):start()
    sound(player, "ENTITY_ILLUSIONER_MIRROR_MOVE", 1.0, 1.0)
    send_translated(player, "supervivientes.civil.habilidades_mensajes.skill2")
end

function on_skill_3(player)
    -- Roca
    projectile(player):item("SNOWBALL"):speed(1.5):on_impact_sound("ENTITY_SNOWBALL_THROW"):on_hit(function(victim)
        damage(victim, 4.5)
    end):launch()
    sound(player, "ENTITY_SNOWBALL_THROW", 1.0, 0.5)
    send_translated(player, "supervivientes.civil.habilidades_mensajes.skill3")
end

-- Trigger
function on_trigger(player, skill_id)
    if skill_id == "skill_0" then
        on_skill_1(player)
    elseif skill_id == "skill_1" then
        on_skill_2(player)
    elseif skill_id == "skill_2" then
        on_skill_3(player)
    end
end

return survivor
