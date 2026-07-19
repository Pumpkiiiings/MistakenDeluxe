package liric.mistaken.roles.killers

import io.papermc.paper.threadedregions.scheduler.ScheduledTask

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Killer: Clase base polimÃƒ³rfica ultra-optimizada.
 * FIX: Adaptada a MULTIARENA y Schedulers Nativos de Paper (Folia-Ready).
 */
abstract class Killer(val id: String, val nombre: String) {

    open val defaultMusic: String? = null

    protected val api = liric.mistaken.api.MistakenProvider.get()
    protected val mm = api.mm

    // Cooldowns: UUID_Slot -> Timestamp (ms)
    private val cooldowns = ConcurrentHashMap<String, Long>()

    // Rastreros de tareas nativas de Paper para limpieza automÃƒ¡tica
    protected val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    /**
     * Verifica el cooldown buscando el tiempo en la raÃƒ­z y el nombre en el idioma del jugador.
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

            // Mensaje de error traducido desde es/messages.yml o en/messages.yml
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
     * Registra una tarea de Paper para limpieza.
     */
    protected fun trackTask(task: ScheduledTask) {
        activeTasks.add(task)
    }

    /**
     * Reproduce el sonido de la habilidad desde el archivo raÃƒ­z.
     */
    fun playSkillEffects(player: Player, slot: Int) {
        val config = api.configManager.getKillerConfig(this.id)
        val sonidoName = config.getString("items.habilidad${slot}_sound") ?: return

        runCatching {
            val sound = Sound.valueOf(sonidoName.uppercase())
            player.world.playSound(player.location, sound, 1.0f, 0.7f)
        }.onFailure {
            player.playSound(player.location, sonidoName, 1.0f, 0.7f)
        }
    }

    /**
     * Limpieza profunda del asesino (Mantenido el fix de espectador).
     */
    open fun clearGlobalData() {}

    open fun cleanup(player: Player?) {
        // Cancelamos las tareas programadas de Paper
        activeTasks.forEach {
            if (!it.isCancelled) it.cancel()
        }
        activeTasks.clear()

        player?.let { p ->
            if (p.isOnline) {
                p.inventory.clear()
                p.inventory.armorContents = arrayOfNulls(4)

                // Limpieza de pociones segura
                p.activePotionEffects.toList().forEach { p.removePotionEffect(it.type) }

                // Reset de estados fÃƒ­sicos
                p.isSwimming = false
                p.isGliding = false
                p.isGlowing = false

                // Aseguramos que vuelva al slot principal
                p.inventory.heldItemSlot = 0

                // Ã°Å¸â€™¡ FIX ESPECTADOR: Solo apagar vuelo si no es espectador
                if (p.gameMode != org.bukkit.GameMode.SPECTATOR) {
                    p.allowFlight = false
                    p.isFlying = false
                }

                resetAttributes(p)

                val prefix = p.uniqueId.toString()
                cooldowns.keys.removeIf { it.startsWith(prefix) }

                p.persistentDataContainer.remove(org.bukkit.NamespacedKey(api.plugin, "assassin_id"))
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
     * Ã°Å¸â€¥ Verifica de forma segura y central si una habilidad le debe hacer daÃƒ±o a este jugador.
     * MULTIARENA FIX: Toma en cuenta el Fuego Amigo leyendo la sesiÃƒ³n especÃƒ­fica de la vÃƒ­ctima.
     */
    protected fun isValidTarget(atacante: Player, victima: Player): Boolean {
        // 1. Inmortales o Espectadores ignorados
        if (victima.gameMode != org.bukkit.GameMode.SURVIVAL) return false
        if (api.isIgnored(victima)) return false
        if (victima.isInvisible) return false

        // 2. No se puede pegar a sÃƒ­ mismo con un Ãƒ¡rea
        if (atacante.uniqueId == victima.uniqueId) return false

        // 3. RevisiÃƒ³n de Fuego Amigo basada en la sesiÃƒ³n del jugador atacado
        val session = api.sessionManager.getSession(victima) ?: return false

        // Si el atacante no estÃƒ¡ en la misma sesiÃƒ³n, se deniega (Cruces entre arenas)
        if (api.sessionManager.getSession(atacante) != session) return false

        val atacanteEsAsesino = session.isKiller(atacante.uniqueId)
        val victimaEsAsesino = session.isKiller(victima.uniqueId)


        // Si es el modo normal y ambos son asesinos, no hay fuego amigo
        if (atacanteEsAsesino && victimaEsAsesino) {
            return false
        }

        // En cualquier otro caso (Killer vs Survivor) es vÃƒ¡lido
        return true
    }

    // --- MÃƒâ€°TODOS ABSTRACTOS ---
    abstract fun equip(player: Player)
    abstract fun useSkill(player: Player, slot: Int)
    abstract fun showTrail(player: Player)
    open fun showPhysicalTrail(player: Player) {}
}

