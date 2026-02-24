package liric.mistaken.commands

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import liric.mistaken.Mistaken

/**
 * [LIRIC-MISTAKEN 2.0]
 * CommandRegistry: El cerebro del registro de comandos.
 */
class CommandRegistry(private val plugin: Mistaken) {

    fun registerAll() {
        val manager = plugin.lifecycleManager

        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()

            // --- GRUPO A: COMANDOS "PRO" (Nodos de Brigadier) ---
            // Estos NO llevan '()' al final del nombre de la clase

            registrar.register(
                VoteCommand.get(plugin),
                "Votar por el mapa",
                listOf("votar")
            )

            registrar.register(
                UnlinkCommand.get(plugin),
                "Desvincular Discord",
                emptyList()
            )

            registrar.register(
                SetLobbyCommand.get(plugin),
                "Establecer el spawn del lobby",
                emptyList()
            )

            // --- GRUPO B: COMANDOS BÁSICOS (Clases BasicCommand) ---
            // Estos SÍ llevan '()' porque son instancias de clase

            registrar.register(
                "mistaken",
                "Comando principal",
                listOf("ms", "mt"),
                MistakenCommand(plugin)
            )

            registrar.register(
                "arena",
                "Gestión de arenas",
                ArenaCommand(plugin)
            )

            registrar.register(
                LinkCommand.get(plugin),
                "Vincular Discord",
                emptyList()
            )

            // Adentro del manager.registerEventHandler(LifecycleEvents.COMMANDS)
            registrar.register(
                MistakenTestCommand.get(plugin),
                "Comando de pruebas secretas para Admins",
                listOf("mtest", "mt") // Alias cortos para no escribir tanto
            )
        }

        plugin.logger.info("[CommandRegistry] Comandos registrados correctamente")
    }
}
