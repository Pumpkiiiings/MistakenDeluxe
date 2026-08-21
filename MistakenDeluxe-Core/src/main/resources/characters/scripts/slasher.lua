-- ──────────── Slasher.lua ────────────
-- Port de la clase Slasher de Kotlin a Lua
-- Usando LuaEffectBindings

local attack_queue = {1, 2, 3, 4}

local function get_next_attack_sound()
    if #attack_queue == 0 then
        attack_queue = {1, 2, 3, 4}
    end
    local idx = math.random(1, #attack_queue)
    local val = attack_queue[idx]
    table.remove(attack_queue, idx)
    return val
end

-- ──────────── LIFECYCLE ────────────
function on_equip(player)
    -- Rastro de sangre pasivo (showTrail)
    trail(player)
        :particle("DUST", {r=1.0, g=0.0, b=0.0}, 0.8)
        :offset(0.1, 0.2, 0.1)
        :view_radius(25)
        :only_when_moving(true)
        :show()
end

function on_unequip(player)
    -- Engine maneja el cleanup de trail() automáticamente
end

-- ──────────── SKILL 1: Sed de Sangre ────────────
local function on_skill_1(player)
    apply_effect(player, "SPEED", 2, 160)
    apply_effect(player, "INCREASE_DAMAGE", 1, 160)
    
    draw_star(player, "#FF0000", 1.5, 5)
    
    sound(player, "ENTITY_WOLF_GROWL", 1.5, 0.5)
    screen_tint(player):color(255, 0, 0):alpha(0.3):duration(160):show()
    
    sequence(player, player:location())
        :delay(160, function()
            apply_effect(player, "SLOW", 1, 100)
            apply_effect(player, "WEAKNESS", 1, 100)
        end)
        :play()
end

-- ──────────── SKILL 2: Machete Lanzable ────────────
local function on_skill_2(player)
    projectile(player)
        :item("IRON_SWORD")
        :speed(1.4)
        :hit_radius(1.2)
        :trail_particle("REDSTONE", 3)
        :max_ticks(30)
        :on_hit(function(victim)
            damage(victim)
            sound(victim, "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1.0, 0.8)
            screen_tint(victim):color(255, 0, 0):alpha(0.6):duration(20):show()
            screen_shake(victim):intensity(1.5):duration(15):show()
        end)
        :start()
end

-- ──────────── SKILL 3: Presencia ────────────
local function on_skill_3(player)
    sound(player, "ENTITY_WARDEN_HEARTBEAT", 1.5, 0.8)
    
    particle_burst(player)
        :type("SCULK_SOUL")
        :count(50)
        :offset(3.0, 1.0, 3.0)
        :speed(0.05)
        :spawn()

    visual_hitbox(player, 8.0, 8.0, 8.0, 20, "PURPLE_STAINED_GLASS")

    local nearby = nearby_players(player, 8.0)
    for _, victim in ipairs(nearby) do
        apply_effect(victim, "BLINDNESS", 0, 100)
        apply_effect(victim, "HUNGER", 1, 100)
        screen_tint(victim):color(0, 0, 0):alpha(0.7):duration(15):show()
    end
end

-- ──────────── SKILL 4: Ejecución ────────────
local function on_skill_4(player)
    apply_effect(player, "DAMAGE_RESISTANCE", 3, 300)
    apply_effect(player, "INCREASE_DAMAGE", 2, 300)
    
    draw_star(player, "#800000", 2.5, 5)
    
    particle_burst(player)
        :type("ASH")
        :count(300)
        :offset(3.0, 3.0, 3.0)
        :speed(0.05)
        :spawn()

    particle_burst(player)
        :type("FALLING_LAVA")
        :count(50)
        :offset(3.0, 3.0, 3.0)
        :speed(0.05)
        :spawn()

    screen_shake(player):intensity(0.8):duration(300):show()
    
    sequence(player, player:location())
        :delay(300, function()
            apply_effect(player, "SLOW", 2, 80)
            apply_effect(player, "WEAKNESS", 2, 80)
        end)
        :play()
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
    elseif trigger_id == "ATTACK" then
        local idx = get_next_attack_sound()
        local sound_name = "mistaken:whitepumpkin_ataque_" .. tostring(idx)
        sound(player, sound_name, 3.0, 1.0)
    end
end

return killer
