package liric.mistaken.listeners.killers

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import liric.mistaken.utils.color.ColorTranslator


class KillerSkillListener(private val plugin: Mistaken) : Listener {

    private val mm = plugin.mm
    private val plain = PlainTextComponentSerializer.plainText()

    private val lastAttackMap = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long>()

    /**
     * Trigger: Ataque básico (Click Izquierdo).
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onBasicAttack(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.isLeftClick) return

        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return
        if (session.currentState != GameState.INGAME) return
        if (!session.isKiller(player.uniqueId)) return

        
        val now = System.currentTimeMillis()
        val lastHit = lastAttackMap.getOrDefault(player.uniqueId, 0L)
        if (now - lastHit < 500L) { 
            return
        }
        lastAttackMap[player.uniqueId] = now

        val killer = plugin.killerManager.getKillerOfPlayer(player) ?: return
        if (killer is liric.mistaken.roles.killers.BaseKiller) {
            val character = killer.getCharacter(player) ?: return
            
            val combatComp = character.getComponent(liric.mistaken.characters.components.CombatComponent::class.java)
            if (combatComp != null) {
                combatComp.performAttack("basic")
            } else {
                character.getComponent(liric.mistaken.characters.components.StateComponent::class.java)
                    ?.transitionTo(liric.mistaken.characters.states.AttackState, force = true)
            }
        }
    }

    /**
     * Trigger: Ataque a entidad (Daño cuerpo a cuerpo).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityAttack(event: org.bukkit.event.entity.EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val session = plugin.sessionManager.getSession(player) ?: return
        if (session.currentState != GameState.INGAME) return
        if (!session.isKiller(player.uniqueId)) return

        val killer = plugin.killerManager.getKillerOfPlayer(player) ?: return
        if (killer is liric.mistaken.roles.killers.BaseKiller) {
            val character = killer.getCharacter(player) ?: return
            
            val combatComp = character.getComponent(liric.mistaken.characters.components.CombatComponent::class.java)
            if (combatComp != null) {
                combatComp.performAttack("entity")
            } else {
                character.getComponent(liric.mistaken.characters.components.StateComponent::class.java)
                    ?.transitionTo(liric.mistaken.characters.states.AttackState, force = true)
            }
        }
    }

    /**
     * Trigger: Activacin de abilities activas (Click Derecho).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseAbility(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.action.isRightClick) return

        val player = event.player

        
        val session = plugin.sessionManager.getSession(player) ?: return
        if (session.currentState != GameState.INGAME) return

        
        if (player.gameMode != GameMode.SURVIVAL || plugin.spectatorManager.isSpectator(player)) return

        val slot = player.inventory.heldItemSlot
        if (!session.isKiller(player.uniqueId)) return
        val killer = plugin.killerManager.getKillerOfPlayer(player) ?: return

        val config = plugin.configManager.getKillerConfig(killer.id)
        val pathBase = "asesinos.${killer.id}"

        var abilityEjecutada = -1
        for (i in 1..4) {
            val configSlot = config.getInt("items.skill${i}_slot", i)
            if (slot == configSlot) {
                abilityEjecutada = i
                break
            }
        }

        if (abilityEjecutada == -1) return

        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) return

        event.isCancelled = true
        plugin.server.scheduler.runTask(plugin, Runnable { player.updateInventory() })

        
        killer.useSkill(player, abilityEjecutada)
    }

    /**
     * Lgica de impacto: Abilities basadas en proyectiles (Ej: Entity 303).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        val shooter = snowball.shooter as? Player ?: return

        
        val session = plugin.sessionManager.getSession(shooter) ?: return

        val nameComp = snowball.customName() ?: return
        val rawName = plain.serialize(nameComp)

        if (rawName == "303_infection") {
            val loc = snowball.location
            val world = loc.world ?: return

            
            world.spawnParticle(Particle.ENCHANTED_HIT, loc, 15, 0.3, 0.3, 0.3, 0.1)
            val dust = Particle.DustOptions(Color.fromRGB(0, 255, 240), 1.0f)
            world.spawnParticle(Particle.DUST, loc, 10, 0.2, 0.2, 0.2, 0.1, dust)

            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f)
            world.playSound(loc, Sound.ENTITY_ITEM_BREAK, 0.8f, 0.1f)

            
            val victim = event.hitEntity as? Player ?: return

            
            if (session.isKiller(victim.uniqueId)) return

            
            if (victim.gameMode != GameMode.SURVIVAL || plugin.spectatorManager.isSpectator(victim)) return

            victim.apply {
                addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
                addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0))

                
                session.combatManager.takeDamage(this)

                world.spawnParticle(Particle.ANGRY_VILLAGER, location.add(0.0, 1.5, 0.0), 5, 0.2, 0.2, 0.2, 0.1)
                playSound(location, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.5f)

                sendMessage(ColorTranslator.translate("<red><bold>[!]</bold> <gray>SISTEMA CORROMPIDO: <white>Has sido infectado por la Estrella del Error."))
            }
        }
    }
}
