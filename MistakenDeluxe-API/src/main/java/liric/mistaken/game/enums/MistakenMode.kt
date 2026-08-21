package liric.mistaken.game.enums


enum class MistakenMode(val isTagMode: Boolean) {
    CLASSIC(false),        
    DOUBLE_KILLER(false),  
    ONE_BOUNCE(false),     
    FREEZE_TAG(true),      
    INFECTION(false),      
    HIDE_AND_SEEK(false),  
    INITIALIZES(true);     
}
