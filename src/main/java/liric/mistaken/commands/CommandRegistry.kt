package liric.mistaken.commands

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import liric.mistaken.Mistaken

class CommandRegistry(private val plugin: Mistaken) {

    fun registerAll() {
        val manager = plugin.lifecycleManager

        // 🔥 FIX: Declaramos los tipos explícitos para ayudar al compilador
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event: ReloadableRegistrarEvent<Commands> ->
            val registrar = event.registrar()

            // --- GRUPO A: COMANDOS "PRO" (Nodos de Brigadier) ---

            // ⚠️ IMPORTANTE: Si alguno de estos comandos falla al compilar después de bajar a 1.9.24,
            // asegúrate de que el método `.get(plugin)` de tus comandos devuelva un LiteralCommandNode.
            // Si devuelven un LiteralArgumentBuilder, debes agregar .build() al final de tu método get() en cada clase.

            registrar.register(VoteCommand.get(plugin), "Votar por el mapa", listOf("votar"))
            registrar.register(UnlinkCommand.get(plugin), "Desvincular Discord", emptyList())
            registrar.register(SetLobbyCommand.get(plugin), "Establecer el spawn del lobby", emptyList())
            registrar.register(LinkCommand.get(plugin), "Vincular Discord", emptyList())
            registrar.register(MistakenTestCommand.get(plugin), "Comando de pruebas", listOf("mtest"))
            registrar.register(CinematicaCommand.get(plugin), "Reproducir cinemáticas", listOf("cine"))
            registrar.register(HitboxCommand.get(plugin), "Alternar el visor de hitboxes 3D", listOf("hitboxes"))

            // --- GRUPO B: COMANDOS BÁSICOS (Clases BasicCommand) ---
            registrar.register("mistaken", "Comando principal", listOf("ms", "mt"), MistakenCommand(plugin))
            registrar.register("arena", "Gestión de arenas", ArenaCommand(plugin))
            registrar.register("espectear", "Entrar al modo espectador", listOf("spectate"), EspectearCommand(plugin))
        }

        plugin.componentLogger.info(plugin.mm.deserialize("<green>[CommandRegistry] Comandos registrados correctamente.</green>"))
    }
}
