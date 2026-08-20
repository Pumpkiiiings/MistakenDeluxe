-- ════════════════════════════════════════════════════════
-- NullAsesino Script (LUA)
-- ════════════════════════════════════════════════════════

local killer = {
    id = "null",
    model = "mistaken:null_model"
}

-- ──────────── on_equip / on_unequip ────────────
function on_equip(player)
    player:set_scale(1.1)
end

function on_unequip(player)
    player:reset_scale()
end

-- ──────────── Habilidad 1: Error Render ────────────
function on_skill_1(player)
    player:sound("BLOCK_GLASS_BREAK", 1.0, 0.5)
    
    -- Partículas FIREWORK (como no hay builder específico de firework expuesto, 
    -- y particle(x) genérico desde LuaEffectBindings podría no estar, 
    -- lo simulamos si no existe, pero en este caso el original usa world.spawnParticle)
    -- Por simplicidad dejaremos que apply_effect marque el impacto, o se puede exponer shape("firework").
    -- Asumimos que un trail corto o algo podría servir si no, pero la lógica central es el radio.
    
    local targets = player:nearby_valid_targets(12.0)
    for i, target in ipairs(targets) do
        target:apply_effect("DARKNESS", 0, 200)
        target:apply_effect("BLINDNESS", 0, 200)
        -- Usamos el component en Lua sería enviar la traducción, 
        -- o enviar el key directo si el cliente lo procesa. 
        -- Enviaremos el ID que el plugin intercepta o mensaje local
        target:send_message("roles.killer.abilities.null_asesino.sistema_corrupto")
    end
end

-- ──────────── Habilidad 2: Generador Bait ────────────
function on_skill_2(player)
    local loc = player:location()
    
    bait_trap(player, loc)
        :marker_item("BEACON")
        :orbit_particle("END_ROD")
        :trigger_radius(3.5)
        :max_ticks(400)
        :on_trigger(function(victim)
            victim:damage()
            victim:sound("ENTITY_ENDERMAN_SCREAM", 1.0, 0.1)
        end)
        :spawn()
end

-- ──────────── Habilidad 3: Prisión del Vacío ────────────
function on_skill_3(player)
    local target = player:ray_trace_player(15.0)
    
    if target then
        target:apply_effect("SLOWNESS", 10, 100)
        target:sound("BLOCK_CHAIN_PLACE", 1.0, 0.5)
    end
end

-- ──────────── Habilidad 4: Colmillos del Vacío ────────────
function on_skill_4(player)
    player:line_spawn()
        :count(15)
        :spacing(1.0)
        :delay_ticks(1)
        :snap_to_ground(true)
        :on_hit(function(victim)
            victim:damage()
            victim:apply_effect("DARKNESS", 0, 40)
        end)
        :start()
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
    player:sound("ENTITY_WITHER_AMBIENT", 1.0, 0.5)
    player:apply_effect("REGENERATION", 1, 60)
end

-- ──────────── on_finisher ────────────
function on_finisher(player, victim)
    local loc = victim:location()
    local choice = math.random(0, 2)
    
    if choice == 0 then
        -- Efecto 1: Drenaje de Alma
        loc:sound("ENTITY_ENDERMAN_STARE", 1.5, 0.1)
        
        spiral_particle(player, loc)
            :particle_1("SQUID_INK")
            :particle_2("SCULK_SOUL")
            :duration(40)
            :on_finish(function(final_loc)
                final_loc:sound("ENTITY_WARDEN_SONIC_BOOM", 1.0, 1.5)
                -- Simular SONIC BOOM si es necesario
            end)
            :start()
            
    elseif choice == 1 then
        -- Efecto 2: Prisión de Obsidiana Llorosa
        loc:sound("BLOCK_RESPAWN_ANCHOR_SET_SPAWN", 1.5, 0.5)
        
        sinking_block(player, loc)
            :material("CRYING_OBSIDIAN")
            :sink_ticks(20)
            :duration(30)
            :on_remove(function(final_loc)
                final_loc:sound("BLOCK_GLASS_BREAK", 2.0, 0.1)
            end)
            :show()
            
    elseif choice == 2 then
        -- Efecto 3: Mirada del Vacío
        loc:sound("AMBIENT_CAVE", 2.0, 0.5)
        
        formation(player, loc)
            :shape("triangle")
            :count(3)
            :material("BEACON")
            :radius(2.0)
            :duration(30)
            :on_expire(function(final_loc)
                final_loc:sound("ENTITY_WITHER_DEATH", 1.0, 1.0)
            end)
            :show()
    end
end

return killer
