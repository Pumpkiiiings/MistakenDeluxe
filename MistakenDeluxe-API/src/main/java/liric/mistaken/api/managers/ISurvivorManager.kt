package liric.mistaken.api.managers

import liric.mistaken.api.roles.ISurvivor

interface ISurvivorManager {
    val catalogo: Map<String, ISurvivor>
    
    fun registerClass(role: ISurvivor)
    fun getClassById(id: String?): ISurvivor?
}
