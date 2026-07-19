package liric.mistaken.roles.killers

import liric.mistaken.Mistaken

abstract class CoreKiller(id: String, nombre: String) : Killer(id, nombre) {
    protected val plugin: Mistaken
        get() = Mistaken.instance
}
