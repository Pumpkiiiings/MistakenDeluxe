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

    // Instancia lazy del contexto que inyectaremos al script
    private val scriptContext by lazy {
        object : ScriptContext {
            override fun scheduler(): ScriptScheduler = LuaScriptScheduler(this@LuaKillerAdapter)
            override fun log_info(message: String) = Bukkit.getLogger().info("[$id Lua] $message")
            override fun log_warning(message: String) = Bukkit.getLogger().warning("[$id Lua] $message")
            override fun log_error(message: String) = Bukkit.getLogger().severe("[$id Lua] $message")
        }
    }

    init {
        // Notificamos al script que ha sido cargado
        scriptKiller.on_load(scriptContext)
    }

    override fun getModelId(): String? = scriptKiller.model_id()

    override fun equip(player: Player) {
        super.equip(player) // Configura ECS, modelo y utilidades del Core
        
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

    override fun onDispose() {
        // Stop all effects registered by this script before notifying it
        liric.mistaken.scripting.effects.EffectRegistry.stopAll(id)
        scriptKiller.on_disable()
        // NOTA: Los schedulers de CoreKiller ya se cancelan en super.dispose()
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

