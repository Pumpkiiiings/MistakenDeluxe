package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.ScriptContext
import liric.mistaken.scripting.api.ScriptKiller
import liric.mistaken.scripting.api.ScriptScheduler
import liric.mistaken.scripting.scheduler.LuaScriptScheduler
import liric.mistaken.roles.killers.BaseKiller
import org.bukkit.entity.Player
import org.bukkit.Bukkit

/**
 * Adaptador que puentea la arquitectura interna de Mistaken (BaseKiller)
 * con el ScriptKiller de Lua.
 */
class LuaKillerAdapter(
    id: String,
    nombre: String,
    private val scriptKiller: ScriptKiller
) : BaseKiller(id, nombre) {

    
    private val scriptContext by lazy {
        object : ScriptContext {
            override fun scheduler(): ScriptScheduler = LuaScriptScheduler(this@LuaKillerAdapter)
            override fun log_info(message: String) = Bukkit.getLogger().info("[$id Lua] $message")
            override fun log_warning(message: String) = Bukkit.getLogger().warning("[$id Lua] $message")
            override fun log_error(message: String) = Bukkit.getLogger().severe("[$id Lua] $message")
        }
    }

    init {
        
        scriptKiller.on_load(scriptContext)
    }

    override fun getModelId(): String? = scriptKiller.model_id()

    override fun equip(player: Player) {
        super.equip(player) 
        
        val config = plugin.configManager.getKillerConfig(id)
        val hasTriggersSection = config.isConfigurationSection("triggers")
        val hasSkills = (1..4).any { config.contains("items.skill$it") }
        
        if (scriptKiller.has_trigger() && !hasTriggersSection && !hasSkills) {
            val msgEn = liric.mistaken.utils.color.ColorTranslator.translate("<red>Warning! This killer (Lua) defines on_trigger but has no configured triggers or skills. Abilities won't work. Please notify an admin.</red>")
            org.bukkit.Bukkit.getConsoleSender().sendMessage(msgEn)
        }
        val scriptPlayer = BukkitPlayerAdapter(player)
        scriptKiller.on_equip(scriptPlayer)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        if (player != null) {
            val scriptPlayer = BukkitPlayerAdapter(player)
            scriptKiller.on_unequip(scriptPlayer)
        }
    }

    override fun useSkill(player: Player, slot: Int) {
        val config = plugin.configManager.getKillerConfig(id)
        val cooldown = config.getInt("items.skill${slot}_cooldown", 0)
        
        if (triggerRegistry.checkCooldown(player, "skill_$slot", cooldown)) {
            return
        }

        val scriptPlayer = BukkitPlayerAdapter(player)
        scriptKiller.on_trigger(scriptPlayer, "skill_$slot")
    }

    override fun onDispose() {
        
        liric.mistaken.scripting.effects.EffectRegistry.stopAll(id)
        scriptKiller.on_disable()
        
    }

    /**
     * Puente para enviar eventos de Bukkit (ya adaptados) hacia el script Lua.
     */
    fun dispatchEvent(event: liric.mistaken.scripting.api.ScriptEvent) {
        scriptKiller.dispatch_event(event)
    }

    override fun onTrigger(player: Player, triggerId: String) {
        val scriptPlayer = BukkitPlayerAdapter(player)
        scriptKiller.on_trigger(scriptPlayer, triggerId)
    }

    override fun onInterceptChat(player: Player, message: String): String? {
        val scriptPlayer = BukkitPlayerAdapter(player)
        return scriptKiller.on_intercept_chat(scriptPlayer, message)
    }

    override fun onKill(killer: Player, victim: Player) {
        val scriptKillerPlayer = BukkitPlayerAdapter(killer)
        val scriptVictimPlayer = BukkitPlayerAdapter(victim)
        scriptKiller.on_kill(scriptKillerPlayer, scriptVictimPlayer)
    }
}
