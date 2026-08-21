package liric.mistaken.game

import liric.mistaken.game.enums.MistakenMode

data class PrivateGameSettings(
    var gameDuration: Int? = null,
    var minPlayers: Int? = null,
    var maxPlayers: Int? = null,
    var forcedMap: String? = null,
    var forcedMode: MistakenMode? = null,
    var speedMultiplier: Int? = null, 
    var jumpMultiplier: Int? = null,
    var blindnessRole: String? = null, 
    var heartbeatsEnabled: Boolean = true,
    var killerHealth: Double? = null,
    var survivorHealth: Double? = null,
    var glowingEnabled: Boolean = false,
    val allowedKillers: MutableList<String> = mutableListOf(),
    val allowedSurvivors: MutableList<String> = mutableListOf(),
    val disabledClasses: MutableSet<String> = mutableSetOf()
)
