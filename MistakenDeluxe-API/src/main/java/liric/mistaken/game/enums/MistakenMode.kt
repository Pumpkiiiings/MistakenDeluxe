package liric.mistaken.game.enums


enum class MistakenMode(val isTagMode: Boolean) {
    CLASSIC(false),        //  Killer vs Todos
    DOUBLE_KILLER(false),  //  Killers vs Todos
    ONE_BOUNCE(false),     //  Survivor vs Todos Killers
    FREEZE_TAG(true),      // Los killers congelan, los humanos rescatan
    INFECTION(false),      // Survivors muertos se convierten en killers
    HIDE_AND_SEEK(false),  // Escondite: Killer inmovilizado 1 minuto
    INITIALIZES(true);     // Aparece geoffrey.
}
