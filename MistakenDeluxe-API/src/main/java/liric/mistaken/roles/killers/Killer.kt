package liric.mistaken.roles.killers

import io.papermc.paper.threadedregions.scheduler.ScheduledTask

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.api.MistakenProvider
import liric.mistaken.api.util.Sounds
import org.bukkit.GameMode
import org.bukkit.NamespacedKey

import liric.mistaken.api.roles.GameRole


abstract class Killer(override val id: String, override val nombre: String) : GameRole {

    open val defaultMusic: String? = null

    protected val api = MistakenProvider.get()
    protected val mm = api.mm

    // Cooldowns: UUID_Slot -> Timestamp (ms)
    private val cooldowns = ConcurrentHashMap<String, Long>()


    /**
     * Verifica el cooldown buscando el tiempo en la raÃƒ­z y el nombre en el idioma del player.
     */
    fun checkCooldown(player: Player, slot: Int): Boolean {
        // 1. Obtenemos el tiempo del archivo raÃƒ­z (LÃƒ³gica global)
        val globalConfig = api.configManager.getKillerConfig(this.id)
        val cooldownSecs = globalConfig.getInt("items.skill${slot}_cooldown", 0)

        if (cooldownSecs <= 0) return false

        // 2. Obtenemos el nombre traducido para el feedback visual
        val langConfig = api.messages.getSpecificFile(player, "asesinos")
        val nombreHab = langConfig.getString("asesinos.$id.items.habilidad${slot}_name") ?: "Skill $slot"

        val key = "${player.uniqueId}_$slot"
        val now = System.currentTimeMillis()
        val expireTime = cooldowns.getOrDefault(key, 0L)

        if (now < expireTime) {
            val remaining = (expireTime - now) / 1000.0

            // Message de error traducido desde es/messages.yml o en/messages.yml
            val msg = api.messages.getComponent(player, "errors.ability-cooldown",
                Placeholder.parsed("skill", nombreHab),
                Placeholder.parsed("time", "%.1f".format(remaining))
            )

            player.sendActionBar(msg)
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f)
            return true
        }

        // Registrar nuevo cooldown
        cooldowns[key] = now + (cooldownSecs * 1000L)
        return false
    }


    /**
     * Reproduce el sonido de la ability desde el archivo raÃƒ­z.
     */
    fun playSkillEffects(player: Player, slot: Int) {
        val config = api.configManager.getKillerConfig(this.id)
        val sonidoName = config.getString("items.skill${slot}_sound") ?: return

        // Si no es un sonido de vanilla se manda el string tal cual: puede ser
        // un sonido custom del resource pack.
        val sound = Sounds.orNull(sonidoName)
        if (sound != null) {
            player.world.playSound(player.location, sound, 1.0f, 0.7f)
        } else {
            player.playSound(player.location, sonidoName, 1.0f, 0.7f)
        }
    }

    /**
     * Limpieza profunda del killer (Mantenido el fix de espectador).
     */
    open fun clearGlobalData() {}
    
    /**
     * Limpia los recursos globales asociados a este killer y previene su uso futuro.
     */
    open fun dispose() {
        clearGlobalData()
    }

    open override fun cleanup(player: Player?) {

        player?.let { p ->
            if (p.isOnline) {
                p.inventory.clear()
                p.inventory.armorContents = arrayOfNulls(4)

                // Limpieza de pociones segura
                p.activePotionEffects.toList().forEach { p.removePotionEffect(it.type) }

                
                p.isSwimming = false
                p.isGliding = false
                p.isGlowing = false

                // Aseguramos que vuelva al slot principal
                p.inventory.heldItemSlot = 0

                
                if (p.gameMode != GameMode.SPECTATOR) {
                    p.allowFlight = false
                    p.isFlying = false
                }

                resetAttributes(p)

                val prefix = p.uniqueId.toString()
                cooldowns.keys.removeIf { it.startsWith(prefix) }

                p.persistentDataContainer.remove(NamespacedKey(api.plugin, "assassin_id"))
                p.updateInventory()
            }
        }
    }

    /**
     * Resetea atributos a los valores por defecto de Minecraft 1.21.4.
     */
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

    /**
     * Ã°Å¸â€¥ Verifica de forma segura y central si una ability le debe hacer daÃƒ±o a este player.
     * MULTIARENA FIX: Toma en cuenta el Fuego Amigo leyendo la sesiÃƒ³n especÃƒ­fica de la vÃƒ­ctima.
     */
    protected fun isValidTarget(atacante: Player, victim: Player): Boolean {
        // 1. Inmortales o Espectadores ignorados
        if (victim.gameMode != GameMode.SURVIVAL) return false
        if (api.isIgnored(victim)) return false
        if (victim.isInvisible) return false

        // 2. No se puede pegar a sÃƒ­ mismo con un Ãƒ¡rea
        if (atacante.uniqueId == victim.uniqueId) return false

        // 3. RevisiÃƒ³n de Fuego Amigo basada en la sesiÃƒ³n del player atacado
        val session = api.sessionManager.getSession(victim) ?: return false

        
        if (api.sessionManager.getSession(atacante) != session) return false

        val atacanteEsKiller = session.isKiller(atacante.uniqueId)
        val victimEsKiller = session.isKiller(victim.uniqueId)


        
        if (atacanteEsKiller && victimEsKiller) {
            return false
        }

        // En cualquier otro caso (Killer vs Survivor) es vÃƒ¡lido
        return true
    }

    // --- MÃƒâ€°TODOS ABSTRACTOS ---
    abstract override fun equip(player: Player)
    abstract fun useSkill(player: Player, slot: Int)
    abstract fun showTrail(player: Player)
    open fun showPhysicalTrail(player: Player) {}
}

