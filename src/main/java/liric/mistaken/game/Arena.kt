package liric.mistaken.game

import org.bukkit.Location

/**
 * [LIRIC-MISTAKEN 2.0]
 * MÓDULO: Arena
 * DESCRIPCIÓN: Modelo de datos.
 */
class Arena(val name: String) {

    var slimeWorldName: String? = null
    var asesinoSpawn: Location? = null

    // Usamos ArrayList directamente para evitar overhead de interfaces extra
    val survivorSpawns = ArrayList<Location>()
    val generators = ArrayList<Location>()

    /**
     * Añade un spawn solo si no existe ya (evita duplicados exactos).
     */
    fun addSurvivorSpawn(loc: Location) {
        if (!survivorSpawns.contains(loc)) {
            survivorSpawns.add(loc)
        }
    }

    fun addGenerator(loc: Location) {
        if (!generators.contains(loc)) {
            generators.add(loc)
        }
    }

    fun getGeneratorLocations(): List<Location> = generators

    fun clearConfig() {
        survivorSpawns.clear()
        generators.clear()
        asesinoSpawn = null
    }
}
