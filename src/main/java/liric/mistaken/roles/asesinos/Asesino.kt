package liric.mistaken.roles.asesinos

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Asesino: Clase base polimórfica ultra-optimizada.
 * FIX: Adaptada a MULTIARENA y Schedulers Nativos de Paper (Folia-Ready).
 */
abstract class Asesino(val id: String, val nombre: String) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    // Cooldowns: UUID_Slot -> Timestamp (ms)
    private val cooldowns = ConcurrentHashMap<String, Long>()

    // Rastreros de tareas nativas de Paper para limpieza automática
    protected val activeTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    /**
     * Verifica el cooldown buscando el tiempo en la raíz y el nombre en el idioma del jugador.
     */
    fun checkCooldown(player: Player, slot: Int): Boolean {
        // 1. Obtenemos el tiempo del archivo raíz (Lógica global)
        val globalConfig = plugin.configManager.getAsesinos()
        val cooldownSecs = globalConfig.getInt("asesinos.$id.items.habilidad${slot}_cooldown", 0)

        if (cooldownSecs <= 0) return false

        // 2. Obtenemos el nombre traducido para el feedback visual
        val langConfig = plugin.messageConfig.getSpecificFile(player, "asesinos")
        val nombreHab = langConfig.getString("asesinos.$id.items.habilidad${slot}_nombre") ?: "Skill $slot"

        val key = "${player.uniqueId}_$slot"
        val now = System.currentTimeMillis()
        val expireTime = cooldowns.getOrDefault(key, 0L)

        if (now < expireTime) {
            val remaining = (expireTime - now) / 1000.0

            // Mensaje de error traducido desde es/messages.yml o en/messages.yml
            val msg = plugin.messageConfig.getMessage(player, "errors.ability-cooldown",
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
     * Reproduce el sonido de la habilidad desde el archivo raíz.
     */
    fun reproducirEfectosHabilidad(player: Player, slot: Int) {
        val config = plugin.configManager.getAsesinos()
        val sonidoName = config.getString("asesinos.$id.items.habilidad${slot}_sonido") ?: return

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
    open fun limpiarDatosGlobales() {}

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

                // Reset de estados físicos
                p.isSwimming = false
                p.isGliding = false
                p.isGlowing = false

                // Aseguramos que vuelva al slot principal
                p.inventory.heldItemSlot = 0

                // 💡 FIX ESPECTADOR: Solo apagar vuelo si no es espectador
                if (p.gameMode != org.bukkit.GameMode.SPECTATOR) {
                    p.allowFlight = false
                    p.isFlying = false
                }

                resetAttributes(p)

                val prefix = p.uniqueId.toString()
                cooldowns.keys.removeIf { it.startsWith(prefix) }
                p.persistentDataContainer.remove(plugin.assassinKey)
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
     * 🔥 Verifica de forma segura y central si una habilidad le debe hacer daño a este jugador.
     * MULTIARENA FIX: Toma en cuenta el Fuego Amigo leyendo la sesión específica de la víctima.
     */
    protected fun esObjetivoValido(atacante: Player, victima: Player): Boolean {
        // 1. Inmortales o Espectadores ignorados
        if (victima.gameMode != org.bukkit.GameMode.SURVIVAL) return false
        if (plugin.isIgnored(victima)) return false
        if (victima.isInvisible) return false

        // 2. No se puede pegar a sí mismo con un área
        if (atacante.uniqueId == victima.uniqueId) return false

        // 3. Revisión de Fuego Amigo basada en la sesión del jugador atacado
        val session = plugin.sessionManager.getSession(victima) ?: return false

        // Si el atacante no está en la misma sesión, se deniega (Cruces entre arenas)
        if (plugin.sessionManager.getSession(atacante) != session) return false

        val atacanteEsAsesino = session.esAsesino(atacante.uniqueId)
        val victimaEsAsesino = session.esAsesino(victima.uniqueId)

        // Si el modo es Assassin PvP, todos se pueden pegar
        if (session.currentMode == liric.mistaken.game.enums.MistakenMode.ASSASSIN_PVP) {
            return true
        }

        // Si es el modo normal y ambos son asesinos, no hay fuego amigo
        if (atacanteEsAsesino && victimaEsAsesino) {
            return false
        }

        // En cualquier otro caso (Asesino vs Superviviente) es válido
        return true
    }

    // --- MÉTODOS ABSTRACTOS ---
    abstract fun equipar(player: Player)
    abstract fun usarHabilidad(player: Player, slot: Int)
    abstract fun mostrarTrail(player: Player)
    open fun mostrarTrailFisico(player: Player) {}
}
