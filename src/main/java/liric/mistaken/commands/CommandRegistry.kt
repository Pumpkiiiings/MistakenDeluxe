package liric.mistaken.commands

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import liric.mistaken.Mistaken

/**
 * [LIRIC-MISTAKEN 2.0]
 * CommandRegistry: El cerebro del registro de comandos.
 * FIX: Alias duplicados arreglados, ComponentLogger añadido y nuevo comando Espectear registrado.
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

            registrar.register(
                LinkCommand.get(plugin),
                "Vincular Discord",
                emptyList()
            )

            registrar.register(
                MistakenTestCommand.get(plugin),
                "Comando de pruebas secretas para Admins",
                listOf("mtest") // Quité el 'mt' para evitar conflictos con el comando principal
            )

            // 🔥 NUEVO COMANDO: CINEMÁTICA (Para probar los Outros épicos)
            registrar.register(
                CinematicaCommand.get(plugin),
                "Reproducir cinemáticas de asesinos",
                listOf("cine")
            )

            // --- GRUPO B: COMANDOS BÁSICOS (Clases BasicCommand) ---
            // Estos SÍ llevan '()' porque son instancias de clase

            registrar.register(
                "mistaken",
                "Comando principal",
                listOf("ms", "mt"), // 'mt' se queda solo aquí
                MistakenCommand(plugin)
            )

            registrar.register(
                "arena",
                "Gestión de arenas",
                ArenaCommand(plugin)
            )

            // 🔥 NUEVO COMANDO: ESPECTADOR INVISIBLE
            registrar.register(
                "espectear",
                "Entrar al modo espectador invisible con TP",
                listOf("spectate"),
                EspectearCommand(plugin)
            )

            // 🔥 COMANDO DE DEBUG DE HITBOXES
            registrar.register(
                HitboxCommand.get(plugin),
                "Alternar el visor de hitboxes 3D",
                listOf("hitboxes") // Alias opcional
            )
        }

        plugin.componentLogger.info(plugin.mm.deserialize("<green>[CommandRegistry] Comandos registrados correctamente.</green>"))
    }
}
