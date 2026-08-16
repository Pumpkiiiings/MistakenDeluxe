package liric.mistaken.utils.misc

import org.bukkit.entity.Entity
import org.bukkit.entity.TextDisplay

object EntityUtils {
    /**
     * Comprueba si una entidad es puramente visual de la interfaz del jugador (ej. Nametags).
     * Se usa para ignorarlas al verificar si un jugador esta cargando a alguien, etc.
     */
    fun isHUDEntity(entity: Entity?): Boolean {
        if (entity == null) return false
        return entity is TextDisplay
    }
}
