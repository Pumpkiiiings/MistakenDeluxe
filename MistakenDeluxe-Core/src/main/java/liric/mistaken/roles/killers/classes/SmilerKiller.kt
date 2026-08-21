package liric.mistaken.roles.killers.classes

import liric.mistaken.models.components.CombatComponent
import liric.mistaken.models.core.Character
import liric.mistaken.models.states.CharacterState
import liric.mistaken.roles.killers.BaseKiller
import org.bukkit.entity.Player

object SmilerAttackState : CharacterState {
    override val id = "attack"
    override val priority = 50
}

class SmilerKiller : BaseKiller("smiler", "Smiler") {
    
    override fun getModelId(): String = "smiler"

    override fun equip(player: Player) {
        super.equip(player)
        
        val inv = player.inventory
        val configMecanica = liric.mistaken.Mistaken.instance.configManager.getKillerConfig(this.id)

        val id = configMecanica.getString("items.weapon")
        if (id != null && id != "none") {
            val matName = id.replace(".*:".toRegex(), "").uppercase()
            val mat = org.bukkit.Material.matchMaterial(matName)
            if (mat != null) {
                val item = org.bukkit.inventory.ItemStack(mat)
                item.editMeta { meta -> meta.displayName(net.kyori.adventure.text.Component.text("§cArma Smiler")) }
                inv.setItem(8, item)
            }
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
                    
                    org.bukkit.Bukkit.getScheduler().runTaskLater(liric.mistaken.Mistaken.instance, Runnable {
                        if (player.isOnline && getCharacter(player)?.getComponent(liric.mistaken.models.components.StateComponent::class.java)?.currentState == SmilerAttackState) {
                            transitionTo(player, liric.mistaken.models.states.IdleState, force = true)
                        }
                    }, 15L) 
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
