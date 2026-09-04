package liric.mistaken.roles.killers.classes

import liric.mistaken.models.components.CombatComponent
import liric.mistaken.models.core.Character
import liric.mistaken.models.states.CharacterState
import liric.mistaken.roles.killers.BaseKiller
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap



abstract class WardenSwipeState(override val id: String) : CharacterState {
    override val priority = 50
    
    private val litBlocks = java.util.concurrent.ConcurrentHashMap<java.util.UUID, org.bukkit.Location>()
    private val restoreData = java.util.concurrent.ConcurrentHashMap<java.util.UUID, org.bukkit.block.data.BlockData>()

    override fun onEnter(character: Character) {
        val player = character.entity as? org.bukkit.entity.Player ?: return
        val session = liric.mistaken.Mistaken.instance.sessionManager.getSession(player) ?: return
        
        
        val loc = player.eyeLocation
        val block = loc.block
        if (block.type == org.bukkit.Material.AIR || block.type == org.bukkit.Material.CAVE_AIR) {
            val lightData = org.bukkit.Bukkit.createBlockData(org.bukkit.Material.LIGHT)
            if (lightData is org.bukkit.block.data.type.Light) {
                lightData.level = 15
            }
            
            litBlocks[player.uniqueId] = block.location
            restoreData[player.uniqueId] = block.blockData
            
            for (p in session.getPlayers()) {
                liric.mistaken.packet.PacketFactory.blocks.sendBlockChange(p, block.location, lightData)
            }
        }
    }

    override fun onExit(character: Character) {
        val player = character.entity as? org.bukkit.entity.Player ?: return
        val session = liric.mistaken.Mistaken.instance.sessionManager.getSession(player) ?: return
        
        val loc = litBlocks.remove(player.uniqueId)
        val data = restoreData.remove(player.uniqueId)
        
        if (loc != null && data != null) {
            for (p in session.getPlayers()) {
                liric.mistaken.packet.PacketFactory.blocks.sendBlockChange(p, loc, data)
            }
        }
    }
}

object WardenSwipe1State : WardenSwipeState("swipe_1")
object WardenSwipe2State : WardenSwipeState("swipe_2")
object WardenSwipe3State : WardenSwipeState("swipe_3")

object WardenStunState : CharacterState {
    override val id = "stun"
    override val priority = 90
}

object WardenSlamState : CharacterState {
    override val id = "slam"
    override val priority = 80
}

object WardenRageState : CharacterState {
    override val id = "rage"
    override val priority = 80
}

object WardenSniffWalkState : CharacterState {
    override val id = "sniff_walk"
    override val priority = 90
    
    
    override fun onEnter(character: Character) {
        val player = character.entity as? org.bukkit.entity.Player ?: return
        player.walkSpeed = 0.0f
    }
    
    override fun onExit(character: Character) {
        val player = character.entity as? org.bukkit.entity.Player ?: return
        player.walkSpeed = 0.2f
    }
}



class WardenKiller : BaseKiller("warden", "Warden") {
    
    override fun getModelId(): String = "warden"

    
    private val comboSteps = ConcurrentHashMap<UUID, Int>()
    private val lastAttackTimes = ConcurrentHashMap<UUID, Long>()

    override fun equip(player: Player) {
        super.equip(player) 
        
        val inv = player.inventory
        val configMecanica = liric.mistaken.Mistaken.instance.configManager.getKillerConfig(this.id)
        val langInfo = liric.mistaken.config.engine.core.MessageService.getSpecificFile(player, "killers_info")

        fun deliver(key: String, slot: Int) {
            val id = configMecanica.getString("items.$key")
            if (id == null || id == "none") return

            val item = liric.mistaken.utils.resourcepack.CustomItemManager.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = org.bukkit.Material.matchMaterial(matName)
                if (mat != null) org.bukkit.inventory.ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "weapon") "killers.warden.skill_names.weapon"
            else "killers.warden.skill_names.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(liric.mistaken.utils.color.ColorTranslator.translate(it)) }
            }
            inv.setItem(slot, item)
        }

        deliver("skill1", 1)
        deliver("skill2", 2)
        deliver("skill3", 3)
        deliver("skill4", 4)
        deliver("weapon", 8)
    }

    override fun setupAdditionalComponents(character: Character) {
        val uuid = character.entity.uniqueId
        
        character.addComponent(CombatComponent::class.java, object : CombatComponent {
            override fun onEnable(character: Character) {}
            override fun onDisable() {}
            
            override fun performAttack(attackId: String) {
                if (character.entity is Player) {
                    this@WardenKiller.attack(character.entity as Player)
                }
            }

            override fun takeDamage(amount: Double, source: Any?): Boolean {
                return true
            }
        })
    }

    override fun useSkill(player: Player, slot: Int) {
        when (slot) {
            1 -> performSlam(player)
            2 -> performRage(player)
            3 -> performSniffWalk(player)
        }
    }

    private fun performSlam(player: Player) {
        val character = getCharacter(player) ?: return
        transitionTo(player, WardenSlamState, force = true)

        val direction = player.location.direction
        direction.y = 0.0
        if (direction.lengthSquared() > 0) direction.normalize()
        player.velocity = direction.multiply(1.5).setY(1.0)

        player.world.playSound(player.location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.5f)

        player.scheduler.runAtFixedRate(liric.mistaken.Mistaken.instance, java.util.function.Consumer { task ->
            if (!player.isOnline || getCharacter(player)?.getComponent(liric.mistaken.models.components.StateComponent::class.java)?.currentState != WardenSlamState) {
                task.cancel()
                return@Consumer
            }

            if ((player.isOnGround || player.location.block.getRelative(org.bukkit.block.BlockFace.DOWN).type.isSolid) && player.velocity.y <= 0) {
                task.cancel()
                transitionTo(player, liric.mistaken.models.states.IdleState, force = true)

                    val loc = player.location
                    val world = loc.world ?: return@Consumer
                    val session = liric.mistaken.Mistaken.instance.sessionManager.getSession(player) ?: return@Consumer

                    player.world.playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f)
                    player.world.spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 5)

                    
                    for (victim in session.getPlayers()) {
                        if (victim == player) continue
                        if (!session.isKiller(victim.uniqueId) && victim.location.distanceSquared(loc) <= 16.0) { 
                            session.playerController.handlePlayerDeath(victim)
                        }
                    }

                    
                    val radiusBox = intArrayOf(1)
                    val maxRadius = 10
                    org.bukkit.Bukkit.getRegionScheduler().runAtFixedRate(liric.mistaken.Mistaken.instance, loc, java.util.function.Consumer { task ->
                        val radius = radiusBox[0]
                        if (radius > maxRadius) {
                            task.cancel()
                            return@Consumer
                        }

                        val cx = loc.blockX
                        val cy = loc.blockY
                        val cz = loc.blockZ

                        for (x in -radius..radius) {
                            for (z in -radius..radius) {
                                if (Math.max(Math.abs(x), Math.abs(z)) == radius) {
                                    val b = world.getBlockAt(cx + x, cy - 1, cz + z)
                                    if (b.type != org.bukkit.Material.AIR && b.type.isSolid) {
                                        val data = b.blockData
                                        val bLoc = b.location

                                        for (p in session.getPlayers()) {
                                            liric.mistaken.packet.PacketFactory.blocks.sendBlockChange(p, bLoc, org.bukkit.Material.AIR)
                                        }

                                        val fb = world.spawnFallingBlock(bLoc.add(0.5, 0.0, 0.5), data)
                                        fb.velocity = org.bukkit.util.Vector(0.0, 0.4, 0.0)
                                        fb.dropItem = false
                                        fb.setHurtEntities(false)

                                        fb.scheduler.runDelayed(liric.mistaken.Mistaken.instance, java.util.function.Consumer {
                                            if (fb.isValid) fb.remove()
                                            for (p in session.getPlayers()) {
                                                liric.mistaken.packet.PacketFactory.blocks.sendBlockChange(p, bLoc, data)
                                            }
                                        }, null, 20L)
                                    }
                                }
                            }
                        }
                        player.world.playSound(loc, org.bukkit.Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f)
                        radiusBox[0]++
                    }, 1L, 2L)
                }
        }, null, 10L, 1L)
    }

    private fun performRage(player: Player) {
        transitionTo(player, WardenRageState, force = true)
        player.world.playSound(player.location, org.bukkit.Sound.ENTITY_WARDEN_ANGRY, 1.5f, 1.0f)
        player.world.spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, player.location.add(0.0, 2.0, 0.0), 10, 0.5, 0.5, 0.5, 0.0)
        
        player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 15, 1))
        player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, 20 * 15, 1))
        player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 20 * 15, 0))
    }

    private fun performSniffWalk(player: Player) {
        transitionTo(player, WardenSniffWalkState, force = true)
        player.world.playSound(player.location, org.bukkit.Sound.ENTITY_WARDEN_SNIFF, 1.5f, 1.0f)

        player.scheduler.runDelayed(liric.mistaken.Mistaken.instance, java.util.function.Consumer {
            if (player.isOnline && getCharacter(player)?.getComponent(liric.mistaken.models.components.StateComponent::class.java)?.currentState == WardenSniffWalkState) {
                transitionTo(player, liric.mistaken.models.states.IdleState, force = true)
                
                val session = liric.mistaken.Mistaken.instance.sessionManager.getSession(player) ?: return@Consumer
                player.world.playSound(player.location, org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 1.0f)

                for (victim in session.getPlayers()) {
                    if (!session.isKiller(victim.uniqueId)) {
                        victim.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 20 * 15, 0))
                        victim.sendMessage("�c�El Warden te ha olfateado!")
                    }
                }
            }
        }, null, 100L) 
    }

    /**
     * L�gica de combo simple por player: Alterna entre swipe 1, 2 y 3.
     */
    fun attack(player: Player) {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        val lastAttackTime = lastAttackTimes.getOrDefault(uuid, 0L)
        
        var comboStep = comboSteps.getOrDefault(uuid, 0)
        
        
        if (now - lastAttackTime > 1500) {
            comboStep = 0
        }

        val state = when (comboStep) {
            0 -> WardenSwipe1State
            1 -> WardenSwipe2State
            else -> WardenSwipe3State
        }

        
        transitionTo(player, state, force = true)
        
        lastAttackTimes[uuid] = now
        comboSteps[uuid] = (comboStep + 1) % 3 
    }

    /**
     * Aturde al Warden temporalmente.
     */
    fun applyStun(player: Player) {
        transitionTo(player, WardenStunState, force = true)
    }
}
