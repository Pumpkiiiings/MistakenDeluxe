package liric.mistaken.commands

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import liric.mistaken.Mistaken

/**
 * [LIRIC-MISTAKEN 2.0]
 * CommandRegistry: Centralizador de inyección de comandos Brigadier.
 * Optimización: Registra todas las instancias una sola vez en el ciclo de vida de Paper.
 */
class CommandRegistry(private val plugin: Mistaken) {

    fun registerAll() {
        val manager = plugin.lifecycleManager

        // El evento COMMANDS se dispara cuando el servidor está listo para recibir registros
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()

            // --- 1. COMANDO PRINCIPAL (/mistaken) ---
            registrar.register(
                "mistaken",
                "Comando general de Mistaken (Stats, Shop, Admin)",
                listOf("ms", "mt", "mist"),
                MistakenCommand(plugin)
            )

            // --- 2. COMANDO DE ARENAS (/arena) ---
            registrar.register(
                "arena",
                "Administración y creación de mapas/arenas",
                ArenaCommand(plugin)
            )

            // --- 3. COMANDO DE VOTACIÓN (/vote) ---
            registrar.register(
                "vote",
                "Votar por el siguiente mapa de la partida",
                VoteCommand(plugin)
            )

            // --- 4. COMANDO DE VINCULACIÓN (/link) ---
            registrar.register(
                "link",
                "Generar código de vinculación para Discord",
                LinkCommand(plugin)
            )

            // --- 5. COMANDO DE DESVINCULACIÓN (/unlink) ---
            registrar.register(
                "unlink",
                "Desvincular a un jugador de su cuenta de Discord",
                UnlinkCommand(plugin)
            )

            // --- 6. COMANDO DE LOBBY (/setlobby) ---
            registrar.register(
                "setlobby",
                "Establecer el punto de spawn del lobby principal",
                SetLobbyCommand(plugin)
            )
        }

        plugin.logger.info("§a[CommandRegistry] Todos los comandos inyectados en Brigadier (Nativo).")
    }
}
