package liric.mistaken.characters.core

import org.bukkit.entity.Entity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestiona el ciclo de vida de todos los Characters activos.
 */
class CharacterManager {
    
    private val charactersByEntity = ConcurrentHashMap<UUID, Character>()

    /**
     * Crea un nuevo Character asociado a una entidad y lo registra.
     * Si la entidad ya tiene un Character, retorna el existente.
     */
    fun create(entity: Entity): Character {
        return charactersByEntity.computeIfAbsent(entity.uniqueId) {
            Character(entity)
        }
    }

    /**
     * Obtiene el Character asociado a una entidad, si existe.
     */
    fun get(entity: Entity): Character? {
        return charactersByEntity[entity.uniqueId]
    }
    
    /**
     * Obtiene el Character asociado a un UUID, si existe.
     */
    fun get(uuid: UUID): Character? {
        return charactersByEntity[uuid]
    }

    /**
     * Elimina el Character asociado a la entidad y llama a su método destroy().
     */
    fun remove(entity: Entity) {
        charactersByEntity.remove(entity.uniqueId)?.destroy()
    }

    /**
     * Llama a tick() en todos los Characters registrados.
     * Esto debería ser invocado periódicamente por un BukkitRunnable (ej. cada tick).
     */
    fun tickAll() {
        for (character in charactersByEntity.values) {
            character.tick()
        }
    }

    /**
     * Limpia y destruye todos los characters.
     */
    fun clear() {
        for (character in charactersByEntity.values) {
            character.destroy()
        }
        charactersByEntity.clear()
    }
}
