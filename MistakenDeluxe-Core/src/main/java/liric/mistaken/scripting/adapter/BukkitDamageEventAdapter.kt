package liric.mistaken.scripting.adapter

import liric.mistaken.scripting.api.ScriptDamageEvent
import liric.mistaken.scripting.api.ScriptEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent

class BukkitDamageEventAdapter(
    private val event: EntityDamageByEntityEvent,
    private val wrappedVictim: ScriptEntity,
    private val wrappedAttacker: ScriptEntity?
) : ScriptDamageEvent {

    override fun event_name(): String = "entity_damage_by_entity"

    override fun victim(): ScriptEntity = wrappedVictim

    override fun attacker(): ScriptEntity? = wrappedAttacker

    override fun original_damage(): Double = event.damage

    override fun damage(): Double = event.damage

    override fun set_damage(amount: Double) {
        event.damage = amount
    }

    override fun cancel() {
        event.isCancelled = true
    }

    override fun is_cancelled(): Boolean = event.isCancelled
}

