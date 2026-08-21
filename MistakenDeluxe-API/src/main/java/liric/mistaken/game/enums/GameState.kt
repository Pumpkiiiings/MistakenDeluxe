package liric.mistaken.game.enums


enum class GameState(val isJoinable: Boolean) {
    LOBBY(true),      // Esperando players mínimos
    BREAK(true),      // 🔥 Descanso post-partida (espera antes de votar)
    VOTING(true),     // Elección de mapa por los players
    STARTING(false),  // Secuencia de inicio (revelación de roles/modo)
    INGAME(false),    // Partida en curso
    ENDING(false);    // Celebración de victoria y limpieza
}
