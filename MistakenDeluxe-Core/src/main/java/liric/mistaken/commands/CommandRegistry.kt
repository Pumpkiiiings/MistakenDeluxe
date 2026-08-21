package liric.mistaken.commands

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import liric.mistaken.Mistaken
import liric.mistaken.commands.admin.ArenaCommand
import liric.mistaken.commands.other.DataCommand
import liric.mistaken.commands.admin.SetLobbyCommand
import liric.mistaken.commands.other.UnlinkCommand
import liric.mistaken.commands.debug.CinematicCommand
import liric.mistaken.commands.debug.HitboxCommand
import liric.mistaken.commands.debug.MistakenDebugCommand
import liric.mistaken.commands.game.EspectearCommand
import liric.mistaken.commands.game.JoinCommand
import liric.mistaken.commands.game.LeaveCommand
import liric.mistaken.commands.game.MistakenCommand
import liric.mistaken.commands.game.VoteCommand
import liric.mistaken.commands.other.LinkCommand
import pumpking.lib.color.ColorTranslator

class CommandRegistry(private val plugin: Mistaken) {

    fun registerAll() {
        val manager = plugin.lifecycleManager

        // ðŸ”¥ FIX: Declaramos los tipos explÃ­citos para ayudar al compilador
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event: ReloadableRegistrarEvent<Commands> ->
            val registrar = event.registrar()

            // --- GRUPO A: COMANDOS "PRO" (Nodos de Brigadier) ---

            // âš ï¸ IMPORTANTE: Si alguno de estos comandos falla al compilar despuÃ©s de bajar a 1.9.24,
            // asegÃºrate de que el mÃ©todo `.get(plugin)` de tus comandos devuelva un LiteralCommandNode.
            // Si devuelven un LiteralArgumentBuilder, debes agregar .build() al final de tu mÃ©todo get() en cada clase.

            registrar.register(JoinCommand.get(plugin), "Unirse a una partida", listOf("join", "play"))
            registrar.register(VoteCommand.get(plugin), "Votar por el mapa", listOf("votar"))
            registrar.register(DataCommand.get(plugin), "Migrar datos de YML a MySQL", emptyList())
            registrar.register(LeaveCommand.get(plugin), "Salir de la partida actual", listOf("leave", "quit"))
            registrar.register(UnlinkCommand.get(plugin), "Desvincular Discord", emptyList())
            registrar.register(SetLobbyCommand.get(plugin), "Establecer el spawn del lobby", emptyList())
            registrar.register(LinkCommand.get(plugin), "Vincular Discord", emptyList())
            registrar.register(MistakenDebugCommand.get(plugin), "Comando de pruebas", listOf("mdebug"))
            registrar.register(CinematicCommand.get(plugin), "Reproducir cinemÃ¡ticas", listOf("cine", "cinematica"))
            registrar.register(HitboxCommand.get(plugin), "Alternar el visor de hitboxes 3D", listOf("hitboxes"))

            // --- GRUPO B: COMANDOS BÃSICOS (Classes BasicCommand) ---
            registrar.register("mistaken", "Comando principal", listOf("ms", "mt"), MistakenCommand(plugin))
            registrar.register("arena", "GestiÃ³n de arenas", ArenaCommand(plugin))
            registrar.register("espectear", "Entrar al modo espectador", listOf("spectate"), EspectearCommand(plugin))
        }

        plugin.componentLogger.info(pumpking.lib.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Commands registered successfully.</gray>"))
    }
}
