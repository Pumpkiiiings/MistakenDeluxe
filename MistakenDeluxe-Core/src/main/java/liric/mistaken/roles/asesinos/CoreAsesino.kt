package liric.mistaken.roles.asesinos

import liric.mistaken.Mistaken

abstract class CoreAsesino(id: String, nombre: String) : Asesino(id, nombre) {
    protected val plugin: Mistaken
        get() = Mistaken.instance
}
