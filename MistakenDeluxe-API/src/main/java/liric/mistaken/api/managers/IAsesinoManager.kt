package liric.mistaken.api.managers

import liric.mistaken.roles.asesinos.Asesino

interface IAsesinoManager {
    fun registrarClase(asesino: Asesino)
    fun getClasePorId(id: String?): Asesino?
    val catalogo: Map<String, Asesino>
}
