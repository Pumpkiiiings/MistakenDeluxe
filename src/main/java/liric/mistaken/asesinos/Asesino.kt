package liric.mistaken.asesinos

import kotlinx.coroutines.Job
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Asesino: Clase base polimórfica ultra-optimizada.
 * Adaptada para Paper 1.21.4+ con reseteo de atributos moderno y gestión de tareas.
 */
abstract class Asesino(val id: String, val nombre: String) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    // Cooldowns: UUID_Slot -> Timestamp (ms)
    private val cooldowns = ConcurrentHashMap<String, Long>()

    // Rastrero de tareas asíncronas (Jobs) para limpieza automática
    protected val activeJobs = ConcurrentHashMap.newKeySet<Job>()

    // Rastrero de tareas de Bukkit para limpieza completa (Schedulers)
    protected val activeTasks = ConcurrentHashMap.newKeySet<BukkitTask>()

    /**
     * Verifica si una habilidad está en cooldown y envía feedback visual.
     * @return true si está en cooldown (no se puede usar), false si está lista.
     */
    fun checkCooldown(player: Player, slot: Int): Boolean {
        val config = plugin.configManager.getAsesinos()
        val pathBase = "asesinos.$id.items.habilidad$slot"

        val cooldownSecs = config.getInt("${pathBase}_cooldown", 0)
        val nombreHabilidad = config.getString("${pathBase}_nombre", "Habilidad $slot") ?: "Habilidad $slot"

        if (cooldownSecs <= 0) return false

        val key = "${player.uniqueId}_$slot"
        val now = System.currentTimeMillis()
        val expireTime = cooldowns.getOrDefault(key, 0L)

        if (now < expireTime) {
            val remaining = (expireTime - now) / 1000.0
            player.sendActionBar(mm.deserialize(
                "<red><bold>!!</bold> <yellow>$nombreHabilidad</yellow> disponible en: <white>${"%.1f".format(remaining)}s</white>"
            ))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f)
            return true
        }

        // Feedback de uso exitoso
        player.sendMessage(mm.deserialize("<green><bold>✔</bold> Has utilizado <white>$nombreHabilidad</white>"))
        cooldowns[key] = now + (cooldownSecs * 1000L)
        return false
    }

    /**
     * Registra un Job de corrutina para ser cancelado automáticamente al terminar la partida.
     */
    protected fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    /**
     * Registra una tarea de Bukkit (Scheduler) para ser cancelada automáticamente.
     * IMPORTANTE: Debes pasar el resultado de .runTaskTimer() o .runTaskLater()
     */
    protected fun trackTask(task: BukkitTask) {
        activeTasks.add(task)
    }

    /**
     * Reproduce el sonido configurado para una habilidad.
     */
    fun reproducirEfectosHabilidad(player: Player, slot: Int) {
        val config = plugin.configManager.getAsesinos()
        val sonidoName = config.getString("asesinos.$id.items.habilidad${slot}_sonido") ?: return

        try {
            // Soporte para sonidos nativos de Bukkit
            val sound = Sound.valueOf(sonidoName.uppercase())
            player.world.playSound(player.location, sound, 1.0f, 0.7f)
        } catch (e: IllegalArgumentException) {
            // Si no es un sonido nativo, intentamos reproducirlo como sonido de recurso (custom)
            player.playSound(player.location, sonidoName, 1.0f, 0.7f)
        }
    }

    /**
     * Limpieza profunda del asesino.
     * Cancela todas las corrutinas activas y resetea al jugador físicamente.
     */
    open fun cleanup(player: Player?) {
        // 1. Cancelar todas las tareas (Coroutines y BukkitTasks)
        activeJobs.forEach { if (it.isActive) it.cancel() }
        activeJobs.clear()

        // Cancelación de tareas de Bukkit registradas (Schedulers)
        activeTasks.forEach { it.cancel() }
        activeTasks.clear()

        player?.let { p ->
            if (p.isOnline) {
                // 2. Reset de inventario y equipo
                p.inventory.clear()
                p.inventory.armorContents = arrayOfNulls(4)

                // 3. Limpiar todos los efectos de poción de forma segura
                // Usamos toList() para evitar ConcurrentModificationException en 1.21.4
                p.activePotionEffects.toList().forEach { effect ->
                    p.removePotionEffect(effect.type)
                }

                // 4. 🔥 FIX ESPECTADOR Y ESTADOS FÍSICOS
                p.isSwimming = false // Evita el bug de la cámara "nadando"
                p.isGliding = false  // Evita el bug de las elitras
                p.isGlowing = false
                p.inventory.heldItemSlot = 0

                // 💡 LA CLAVE: Solo quitamos el vuelo si el jugador NO es espectador.
                // Si el juego terminó y es espectador, lo dejamos volar para que no caiga al vacío.
                if (p.gameMode != org.bukkit.GameMode.SPECTATOR) {
                    p.allowFlight = false
                    p.isFlying = false
                }

                // 5. Reset de atributos de la 1.21.4 (Crucial para el rendimiento)
                resetAttributes(p)

                // 6. Limpiar sus cooldowns para liberar RAM
                val prefix = p.uniqueId.toString()
                cooldowns.keys.removeIf { it.startsWith(prefix) }

                // 7. Limpieza de PDC (Persistent Data Container)
                p.persistentDataContainer.remove(plugin.assassinKey)

                p.updateInventory()
            }
        }
    }

    /**
     * Resetea los atributos del jugador a sus valores originales de la 1.21.4.
     */
    private fun resetAttributes(player: Player) {
        // Lista extendida de atributos modernos para evitar bugs de velocidad/vida
        val attributes = listOf(
            Attribute.MAX_HEALTH,
            Attribute.MOVEMENT_SPEED,
            Attribute.ATTACK_DAMAGE,
            Attribute.ATTACK_SPEED,
            Attribute.KNOCKBACK_RESISTANCE,
            Attribute.SCALE,            // 1.20.5+
            Attribute.STEP_HEIGHT,      // 1.21.4+
            Attribute.GRAVITY,          // 1.21.4+
            Attribute.JUMP_STRENGTH     // 1.21.4+
        )

        attributes.forEach { attr ->
            player.getAttribute(attr)?.let { instance ->
                // Eliminar todos los modificadores (de otros plugins o habilidades)
                instance.modifiers.forEach { instance.removeModifier(it) }
                // Resetear al valor base por defecto del servidor
                instance.baseValue = instance.defaultValue
            }
        }
        // Curar al jugador tras resetear la vida máxima
        player.health = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
    }

    /**
     * Utilidad de mensajería rápida MiniMessage con placeholders.
     */
    protected fun msg(player: Player, path: String, vararg placeholders: TagResolver) {
        val message = plugin.messageConfig.getMessage(player, path, *placeholders)
        player.sendMessage(message)
    }

    // --- MÉTODOS ABSTRACTOS ---

    abstract fun equipar(player: Player)
    abstract fun usarHabilidad(player: Player, slot: Int)
    abstract fun mostrarTrail(player: Player)

    /**
     * Trail físico (bloques, fuego, etc). Se ejecuta en el Main Thread.
     */
    open fun mostrarTrailFisico(player: Player) {}

    // --- GETTERS ---
    fun getRemainingCooldown(player: Player, slot: Int): Long {
        val key = "${player.uniqueId}_$slot"
        return (cooldowns.getOrDefault(key, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    }
}
