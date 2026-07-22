package liric.mistaken.game

import liric.mistaken.game.enums.MistakenMode

data class PrivateGameSettings(
    var gameDuration: Int? = null,
    var minPlayers: Int? = null,
    var maxPlayers: Int? = null,
    var forcedMap: String? = null,
    var forcedMode: MistakenMode? = null,
    var speedMultiplier: Int? = null, // Nivel de poción (0 = nivel 1)
    var jumpMultiplier: Int? = null,
    var blindnessRole: String? = null, // "KILLER", "SURVIVOR", "NONE"
    var heartbeatsEnabled: Boolean = true,
    var killerHealth: Double? = null,
    var survivorHealth: Double? = null,
    var glowingEnabled: Boolean = false,
    val allowedKillers: MutableList<String> = mutableListOf(),
    val allowedSurvivors: MutableList<String> = mutableListOf()
)
