package liric.mistaken.game

import org.bukkit.Location

import liric.mistaken.api.managers.IArena

class Arena(override val name: String) : IArena {

    override var slimeWorldName: String? = null
    override var killerSpawn: Location? = null
    override var timeMode: String = "dynamic"

    
    
    override val survivorSpawns: MutableList<Location> = mutableListOf()
    override val generators: MutableList<Location> = mutableListOf()

    /**
     * Añade un punto de spawn para survivors de forma segura.
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
     * Limpia las configurations de la arena si es necesario.
     */
    fun clearConfig() {
        survivorSpawns.clear()
        generators.clear()
        killerSpawn = null
    }
}
