package liric.mistaken.game.enums

/**
 * [LIRIC-MISTAKEN 2.0]
 * GameState: Define el estado actual del servidor.
 */
enum class GameState(val isJoinable: Boolean) {
    LOBBY(true),      // Esperando jugadores mínimos
    BREAK(true),      // 🔥 Descanso post-partida (espera antes de votar)
    VOTING(true),     // Elección de mapa por los jugadores
    STARTING(false),  // Secuencia de inicio (revelación de roles/modo)
    INGAME(false),    // Partida en curso
    ENDING(false);    // Celebración de victoria y limpieza
}
