package liric.mistaken.game.enums

/**
 * [LIRIC-MISTAKEN 2.0]
 * MistakenMode: Modos de juego con sus reglas específicas.
 */
enum class MistakenMode(val isTagMode: Boolean) {
    CLASSIC(false),        //  Asesino vs Todos
    DOUBLE_KILLER(false),  //  Asesinos vs Todos
    ONE_BOUNCE(false),     //  Superviviente vs Todos Asesinos
    FREEZE_TAG(true),     // Los asesinos congelan, los humanos rescatan
    INFECTION(false),      // Supervivientes muertos se convierten en asesinos
    INITIALIZES(true);   // Aparece geoffrey.
}
