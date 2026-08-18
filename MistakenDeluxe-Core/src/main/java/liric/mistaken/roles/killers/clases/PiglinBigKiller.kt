package liric.mistaken.roles.killers.clases

import liric.mistaken.Mistaken
import liric.mistaken.characters.components.CombatComponent
import liric.mistaken.characters.components.StateComponent
import liric.mistaken.characters.core.Character
import liric.mistaken.characters.states.CharacterState
import liric.mistaken.characters.states.IdleState
import liric.mistaken.roles.killers.BaseKiller
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.data.BlockData
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import pumpking.lib.color.ColorTranslator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PiglinBigCinematicState : CharacterState {
    override val id = "stat_battle" // Usa la animación del modelo
    override val priority = 100
}

object PiglinBigMelee1State : CharacterState {
    override val id = "melee1"
    override val priority = 50
}

object PiglinBigMelee2State : CharacterState {
    override val id = "melee2"
    override val priority = 50
}

object PiglinBigMelee3State : CharacterState {
    override val id = "melee3"
    override val priority = 50
}

object PiglinBigHookThrowState : CharacterState {
    override val id = "hook_throw"
    override val priority = 70
}

class PiglinBigKiller : BaseKiller("piglinbig", "PiglinBig") {
    
    override fun getModelId(): String = "bigpig"

    private val comboSteps = ConcurrentHashMap<UUID, Int>()
    private val lastAttackTimes = ConcurrentHashMap<UUID, Long>()

    override fun equip(player: Player) {
        super.equip(player) // Inicializa el ECS (modelo, animaciones)
        
        // Ejecutar animación de spawn/cinemática
        transitionTo(player, PiglinBigCinematicState, force = true)
        
        val inv = player.inventory
        val configMecanica = Mistaken.instance.configManager.getKillerConfig(this.id)
        val langInfo = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "killers_info")

        fun deliver(key: String, slot: Int) {
            val itemId = configMecanica.getString("items.$key")
            if (itemId == null || itemId == "none") return

            val item = liric.mistaken.utils.hooks.CraftEngine.getCustomItem(itemId) ?: run {
                val matName = itemId.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "weapon") "asesinos.piglinbig.skill_names.weapon"
            else "asesinos.piglinbig.skill_names.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(ColorTranslator.translate(it)) }
            }
            inv.setItem(slot, item)
        }

        deliver("skill1", 1)
        deliver("skill2", 2)
        deliver("weapon", 8)
    }

    override fun setupAdditionalComponents(character: Character) {
        character.addComponent(CombatComponent::class.java, object : CombatComponent {
            override fun onEnable(character: Character) {}
            override fun onDisable() {}
            
            override fun performAttack(attackId: String) {
                if (character.entity is Player) {
                    this@PiglinBigKiller.attack(character.entity as Player)
                }
            }

            override fun takeDamage(amount: Double, source: Any?): Boolean {
                return true
            }
        })
    }

    fun attack(player: Player) {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        val lastAttackTime = lastAttackTimes.getOrDefault(uuid, 0L)
        
        var comboStep = comboSteps.getOrDefault(uuid, 0)
        
        // Resetea el combo si pasó mucho tiempo (ej: más de 1.5 segundos) sin atacar
        if (now - lastAttackTime > 1500) {
            comboStep = 0
        }

        val state = when (comboStep) {
            0 -> PiglinBigMelee1State
            1 -> PiglinBigMelee2State
            else -> PiglinBigMelee3State
        }

        transitionTo(player, state, force = true)
        
        lastAttackTimes[uuid] = now
        comboSteps[uuid] = (comboStep + 1) % 3 // Cicla entre 0, 1 y 2
    }

    override fun useSkill(player: Player, slot: Int) {
        when (slot) {
            1 -> performGroundLift(player)
            2 -> performAxeThrow(player)
        }
    }

    // Skill 1: Levantar el Suelo (Rango 10x10)
    private fun performGroundLift(player: Player) {
        val loc = player.location
        val world = loc.world ?: return
        val session = Mistaken.instance.sessionManager.getSession(player) ?: return

        player.world.playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.5f)
        
        val radius = 5 // 10x10 = radio 5 desde el centro

        val cx = loc.blockX
        val cy = loc.blockY
        val cz = loc.blockZ

        // Levantar bloques visualmente
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                // Buscamos el bloque del suelo (hasta 2 bloques hacia abajo)
                var b = world.getBlockAt(cx + x, cy - 1, cz + z)
                if (!b.type.isSolid) {
                    b = world.getBlockAt(cx + x, cy - 2, cz + z)
                }
                
                if (b.type != Material.AIR && b.type.isSolid) {
                    val data = b.blockData
                    val bLoc = b.location

                    // Ocultamos el bloque original temporalmente
                    for (p in session.getPlayers()) {
                        liric.mistaken.packet.PacketFactory.blocks.sendBlockChange(p, bLoc, Material.AIR)
                    }

                    // Creamos un FallingBlock que simula que salta
                    val fb = world.spawnFallingBlock(bLoc.clone().add(0.5, 0.0, 0.5), data)
                    // Hacemos que suba y luego vuelva a caer
                    fb.velocity = Vector(0.0, 0.6 + (Math.random() * 0.2), 0.0)
                    fb.dropItem = false
                    fb.setHurtEntities(false)

                    // Restauramos el bloque original después de 1.5 segundos
                    Bukkit.getScheduler().runTaskLater(Mistaken.instance, Runnable {
                        if (fb.isValid) fb.remove()
                        for (p in session.getPlayers()) {
                            liric.mistaken.packet.PacketFactory.blocks.sendBlockChange(p, bLoc, data)
                        }
                    }, 30L)
                }
            }
        }

        // Aplicar aturdimiento a supervivientes en el radio
        for (victim in session.getPlayers()) {
            if (victim == player) continue
            if (!session.isKiller(victim.uniqueId)) {
                // Checar si están dentro del área 10x10
                val distanceX = Math.abs(victim.location.x - loc.x)
                val distanceZ = Math.abs(victim.location.z - loc.z)
                
                if (distanceX <= radius && distanceZ <= radius && Math.abs(victim.location.y - loc.y) <= 3) {
                    // Aturdir (Slowness y Blindness)
                    victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 20 * 3, 5))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 20 * 3, 1))
                    victim.playSound(victim.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.5f)
                    victim.sendMessage(ColorTranslator.translate("<red>¡Has sido aturdido por el terremoto!</red>"))
                }
            }
        }
    }

    // Skill 2: Lanzar Hacha de Oro
    private fun performAxeThrow(player: Player) {
        transitionTo(player, PiglinBigHookThrowState, force = true)
        player.world.playSound(player.location, Sound.ENTITY_EGG_THROW, 1.0f, 0.5f)
        
        val startLoc = player.eyeLocation
        val direction = startLoc.direction.normalize()
        val session = Mistaken.instance.sessionManager.getSession(player) ?: return

        // Creamos un item tirado que simula el hacha
        val axeItem = player.world.dropItem(startLoc, ItemStack(Material.GOLDEN_AXE))
        axeItem.pickupDelay = Int.MAX_VALUE
        axeItem.setGravity(false)
        axeItem.velocity = direction.multiply(1.5) // Velocidad del proyectil

        object : BukkitRunnable() {
            var ticks = 0
            val maxTicks = 40 // Vive hasta 2 segundos
            
            override fun run() {
                if (!axeItem.isValid || ticks > maxTicks || axeItem.isOnGround) {
                    axeItem.remove()
                    cancel()
                    return
                }

                // Partículas mientras vuela
                axeItem.world.spawnParticle(Particle.CRIT, axeItem.location, 2, 0.1, 0.1, 0.1, 0.0)

                // Detectar impacto
                for (victim in session.getPlayers()) {
                    if (victim == player || session.isKiller(victim.uniqueId)) continue
                    
                    if (victim.location.distanceSquared(axeItem.location) < 2.5) { // Hitbox pequeña
                        // Aplicar daño
                        Mistaken.instance.combatManager.takeDamage(victim)
                        victim.world.spawnParticle(Particle.BLOCK_CRUMBLE, victim.location.add(0.0, 1.0, 0.0), 10, 0.5, 0.5, 0.5, Material.REDSTONE_BLOCK.createBlockData())
                        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)
                        
                        axeItem.remove()
                        cancel()
                        return
                    }
                }
                
                ticks++
            }
        }.runTaskTimer(Mistaken.instance, 1L, 1L)
    }
}
