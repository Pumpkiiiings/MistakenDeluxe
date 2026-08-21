package liric.mistaken.api.managers

import org.bukkit.Location

interface IArena {
    val name: String
    val slimeWorldName: String?
    val killerSpawn: Location?
    val timeMode: String
    
    val survivorSpawns: List<Location>
    val generators: List<Location>
}
