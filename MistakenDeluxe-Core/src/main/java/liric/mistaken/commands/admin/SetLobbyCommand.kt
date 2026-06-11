package liric.mistaken.commands.admin

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * SetLobbyCommand - Kotlin Edition (Paper 1.21.4+)
 *
 * OptimizaciÃ³n:
 * - Usa Brigadier Node para inyecciÃ³n directa en el Dispatcher.
 * - ValidaciÃ³n de permisos nativa (.requires).
 * - Cero "reflection" de Bukkit antiguo.
 */
object SetLobbyCommand {

    private val mm = MiniMessage.miniMessage()

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("setlobby")
            // 1. ValidaciÃ³n de Permisos (Nativa y rÃ¡pida)
            // Si no tiene permiso, el comando ni siquiera aparece en el autocompletado.
            .requires { source ->
                source.sender.hasPermission("mistaken.admin")
            }
            // 2. EjecuciÃ³n
            .executes { ctx ->
                val sender = ctx.source.sender

                // Casting seguro de Kotlin. Si no es Player, 'player' serÃ¡ null.
                val player = sender as? Player

                // ValidaciÃ³n de ejecutor tÃ©cnico
                if (player == null) {
                    sender.sendMessage(mm.deserialize("<red>Este comando solo puede ser ejecutado por jugadores."))
                    return@executes 0 // Retornamos 0 para indicar fallo/no acciÃ³n
                }

                // --- LÃ“GICA DEL COMANDO ---

                // 3. Persistencia (La lÃ³gica interna de tu plugin)
                plugin.setLobbyLocationConfig(player.location)

                // 4. Feedback Visual (MultilingÃ¼e)
                // Asumiendo que tu MessageConfig ya devuelve un Component, si devuelve String usa mm.deserialize()
                val message = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.lobby-set")
                player.sendMessage(message)

                // 5. Feedback Auditivo
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)

                // 6. Registro de AuditorÃ­a (Logger de Paper)
                // Usamos Templates de Kotlin ($) para mÃ¡xima legibilidad y rendimiento
                plugin.componentLogger.info(mm.deserialize(
                    "<gray>[Mistaken]</gray> <green>Lobby actualizado en </green><white>${player.world.name}</white><green> por </green><white>${player.name}</white>"
                ))

                1 // Retornamos 1 para indicar Ã©xito (Command.SINGLE_SUCCESS)
            }
            .build()
    }
}
