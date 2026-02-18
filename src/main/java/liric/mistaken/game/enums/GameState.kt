package liric.mistaken.game.enums

/**
 * [LIRIC-MISTAKEN 2.0]
 * GameState: Define el estado actual del servidor.
 */
enum class GameState(val isJoinable: Boolean) {
    LOBBY(true),      // Jugadores pueden entrar y esperar
    VOTING(true),     // Jugadores pueden entrar y votar mapa
    STARTING(false),   // El juego está cargando (teleports, etc)
    INGAME(false),     // Partida en curso
    ENDING(false);    // Limpiando mapa y enviando al lobby
}
