package liric.mistaken.game.modes

import liric.mistaken.Mistaken
import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.MistakenMode
import liric.mistaken.game.modes.handlers.*

object ModeHandlerFactory {
    fun create(plugin: Mistaken, session: GameSession, mode: MistakenMode): ModeHandler {
        return when (mode) {
            MistakenMode.CLASSIC -> ClassicModeHandler(plugin, session)
            MistakenMode.ONE_BOUNCE -> OneBounceModeHandler(plugin, session)
            MistakenMode.DOUBLE_KILLER -> DoubleKillerModeHandler(plugin, session)
            MistakenMode.HIDE_AND_SEEK -> HideAndSeekModeHandler(plugin, session)
            MistakenMode.FREEZE_TAG -> FreezeTagModeHandler(plugin, session)
            MistakenMode.INFECTION -> InfectionModeHandler(plugin, session)
            MistakenMode.INITIALIZES -> InitializesModeHandler(plugin, session)
        }
    }
}
