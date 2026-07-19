package liric.mistaken.game.managers.engine.visibility

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove
import liric.mistaken.Mistaken
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * VisibilityManager: Manejador de visibilidad vía PacketEvents.
 * Reemplaza la inestabilidad de Bukkit enviando/destruyendo entidades directamente a nivel de red.
 */
class VisibilityManager(private val plugin: Mistaken) {

    // Target UUID -> Set of Viewers UUIDs who CANNOT see them
    private val hiddenFrom = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    
    // Target UUID -> Set of Viewers UUIDs who CAN see them (if empty, applies to no one, if used, HIDES from everyone else)
    private val visibleOnlyTo = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    /**
     * Oculta la entidad al viewer.
     */
    fun hideEntity(target: Entity, viewer: Player) {
        if (target.uniqueId == viewer.uniqueId) return

        hiddenFrom.computeIfAbsent(target.uniqueId) { ConcurrentHashMap.newKeySet() }.add(viewer.uniqueId)
        
        // Si el target es jugador, remover del TAB también
        if (target is Player) {
            val infoRemove = WrapperPlayServerPlayerInfoRemove(target.uniqueId)
            PacketEvents.getAPI().playerManager.sendPacket(viewer, infoRemove)
        }

        // Destruir entidad instantáneamente para el cliente
        val destroyPacket = WrapperPlayServerDestroyEntities(target.entityId)
        PacketEvents.getAPI().playerManager.sendPacket(viewer, destroyPacket)
    }

    /**
     * Oculta el target al viewer (Alias para compatibilidad).
     */
    fun hidePlayer(target: Player, viewer: Player) = hideEntity(target, viewer)

    /**
     * Oculta la entidad de TODOS excepto de los viewers especificados.
     * Útil para Displays Client-Side que spawnean en Bukkit.
     */
    fun hideFromAllExcept(target: Entity, viewers: List<Player>) {
        val viewerIds = ConcurrentHashMap.newKeySet<UUID>()
        viewers.forEach { viewerIds.add(it.uniqueId) }
        visibleOnlyTo[target.uniqueId] = viewerIds
        
        // Destruimos la entidad para los que no están en la lista
        org.bukkit.Bukkit.getOnlinePlayers().forEach { online ->
            if (!viewerIds.contains(online.uniqueId)) {
                val destroyPacket = WrapperPlayServerDestroyEntities(target.entityId)
                PacketEvents.getAPI().playerManager.sendPacket(online, destroyPacket)
            }
        }
    }

    /**
     * Muestra el target al viewer.
     */
    fun showPlayer(target: Player, viewer: Player) {
        if (target.uniqueId == viewer.uniqueId) return

        val viewers = hiddenFrom[target.uniqueId]
        if (viewers != null) {
            viewers.remove(viewer.uniqueId)
            if (viewers.isEmpty()) {
                hiddenFrom.remove(target.uniqueId)
            }
        }

        // Si estaba en visibleOnlyTo, agregarlo
        val onlyViewers = visibleOnlyTo[target.uniqueId]
        if (onlyViewers != null) {
            onlyViewers.add(viewer.uniqueId)
        }

        // Forzar re-envío con Bukkit
        viewer.hidePlayer(plugin, target)
        viewer.showPlayer(plugin, target)
    }

    /**
     * Verifica si el target está oculto para el viewer.
     */
    fun isHidden(targetUuid: UUID, viewerUuid: UUID): Boolean {
        // 1. Verificar si tiene Whitelist (visibleOnlyTo)
        val whitelist = visibleOnlyTo[targetUuid]
        if (whitelist != null && !whitelist.contains(viewerUuid)) {
            return true // Oculto porque no está en la lista de los únicos que pueden verlo
        }

        // 2. Verificar si está en Blacklist (hiddenFrom)
        return hiddenFrom[targetUuid]?.contains(viewerUuid) == true
    }
    
    /**
     * Busca la entidad por EntityID y verifica visibilidad.
     * O(N) si no es jugador, pero en Paper/Spigot moderno podemos optimizarlo.
     * Para mantenerlo genérico, iteramos todos los mundos (o asumimos que si es un jugador usamos getOnlinePlayers).
     */
    fun isHidden(targetEntityId: Int, viewerUuid: UUID): Boolean {
        // Optimización 1: ¿Es un jugador?
        val online = org.bukkit.Bukkit.getOnlinePlayers().find { it.entityId == targetEntityId }
        if (online != null) return isHidden(online.uniqueId, viewerUuid)
        
        // Si no es jugador, necesitamos saber su UUID. Como este chequeo ocurre millones de veces, 
        // no podemos hacer un Bukkit.getWorlds().forEach { it.entities }.
        // Solución: PacketVisibilityListener pasará el UUID si lo conoce, pero `WrapperPlayServerSpawnEntity` 
        // tiene un `uuid`. En PacketVisibilityListener debemos usar el UUID del paquete!
        return false // Fallback
    }

    /**
     * Limpieza cuando un jugador o entidad se desconecta/muere.
     */
    fun removePlayer(uuid: UUID) {
        hiddenFrom.remove(uuid)
        visibleOnlyTo.remove(uuid)
        hiddenFrom.values.forEach { it.remove(uuid) }
        visibleOnlyTo.values.forEach { it.remove(uuid) }
    }
    
    fun removeEntity(uuid: UUID) {
        hiddenFrom.remove(uuid)
        visibleOnlyTo.remove(uuid)
    }
}
