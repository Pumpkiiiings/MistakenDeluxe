package liric.mistaken.game.enums


enum class MistakenMode(val isTagMode: Boolean) {
    CLASSIC(false),        //  Killer vs Todos
    DOUBLE_KILLER(false),  //  Asesinos vs Todos
    ONE_BOUNCE(false),     //  Survivor vs Todos Asesinos
    FREEZE_TAG(true),      // Los asesinos congelan, los humanos rescatan
    INFECTION(false),      // Survivors muertos se convierten en asesinos
    HIDE_AND_SEEK(false),  // Escondite: Asesino inmovilizado 1 minuto
    INITIALIZES(true);     // Aparece geoffrey.
}
