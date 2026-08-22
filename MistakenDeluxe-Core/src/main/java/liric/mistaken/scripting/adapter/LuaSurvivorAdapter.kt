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
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val config = plugin.configManager.getSurvivorConfig(id)
        triggerRegistry.loadFromConfig(config)

        val langInfo = liric.mistaken.config.engine.core.MessageService.getSpecificFile(player, "survivors_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val itemId = config.getString("armor.$key") ?: config.getString("items.$key")
            if (itemId == null || itemId == "none" || itemId.isEmpty()) return

            val item = liric.mistaken.utils.hooks.CraftEngine.getCustomItem(itemId) ?: run {
                val mat = org.bukkit.Material.matchMaterial(itemId.replace(".*:".toRegex(), "").uppercase())
                if (mat != null) org.bukkit.inventory.ItemStack(mat) else null
            } ?: return

            val namePath = "survivors.${this.id}.skill_names.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(liric.mistaken.utils.color.ColorTranslator.translate(it)) }
            }

            if (isArmor) {
                when(key) {
                    "helmet" -> inv.helmet = item
                    "chestplate" -> inv.chestplate = item
                    "leggings" -> inv.leggings = item
                    "boots" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        deliver("helmet", 0, true)
        deliver("chestplate", 0, true)
        deliver("leggings", 0, true)
        deliver("boots", 0, true)
        
        deliver("skill1", 0)
        deliver("skill2", 1)
        deliver("skill3", 2)
        deliver("skill4", 3)

        player.updateInventory()

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

    fun onMeleeAttack(attacker: Player, victim: Player, slot: Int) {
        val scriptAttacker = BukkitPlayerAdapter(attacker)
        val scriptVictim = BukkitPlayerAdapter(victim)
        scriptRole.on_melee_attack(scriptAttacker, scriptVictim, slot)
    }

    fun dispatchEvent(event: liric.mistaken.scripting.api.ScriptEvent) {
        scriptRole.dispatch_event(event)
    }
}
