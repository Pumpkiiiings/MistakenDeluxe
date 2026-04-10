package liric.mistaken.listeners.supervivientes

import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.supervivientes.clases.RaincoatKid
import liric.mistaken.supervivientes.clases.KasaneTeto
import liric.mistaken.supervivientes.clases.Jesse
import liric.mistaken.supervivientes.clases.Aldeano
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.Sound
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
 *[LIRIC-MISTAKEN 2.0]
 * SupervivienteHabilidadListener.
 * FIX: Adaptado a la nueva Comandante Teto (Azada Revólver) y seguridad post-mortem.
 */
class SupervivienteHabilidadListener(private val plugin: Mistaken) : Listener {

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
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val player = event.player

        // 🔥 LA SOLUCIÓN: Si está especteando, invisible, o congelado, bloqueamos el click
        if (player.gameMode != GameMode.SURVIVAL) return
        if (player.isInvisible) return
        if (plugin.gameManager.combatManager.isFrozen(player)) return

        val slot = player.inventory.heldItemSlot

        if (slot > 2) return
        if (plugin.gameManager.esAsesino(player.uniqueId)) return

        val clase = plugin.supervivienteManager.getClase(player) ?: return
        if (player.inventory.itemInMainHand.type.isAir) return

        when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                // Ignorar armas Melee que se usan pegando en vez de click derecho
                if (clase is RaincoatKid && slot == 2) return
                if (clase is Jesse && slot == 1) return

                event.isCancelled = true
                clase.usarHabilidad(player, slot)
            }
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                // Trackear heridos si tienen la habilidad en el slot 1
                if (slot == 1 && clase !is KasaneTeto) {
                    event.isCancelled = true
                    clase.trackearHeridos(player)
                }
                // 🔥 NUEVO: Para Kasane Teto, el click izquierdo dispara el Revólver (Slot 0)
                else if (clase is KasaneTeto && slot == 0) {
                    event.isCancelled = true
                    clase.usarHabilidad(player, slot)
                }
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSurvivorMeleeAttack(event: EntityDamageByEntityEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return

        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        if (plugin.gameManager.esAsesino(attacker.uniqueId)) return
        if (!plugin.gameManager.esAsesino(victim.uniqueId)) return

        // Seguridad post-mortem y anti-congelamiento
        if (attacker.gameMode != GameMode.SURVIVAL || attacker.isInvisible || plugin.gameManager.combatManager.isFrozen(attacker)) return

        val item = attacker.inventory.itemInMainHand
        if (!item.hasItemMeta()) return
        val pdc = item.itemMeta.persistentDataContainer

        val clase = plugin.supervivienteManager.getClase(attacker) ?: return

        // 1. Raincoat Kid (Palo)
        if (pdc.has(STICK_KEY, PersistentDataType.BYTE) && clase is RaincoatKid) {
            val cooldownTime = plugin.configManager.getSupervivientes()
                .getInt("supervivientes.raincoatkid.items.habilidad3_cooldown", 40)

            if (!clase.checkCooldown(attacker, 2, cooldownTime)) {
                clase.aplicarGolpePalo(victim)
                attacker.sendMessage(plugin.mm.deserialize("<green><bold>¡BAM!</bold> <gray>Asesino aturdido."))
                attacker.playSound(attacker.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.2f)
            }
            return
        }

        // 2. Jesse (Puñetazo)
        if (pdc.has(JESSE_PUNCH_KEY, PersistentDataType.BYTE) && clase is Jesse) {
            val cooldownTime = plugin.configManager.getSupervivientes()
                .getInt("supervivientes.jesse.items.habilidad2_cooldown", 15)

            if (!clase.checkCooldown(attacker, 1, cooldownTime)) {
                clase.aplicarGolpePuno(victim)
                attacker.sendMessage(plugin.mm.deserialize("<gold><b>¡TOMA ESO!</b></gold>"))
            }
            return
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        val victim = event.hitEntity as? Player ?: return
        val pdc = snowball.persistentDataContainer

        // Verificamos que la víctima esté viva y activa en el juego
        if (victim.gameMode != GameMode.SURVIVAL || victim.isInvisible) return

        // 1. Roca (Civil)
        if (pdc.has(ROCA_KEY, PersistentDataType.BYTE)) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 1))
            victim.playSound(victim.location, Sound.BLOCK_STONE_BREAK, 1f, 0.8f)
            (snowball.shooter as? Player)?.let { shooter ->
                shooter.sendMessage(plugin.messageConfig.getMessage(shooter, "habilidades.roca-impacto-exito"))
            }
            return
        }

        // 2. Pedido (Repartidor)
        if (pdc.has(PEDIDO_KEY, PersistentDataType.BYTE)) {
            val isKiller = plugin.gameManager.esAsesino(victim.uniqueId)
            if (isKiller) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.sendMessage(plugin.messageConfig.getMessage(victim, "habilidades.pedido-impacto-asesino"))
                victim.playSound(victim.location, Sound.ENTITY_GENERIC_SPLASH, 1f, 1f)
            } else {
                val maxHealth = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                victim.health = (victim.health + 4.0).coerceAtMost(maxHealth)
                victim.sendMessage(plugin.messageConfig.getMessage(victim, "habilidades.pedido-recibido-cura"))
                victim.playSound(victim.location, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
            }
            return
        }

        // 3. 🔥 Soborno (Aldeano) 🔥
        if (pdc.has(EMERALD_KEY, PersistentDataType.BYTE)) {
            val isKiller = plugin.gameManager.esAsesino(victim.uniqueId)
            if (isKiller) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 1))

                victim.world.playSound(victim.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                victim.world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, victim.eyeLocation, 10)

                victim.sendMessage(plugin.mm.deserialize("<green>¡Ooh! ¡Una esmeralda! (Te has distraído)"))
            }
            return
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onSalsaMove(event: PlayerMoveEvent) {
        if (plugin.gameManager.currentState != GameState.INGAME) return
        val player = event.player

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
