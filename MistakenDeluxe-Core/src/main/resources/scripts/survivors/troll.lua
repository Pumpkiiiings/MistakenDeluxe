local survivor = {
    id = "troll"
}

function on_load(context)
end

function on_equip(player)
end

function on_unequip(player)
end

function on_disable()
end

function on_skill_1(player)
    -- Hacer invisible al jugador original y ocultar su armadura
    apply_effect(player, "INVISIBILITY", 0, 140)
    hide_equipment(player, 140)
    
    -- Invocar clon inteligente
    local clone = fake_player(player)
        :copy_appearance(player)
        :copy_equipment(player)
        :duration(600)
        :enable_ai(true)
        :flee_from_enemies(100.0, 2.5, function(loc)
            -- Muerte por captura
            sound(loc, "ENTITY_GENERIC_EXPLODE", 0.8, 1.2)
            particle_burst(loc):type("END_ROD"):count(50):offset(0.5, 0.8, 0.5):spread(0.1):show()
        end)
        :on_expire(function(loc)
            -- Despawn por tiempo
            particle_burst(loc):type("CLOUD"):count(10):offset(0.3, 0.5, 0.3):spread(0.05):show()
        end)
        :spawn()
end

function on_skill_2(player)
    local loc = location(player)
    proximity_trap(player, loc)
        :model("YELLOW_DYE")
        :radius(1.0)
        :duration(300)
        :on_trigger(function(victim)
            sound(victim, "ENTITY_SLIME_SQUISH", 1.0, 0.5)
            apply_effect(victim, "SLOWNESS", 4, 60)
            apply_effect(victim, "BLINDNESS", 0, 40)
            
            launch_entity(victim, 0.0, 0.6, 0.0)
            rotate_entity(victim, yaw(victim) + 180.0, -45.0)
            
            send_translated(victim, "supervivientes.troll.habilidades.resbalaste_platano")
            particle_burst(victim):type("DUST"):color(1.0, 1.0, 0.0):count(10):spread(0.2):show()
        end)
        :register()
end

function on_skill_3(player)
    local loc = location(player)
    proximity_trap(player, loc)
        :model("CHEST")
        :radius(2.0)
        :duration(400)
        :on_trigger(function(victim)
            local vloc = location(victim)
            sound(vloc, "ENTITY_GENERIC_EXPLODE", 1.0, 1.0)
            sound(vloc, "ENTITY_WITCH_CELEBRATE", 1.0, 1.0)
            particle_burst(vloc):type("EXPLOSION_EMITTER"):count(1):show()
            
            apply_effect(victim, "BLINDNESS", 0, 80)
            apply_effect(victim, "NAUSEA", 1, 140)
            
            send_translated(victim, "supervivientes.troll.habilidades.boom_trampa")
        end)
        :register()
end

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
