package liric.mistaken.roles.shared

import liric.mistaken.Mistaken
import liric.mistaken.api.roles.GameRole
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import pumpking.lib.color.ColorTranslator

/**
 * [LIRIC-MISTAKEN 2.0]
 * Clase base para manejar los roles (Killers y Survivors).
 * Centraliza la inicialización, limpieza e iteración segura.
 */
abstract class AbstractRoleManager<T : GameRole>(protected val plugin: Mistaken) {

    protected val activeRoles = ConcurrentHashMap<UUID, T>()
    protected val availableClasses = ConcurrentHashMap<String, T>()

    val catalogo: Map<String, T> get() = availableClasses

    open fun getClassById(id: String?): T? {
        return id?.lowercase()?.let { availableClasses[it] }
    }

    abstract fun registerClass(role: T)

    /**
     * Limpia la data base de un rol del jugador.
     */
    protected open fun removeRoleLogic(uuid: UUID, player: Player?) {
        val role = activeRoles.remove(uuid) ?: return

        if (player != null && player.isOnline) {
            // Paper entity scheduler
            player.scheduler.run(plugin, { _ ->
                role.cleanup(player)
                onRoleRemoved(player)
            }, null)
        } else {
            role.cleanup(null)
        }
    }

    /**
     * Hook para lógicas adicionales luego de limpiar el rol base.
     */
    protected open fun onRoleRemoved(player: Player) {}

    open fun cleanAll() {
        val iterator = activeRoles.keys.iterator()
        while (iterator.hasNext()) {
            val uuid = iterator.next()
            val player = Bukkit.getPlayer(uuid)
            removeRoleLogic(uuid, player)
            iterator.remove()
        }
    }

    open fun shutdown() {
        cleanAll()
    }
}
