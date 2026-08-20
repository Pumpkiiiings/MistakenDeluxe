package liric.mistaken.roles.killers.triggers

enum class InputTrigger {
    SLOT_1,
    SLOT_2,
    SLOT_3,
    SLOT_4,
    SWAP_HANDS,
    DROP_ITEM,
    SNEAK_TOGGLE,
    CHAT_MESSAGE,
    ATTACK;

    companion object {
        fun fromString(str: String): InputTrigger? {
            return try {
                valueOf(str.uppercase())
            } catch (e: Exception) {
                null
            }
        }
    }
}
