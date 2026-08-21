package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.ScriptContext
import liric.mistaken.scripting.api.ScriptRole
import liric.mistaken.scripting.api.ScriptScheduler
import liric.mistaken.scripting.scheduler.LuaScriptScheduler
import liric.mistaken.roles.survivors.Survivor
import org.bukkit.entity.Player
import org.bukkit.Bukkit

/**
 * Adaptador que puentea la arquitectura interna de Mistaken (Survivor)
 * con el ScriptRole de Lua.
 */
class LuaSurvivorAdapter(
    id: String,
    nombre: String,
    private val scriptRole: ScriptRole
) : Survivor(id, nombre) {

    private val scriptContext by lazy {
        object : ScriptContext {
            override fun scheduler(): ScriptScheduler = LuaScriptScheduler()
            override fun log_info(message: String) = Bukkit.getLogger().info("[$id Lua] $message")
            override fun log_warning(message: String) = Bukkit.getLogger().warning("[$id Lua] $message")
            override fun log_error(message: String) = Bukkit.getLogger().severe("[$id Lua] $message")
        }
    }

    init {
        scriptRole.on_load(scriptContext)
    }

    override fun equip(player: Player) {
        val config = plugin.configManager.getSurvivorConfig(id)
        triggerRegistry.loadFromConfig(config)

        val scriptPlayer = BukkitPlayerAdapter(player)
        scriptRole.on_equip(scriptPlayer)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        if (player != null) {
            val scriptPlayer = BukkitPlayerAdapter(player)
            scriptRole.on_unequip(scriptPlayer)
        }
    }

    override fun useSkill(player: Player, slot: Int) {
        val config = plugin.configManager.getSurvivorConfig(id)
        val cooldown = config.getInt("items.skill${slot + 1}_cooldown", 0)
        
        if (checkCooldown(player, slot, cooldown)) {
            return
        }

        val scriptPlayer = BukkitPlayerAdapter(player)
        scriptRole.on_trigger(scriptPlayer, "skill_$slot")
    }

    override fun onTrigger(player: Player, triggerId: String) {
        val scriptPlayer = BukkitPlayerAdapter(player)
        scriptRole.on_trigger(scriptPlayer, triggerId)
    }

    fun dispatchEvent(event: liric.mistaken.scripting.api.ScriptEvent) {
        scriptRole.dispatch_event(event)
    }
}
