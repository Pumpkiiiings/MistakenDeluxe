package liric.mistaken.roles.asesinos

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Asesino: Clase base polimórfica ultra-optimizada.
 * FIX: Schedulers nativos (Folia) y soporte Multiarena (Safe Calls).
 */
abstract class Asesino(val id: String, val nombre: String) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    private val cooldowns = ConcurrentHashMap<String, Long>()
    protected val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    fun checkCooldown(player: Player, slot: Int): Boolean {
        val globalConfig = plugin.configManager.getAsesinos()
        val cooldownSecs = globalConfig.getInt("asesinos.$id.items.habilidad${slot}_cooldown", 0)

        if (cooldownSecs <= 0) return false

        val langConfig = plugin.messageConfig.getSpecificFile(player, "asesinos")
        val nombreHab = langConfig.getString("asesinos.$id.items.habilidad${slot}_nombre") ?: "Skill $slot"

        val key = "${player.uniqueId}_$slot"
        val now = System.currentTimeMillis()
        val expireTime = cooldowns.getOrDefault(key, 0L)

        if (now < expireTime) {
            val remaining = (expireTime - now) / 1000.0
            val msg = plugin.messageConfig.getMessage(player, "errors.ability-cooldown",
                Placeholder.parsed("skill", nombreHab),
                Placeholder.parsed("time", "%.1f".format(remaining))
            )

            // Folia: Efectos visuales/sonido en su scheduler
            player.scheduler.run(plugin, { _ ->
                player.sendActionBar(msg)
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f)
            }, null)
            return true
        }

        cooldowns[key] = now + (cooldownSecs * 1000L)
        return false
    }

    protected fun trackTask(task: ScheduledTask) {
        activeTasks.add(task)
    }

    fun reproducirEfectosHabilidad(player: Player, slot: Int) {
        val config = plugin.configManager.getAsesinos()
        val sonidoName = config.getString("asesinos.$id.items.habilidad${slot}_sonido") ?: return

        player.scheduler.run(plugin, { _ ->
            runCatching {
                val sound = Sound.valueOf(sonidoName.uppercase())
                player.world.playSound(player.location, sound, 1.0f, 0.7f)
            }.onFailure {
                player.playSound(player.location, sonidoName, 1.0f, 0.7f)
            }
        }, null)
    }

    open fun limpiarDatosGlobales() {}

    open fun cleanup(player: Player?) {
        activeTasks.forEach {
            if (!it.isCancelled) it.cancel()
        }
        activeTasks.clear()

        player?.let { p ->
            p.scheduler.run(plugin, { _ ->
                if (p.isOnline) {
                    p.inventory.clear()
                    p.inventory.armorContents = arrayOfNulls(4)

                    p.activePotionEffects.toList().forEach { p.removePotionEffect(it.type) }

                    p.isSwimming = false
                    p.isGliding = false
                    p.isGlowing = false
                    p.inventory.heldItemSlot = 0

                    if (p.gameMode != GameMode.SPECTATOR) {
                        p.allowFlight = false
                        p.isFlying = false
                    }

                    resetAttributes(p)

                    val prefix = p.uniqueId.toString()
                    cooldowns.keys.removeIf { it.startsWith(prefix) }
                    p.persistentDataContainer.remove(plugin.assassinKey)
                    p.updateInventory()
                }
            }, null)
        }
    }

    private fun resetAttributes(player: Player) {
        val attributes = listOf(
            Attribute.MAX_HEALTH,
            Attribute.MOVEMENT_SPEED,
            Attribute.ATTACK_DAMAGE,
            Attribute.ATTACK_SPEED,
            Attribute.KNOCKBACK_RESISTANCE,
            Attribute.SCALE,
            Attribute.STEP_HEIGHT,
            Attribute.GRAVITY,
            Attribute.JUMP_STRENGTH
        )

        attributes.forEach { attr ->
            player.getAttribute(attr)?.let { instance ->
                instance.modifiers.forEach { instance.removeModifier(it) }
                instance.baseValue = instance.defaultValue
            }
        }
    }

    protected fun esObjetivoValido(atacante: Player, victima: Player): Boolean {
        if (victima.gameMode != GameMode.SURVIVAL) return false
        if (plugin.isIgnored(victima)) return false
        if (victima.isInvisible) return false

        if (atacante.uniqueId == victima.uniqueId) return false

        val sessionManager = plugin.sessionManager ?: return false
        val session = sessionManager.getSession(victima) ?: return false

        if (sessionManager.getSession(atacante) != session) return false

        val atacanteEsAsesino = session.esAsesino(atacante.uniqueId)
        val victimaEsAsesino = session.esAsesino(victima.uniqueId)

        if (session.currentMode == MistakenMode.ASSASSIN_PVP) return true
        if (atacanteEsAsesino && victimaEsAsesino) return false

        return true
    }

    abstract fun equipar(player: Player)
    abstract fun usarHabilidad(player: Player, slot: Int)
    abstract fun mostrarTrail(player: Player)
    open fun mostrarTrailFisico(player: Player) {}
}
