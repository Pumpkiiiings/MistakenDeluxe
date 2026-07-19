package liric.mistaken.api.managers

import liric.mistaken.roles.killers.Killer

interface IKillerManager {
    fun registerClass(asesino: Killer)
    fun getClassById(id: String?): Killer?
    val catalogo: Map<String, Killer>
}
