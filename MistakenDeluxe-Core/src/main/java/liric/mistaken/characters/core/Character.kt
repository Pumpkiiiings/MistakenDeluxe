package liric.mistaken.characters.core

import org.bukkit.entity.Entity
import java.util.concurrent.ConcurrentHashMap

/**
 * Representa un personaje en el juego (Killer, Survivor, NPC).
 * Es un contenedor puro (Registry) que delega la lógica a sus componentes.
 * 
 * @property entity La entidad de Bukkit asociada a este personaje (Player, Zombie, etc.)
 */
class Character(val entity: Entity) {
    
    private val components: MutableMap<Class<out CharacterComponent>, CharacterComponent> = ConcurrentHashMap()

    /**
     * Añade un componente al personaje. Reemplaza si ya existía uno del mismo tipo.
     */
    fun <T : CharacterComponent> addComponent(type: Class<T>, component: T) {
        
        components[type]?.onDisable()
        
        components[type] = component
        component.onEnable(this)
    }

    /**
     * Obtiene un componente por su clase.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : CharacterComponent> getComponent(type: Class<T>): T? {
        return components[type] as T?
    }

    /**
     * Comprueba si el personaje tiene un componente específico.
     */
    fun hasComponent(type: Class<out CharacterComponent>): Boolean {
        return components.containsKey(type)
    }

    /**
     * Remueve un componente.
     */
    fun removeComponent(type: Class<out CharacterComponent>) {
        components.remove(type)?.onDisable()
    }

    /**
     * Llama al método tick() de todos los componentes.
     */
    fun tick() {
        for (component in components.values) {
            component.tick()
        }
    }

    /**
     * Destruye el personaje y todos sus componentes.
     */
    fun destroy() {
        for (component in components.values) {
            component.onDisable()
        }
        components.clear()
    }
}
