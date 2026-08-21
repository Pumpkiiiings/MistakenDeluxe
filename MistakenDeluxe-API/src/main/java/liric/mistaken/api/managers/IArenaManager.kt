package liric.mistaken.api.managers

interface IArenaManager {
    val defaultArena: IArena?
    
    fun getArena(name: String): IArena?
    fun getArenas(): List<IArena>
}
