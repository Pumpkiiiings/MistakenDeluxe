package liric.mistaken.game.enums


enum class GameState(val isJoinable: Boolean) {
    LOBBY(true),      
    BREAK(true),      
    VOTING(true),     
    STARTING(false),  
    INGAME(false),    
    ENDING(false);    
}
