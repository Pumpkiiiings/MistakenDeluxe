package liric.mistaken.commands

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.entities.GeoffreyEXE
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TEST
 * MistakenTestCommand: El patio de juegos secreto de los Admins.
 * Ahora con inicio autónomo de GEOFFREY.EXE.
 */
object MistakenTestCommand {

    // Lista de Geoffreys activos para poder detenerlos todos de un jalón
    private val activeGeoffreys = ConcurrentHashMap<Int, GeoffreyEXE>()
    private var instanceCounter = 0

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("mistakentest")
            // 🔥 FILTRO NINJA: Solo para los meros jefes
            .requires { it.sender.hasPermission("mistaken.admin") }

            .then(
                Commands.literal("geoffrey")
                    // Subcomando: /mistakentest geoffrey start
                    .then(
                        Commands.literal("start")
                            .executes { ctx ->
                                val sender = ctx.source.sender
                                val player = sender as? Player ?: run {
                                    sender.sendMessage("§cOcupas estar en el mundo para soltar a la bestia, compa.")
                                    return@executes 0
                                }

                                // 1. Creamos la instancia del Geoffrey (IA Autónoma)
                                val geoffrey = GeoffreyEXE(plugin)

                                // 2. Lo spawneamos 5 bloques atrás del admin para que lo vea salir
                                val spawnLoc = player.location.add(player.location.direction.multiply(-5)).add(0.0, 1.0, 0.0)

                                geoffrey.spawn(spawnLoc)

                                // 3. Lo guardamos en el mapa para el comando 'stop'
                                val id = instanceCounter++
                                activeGeoffreys[id] = geoffrey

                                player.sendMessage("§4[!!!] §cAnomalía §lGEOFFREY.EXE §r§ciniciada. ¡Que Dios nos agarre confesados!")
                                1
                            }
                    )
                    // Subcomando: /mistakentest geoffrey stop
                    .then(
                        Commands.literal("stop")
                            .executes { ctx ->
                                if (activeGeoffreys.isEmpty()) {
                                    ctx.source.sender.sendMessage("§yNo hay anomalías activas ahorita, pariente.")
                                    return@executes 1
                                }

                                activeGeoffreys.values.forEach { it.remove() }
                                activeGeoffreys.clear()
                                ctx.source.sender.sendMessage("§a§l[!] §aTodos los Geoffreys han sido borrados del código.")
                                1
                            }
                    )
            )

            // Subcomando global por si quieres limpiar TODO el desmadre de una
            .then(
                Commands.literal("stop")
                    .executes { ctx ->
                        activeGeoffreys.values.forEach { it.remove() }
                        activeGeoffreys.clear()
                        ctx.source.sender.sendMessage("§aLimpieza total de pruebas completada.")
                        1
                    }
            )
            .build()
    }
}
