package liric.mistaken.commands.admin

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Sound
import org.bukkit.entity.Player
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

/**
 * SetLobbyCommand - Kotlin Edition (Paper 1.21.4+)
 *
 * Optimizaci�n:
 * - Usa Brigadier Node para inyecci�n directa en el Dispatcher.
 * - Validaci�n de permisos nativa (.requires).
 * - Cero "reflection" de Bukkit antiguo.
 */
object SetLobbyCommand {

    private val mm = MiniMessage.miniMessage()

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("setlobby")
            // 1. Validaci�n de Permisos (Nativa y r�pida)
            // Si no tiene permiso, el comando ni siquiera aparece en el autocompletado.
            .requires { source ->
                source.sender.hasPermission("mistaken.admin")
            }
            // 2. Ejecuci�n
            .executes { ctx ->
                val sender = ctx.source.sender

                // Casting seguro de Kotlin. Si no es Player, 'player' ser� null.
                val player = sender as? Player

                // Validaci�n de ejecutor t�cnico
                if (player == null) {
                    sender.sendMessage(ColorTranslator.translate("<red>Este comando solo puede ser ejecutado por jugadores."))
                    return@executes 0 // Retornamos 0 para indicar fallo/no acci�n
                }

                // --- L�GICA DEL COMANDO ---

                // 3. Persistencia (La l�gica interna de tu plugin)
                plugin.setLobbyLocationConfig(player.location)

                // 4. Feedback Visual (Multiling�e)
                val message = PumpkingServiceManager.messages.getComponent(player, "admin.lobby-set")
                player.sendMessage(message)

                // 5. Feedback Auditivo
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)

                // 6. Registro de Auditor�a (Logger de Paper)
                // Usamos Templates de Kotlin ($) para m�xima legibilidad y rendimiento
                plugin.componentLogger.info(ColorTranslator.translate(
                    "<gray>[Mistaken]</gray> <green>Lobby actualizado en </green><white>${player.world.name}</white><green> por </green><white>${player.name}</white>"
                ))

                1 // Retornamos 1 para indicar �xito (Command.SINGLE_SUCCESS)
            }
            .build()
    }
}
