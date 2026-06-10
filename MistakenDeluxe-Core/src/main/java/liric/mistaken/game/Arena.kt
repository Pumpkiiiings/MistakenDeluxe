package liric.mistaken.game

import org.bukkit.Location

/**
 * [LIRIC-MISTAKEN 2.0]
 * MÓDULO: Arena
 * DESCRIPCIÓN: Modelo de datos optimizado para las arenas.
 */
class Arena(val name: String) {

    var slimeWorldName: String? = null
    var asesinoSpawn: Location? = null

    // Usamos MutableList de Kotlin (que compila a ArrayList en la JVM)
    // val asegura que la referencia a la lista no cambie, pero el contenido es mutable.
    val survivorSpawns: MutableList<Location> = mutableListOf()
    val generators: MutableList<Location> = mutableListOf()

    /**
     * Añade un punto de spawn para supervivientes de forma segura.
     * O(n) check para evitar duplicados en tiempo de configuración.
     */
    fun addSurvivorSpawn(loc: Location) {
        if (loc !in survivorSpawns) {
            survivorSpawns.add(loc)
        }
    }

    /**
     * Añade un generador a la lista.
     */
    fun addGenerator(loc: Location) {
        if (loc !in generators) {
            generators.add(loc)
        }
    }

    /**
     * Alias para compatibilidad con sistemas que requieran una lista inmutable
     * o para legibilidad en el GameManager.
     */
    fun getGeneratorLocations(): List<Location> = generators

    /**
     * Limpia las configuraciones de la arena si es necesario.
     */
    fun clearConfig() {
        survivorSpawns.clear()
        generators.clear()
        asesinoSpawn = null
    }
}

