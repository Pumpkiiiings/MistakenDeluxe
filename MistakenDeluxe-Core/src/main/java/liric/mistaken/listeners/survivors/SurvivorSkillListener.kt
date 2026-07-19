package liric.mistaken.listeners.survivors

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.roles.survivors.clases.RaincoatKid
import liric.mistaken.roles.survivors.clases.KasaneTeto
import liric.mistaken.roles.survivors.clases.Jesse
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * SurvivorHabilidadListener: Adaptado para MULTIARENA / VELOCITY.
 * FIX: Ahora detecta la sesiï¿½n individual de cada jugador para procesar sus habilidades.
 */
class SurvivorHabilidadListener(private val plugin: Mistaken) : Listener {

    companion object {
        val bloquesDerrame = ConcurrentHashMap.newKeySet<String>()

        private val ROCA_KEY = NamespacedKey("mistaken", "roca")
        private val PEDIDO_KEY = NamespacedKey("mistaken", "pedido")
        private val EMERALD_KEY = NamespacedKey("mistaken", "villager_emerald")

        // Llaves para Melee
        private val STICK_KEY = NamespacedKey("mistaken", "kid_stick")
        private val JESSE_PUNCH_KEY = NamespacedKey("mistaken", "jesse_punch")

        fun marcarBloque(loc: org.bukkit.Location) {
            val key = "${loc.world.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"
            bloquesDerrame.add(key)
        }

        fun desmarcarBloque(loc: org.bukkit.Location) {
            val key = "${loc.world.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"
            bloquesDerrame.remove(key)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onUseSurvivorAbility(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val player = event.player

        // ?? MULTIARENA: Buscamos la sesiï¿½n del jugador
        val session = plugin.sessionManager.getSession(player) ?: return
        if (session.currentState != GameState.INGAME) return

        // Seguridad: Solo en Survival e ignora congelados
        if (player.gameMode != GameMode.SURVIVAL || player.isInvisible) return
        if (plugin.combatManager.isFrozen(player)) return

        val slot = player.inventory.heldItemSlot
        if (slot > 2) return

        // Verifica que NO sea el asesino de SU sesiï¿½n
        if (session.isKiller(player.uniqueId)) return

        val clase = plugin.supervivienteManager.getSurvivorClass(player) ?: return
        if (player.inventory.itemInMainHand.type.isAir) return

        when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                // El RaincoatKid y Jesse usan melee en ciertos slots, saltamos el mensaje de habilidad
                if (clase is RaincoatKid && slot == 2) return
                if (clase is Jesse && slot == 1) return

                event.isCancelled = true
                clase.useSkill(player, slot)
            }
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                // Comandante Teto dispara su Revï¿½lver con Click Izquierdo en el Slot 0
                if (clase is KasaneTeto && slot == 0) {
                    event.isCancelled = true
                    clase.useSkill(player, slot)
                } else if (slot == 1) {
                    // Otros supervivientes usan rastreador en slot 1
                    event.isCancelled = true
                    clase.trackearHeridos(player)
                }
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSurvivorMeleeAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        // ?? MULTIARENA: Buscamos la sesiï¿½n del atacante
        val session = plugin.sessionManager.getSession(attacker) ?: return
        if (session.currentState != GameState.INGAME) return

        // Reglas de equipo: El atacante debe ser humano y la vï¿½ctima asesino en la MISMA sesiï¿½n
        if (session.isKiller(attacker.uniqueId)) return
        if (!session.isKiller(victim.uniqueId)) return

        if (attacker.gameMode != GameMode.SURVIVAL || attacker.isInvisible || plugin.combatManager.isFrozen(attacker)) return

        val item = attacker.inventory.itemInMainHand
        if (!item.hasItemMeta()) return
        val pdc = item.itemMeta.persistentDataContainer

        val clase = plugin.supervivienteManager.getSurvivorClass(attacker) ?: return

        // 1. Raincoat Kid (Palo)
        if (pdc.has(STICK_KEY, PersistentDataType.BYTE) && clase is RaincoatKid) {
            val cooldownTime = plugin.configManager.getSurvivorConfig(clase.id).getInt("supervivientes.raincoatkid.items.skill3_cooldown", 40)
            if (!clase.checkCooldown(attacker, 2, cooldownTime)) {
                clase.aplicarGolpePalo(victim)
                attacker.sendMessage(plugin.mm.deserialize("<green><bold>ï¿½BAM!</bold> <gray>Killer aturdido."))
                attacker.playSound(attacker.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.2f)
            }
            return
        }

        // 2. Jesse (Puï¿½etazo)
        if (pdc.has(JESSE_PUNCH_KEY, PersistentDataType.BYTE) && clase is Jesse) {
            val cooldownTime = plugin.configManager.getSurvivorConfig(clase.id).getInt("supervivientes.jesse.items.skill2_cooldown", 15)
            if (!clase.checkCooldown(attacker, 1, cooldownTime)) {
                clase.aplicarGolpePuno(victim)
                attacker.sendMessage(plugin.mm.deserialize("<gold><b>ï¿½TOMA ESO!</b></gold>"))
            }
            return
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        val victim = event.hitEntity as? Player ?: return
        val pdc = snowball.persistentDataContainer

        // ?? MULTIARENA: Buscamos la sesiï¿½n de la vï¿½ctima para validar el rol
        val session = plugin.sessionManager.getSession(victim) ?: return

        if (victim.gameMode != GameMode.SURVIVAL || victim.isInvisible) return

        // 1. Roca (Civilian)
        if (pdc.has(ROCA_KEY, PersistentDataType.BYTE)) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 1))
            victim.playSound(victim.location, Sound.BLOCK_STONE_BREAK, 1f, 0.8f)
            (snowball.shooter as? Player)?.let { shooter ->
                shooter.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(shooter, "habilidades.roca-impacto-exito"))
            }
            return
        }

        // 2. Pedido (DeliveryMan)
        if (pdc.has(PEDIDO_KEY, PersistentDataType.BYTE)) {
            val isKiller = session.isKiller(victim.uniqueId)
            if (isKiller) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(victim, "habilidades.pedido-impacto-asesino"))
                victim.playSound(victim.location, Sound.ENTITY_GENERIC_SPLASH, 1f, 1f)
            } else {
                val maxHealth = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                victim.health = (victim.health + 4.0).coerceAtMost(maxHealth)
                victim.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(victim, "habilidades.pedido-recibido-cura"))
                victim.playSound(victim.location, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
            }
            return
        }

        // 3. Soborno (Villager)
        if (pdc.has(EMERALD_KEY, PersistentDataType.BYTE)) {
            val isKiller = session.isKiller(victim.uniqueId)
            if (isKiller) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 1))
                victim.world.playSound(victim.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                victim.sendMessage(plugin.mm.deserialize("<green>ï¿½Ooh! ï¿½Una esmeralda! (Te has distraï¿½do)"))
            }
            return
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onSalsaMove(event: PlayerMoveEvent) {
        val player = event.player
        val session = plugin.sessionManager.getSession(player) ?: return
        if (session.currentState != GameState.INGAME) return

        if (player.gameMode != GameMode.SURVIVAL || player.isInvisible) return

        val to = event.to ?: return
        val from = event.from
        if (from.blockX == to.blockX && from.blockZ == to.blockZ && from.blockY == to.blockY) return

        val key = "${to.world.name}_${to.blockX}_${to.blockY}_${to.blockZ}"
        if (bloquesDerrame.contains(key)) {
            if (!player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2))
                player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_STEP, 0.5f, 0.5f)
            }
        }
    }
}



