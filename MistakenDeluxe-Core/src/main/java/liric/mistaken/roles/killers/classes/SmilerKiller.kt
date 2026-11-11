package liric.mistaken.roles.killers.classes

import liric.mistaken.models.components.CombatComponent
import liric.mistaken.models.core.Character
import liric.mistaken.models.states.CharacterState
import liric.mistaken.roles.killers.BaseKiller
import liric.mistaken.roles.shared.RoleItemUtils
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

object SmilerAttackState : CharacterState {
    override val id = "attack"
    override val priority = 50
}

class SmilerKiller : BaseKiller("smiler", "Smiler") {
    
    override fun getModelId(): String = "smiler"

    override fun equip(player: Player) {
        super.equip(player)

        val configMecanica = plugin.configManager.getKillerConfig(this.id)
        RoleItemUtils.resolveItem(configMecanica.getString("items.weapon"))?.let { item ->
            item.editMeta { meta -> meta.displayName(Component.text("§cArma Smiler")) }
            player.inventory.setItem(8, item)
        }
    }

    override fun setupAdditionalComponents(character: Character) {
        character.addComponent(CombatComponent::class.java, object : CombatComponent {
            override fun onEnable(character: Character) {}
            override fun onDisable() {}
            
            override fun performAttack(attackId: String) {
                if (character.entity is Player) {
                    val player = character.entity
                    transitionTo(player, SmilerAttackState, force = true)
                    
                    player.scheduler.runDelayed(plugin, java.util.function.Consumer {
                        if (player.isOnline && getCharacter(player)?.getComponent(liric.mistaken.models.components.StateComponent::class.java)?.currentState == SmilerAttackState) {
                            transitionTo(player, liric.mistaken.models.states.IdleState, force = true)
                        }
                    }, null, 15L) 
                }
            }

            override fun takeDamage(amount: Double, source: Any?): Boolean {
                return true
            }
        })
    }

    override fun useSkill(player: Player, slot: Int) {
        
    }
}
