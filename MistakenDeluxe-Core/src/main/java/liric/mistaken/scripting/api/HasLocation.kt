package liric.mistaken.scripting.api

import org.bukkit.Location

/**
 * Interfaz marcadora para cualquier wrapper del DSL que posea una ubicación en el world.
 * Implementada por BukkitPlayerAdapter (location = player.location) y
 * BukkitLocationAdapter (location = la propia location envuelta).
 *
 * Usada por funciones globales como sound() para operar polimórficamente
 * sobre cualquier objeto con sentido de ubicación, sin necesidad de type-switch.
 * Cualquier wrapper futuro (ScriptVictim, ScriptNPC, etc.) que implemente esta
 * interfaz funciona automáticamente con todas las funciones basadas en ubicación.
 *
 * NOTA: Solo accesible internamente por el plugin, NO expuesto a Lua.
 */
interface HasLocation {
    fun bukkitLocation(): Location
}
