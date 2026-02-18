package liric.mistaken.game.enums

/**
 * [LIRIC-MISTAKEN 2.0]
 * MistakenMode: Modos de juego con sus reglas específicas.
 */
enum class MistakenMode(val isTagMode: Boolean) {
    CLASSIC(false),        // 1 Asesino vs Todos
    DOUBLE_KILLER(false),  // 2 Asesinos vs Todos
    ONE_BOUNCE(false),     // 1 Superviviente vs Todos Asesinos
    FREEZE_TAG(true);      // Los asesinos congelan, los humanos rescatan
}
