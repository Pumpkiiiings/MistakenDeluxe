package liric.mistaken.commands

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import liric.mistaken.Mistaken
import org.bukkit.plugin.java.JavaPlugin

/**
 * [LIRIC-MISTAKEN 2.0]
 * CommandRegistry: Centralizador de comandos usando la nueva API Lifecycle de Paper (1.21.4+).
 * Elimina la necesidad de registrar comandos en el plugin.yml.
 */
class CommandRegistry(private val plugin: Mistaken) {

    fun registerAll() {
        val manager = plugin.lifecycleManager

        // Registramos en el evento COMMANDS del ciclo de vida
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()

            // Registro de comandos individuales
            // "mistaken" es el comando base, luego vienen los alias
            registrar.register(
                "mistaken",
                "Comando principal del plugin",
                listOf("ms", "mt"),
                MistakenCommand(plugin)
            )

            registrar.register(
                "arena",
                "Gestión de arenas",
                ArenaCommand(plugin)
            )

            registrar.register(
                "vote",
                "Votar por un mapa",
                VoteCommand(plugin)
            )
        }
    }
}
