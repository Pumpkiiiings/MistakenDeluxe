package liric.mistaken.roles.killers.triggers

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TriggerRegistry(val killerId: String) {

    private val triggers = mutableMapOf<InputTrigger, MutableList<TriggerDefinition>>()
    // Map<UUID, Map<TriggerId, LastUsedMillis>>
    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()

    fun loadFromConfig(config: FileConfiguration) {
        triggers.clear()
        
        val triggersSection = config.getConfigurationSection("triggers") ?: return
        
        for (triggerId in triggersSection.getKeys(false)) {
            val inputStr = triggersSection.getString("$triggerId.input") ?: continue
            val input = InputTrigger.fromString(inputStr) ?: continue
            val cooldown = triggersSection.getInt("$triggerId.cooldown", 0)
            
            val def = TriggerDefinition(triggerId, input, cooldown)
            triggers.computeIfAbsent(input) { mutableListOf() }.add(def)
        }
    }

    fun getTriggersForInput(input: InputTrigger): List<TriggerDefinition> {
        return triggers[input] ?: emptyList()
    }

    /**
     * Revisa si el trigger est� en cooldown. 
     * Retorna true si est� en cooldown, false si se puede usar (y autom�ticamente lo pone en cooldown si no lo estaba).
     */
    fun checkCooldown(player: Player, triggerId: String, cooldownSeconds: Int): Boolean {
        if (cooldownSeconds <= 0) return false
        
        val playerCooldowns = cooldowns.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
        val now = System.currentTimeMillis()
        val lastUsed = playerCooldowns[triggerId] ?: 0L
        
        if (now - lastUsed < cooldownSeconds * 1000L) {
            val remaining = (cooldownSeconds * 1000L - (now - lastUsed)) / 1000
            player.sendActionBar(liric.mistaken.utils.color.ColorTranslator.translate("<red>Habilidad en enfriamiento. <gray>($remaining s)"))
            return true
        }
        
        playerCooldowns[triggerId] = now
        return false
    }

    fun clearCooldowns(playerUuid: UUID) {
        cooldowns.remove(playerUuid)
    }
}
