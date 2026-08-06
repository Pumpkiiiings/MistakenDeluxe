package liric.mistaken.commands.debug

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.entities.GeoffreyEXE
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import liric.mistaken.game.enums.GameState
import pumpking.lib.color.ColorTranslator

object MistakenTestCommand {

    private val activeGeoffreys = ConcurrentHashMap<Int, GeoffreyEXE>()

    private var instanceCounter = 0

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        val rootNode = Commands.literal("mistakentest")
            .requires { it.sender.hasPermission("mistaken.admin") }

        // --- IGNORAR JUGADORES ---
        rootNode.then(
            Commands.literal("ignore")
            .then(
                Commands.argument("player", StringArgumentType.word())
                .suggests { _, builder: SuggestionsBuilder ->
                    Bukkit.getOnlinePlayers().forEach { builder.suggest(it.name) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val targetName = StringArgumentType.getString(ctx, "player")
                    val target = Bukkit.getPlayer(targetName)

                    if (target == null) {
                        sender.sendMessage("§cEse plebe no anda por aquí, pariente.")
                        return@executes 0
                    }

                    val uuid = target.uniqueId
                    if (plugin.ignoredTestPlayers.contains(uuid)) {
                        plugin.ignoredTestPlayers.remove(uuid)
                        sender.sendMessage("§a[!] §e${target.name} §fya no es ignorado por las anomalías.")
                    } else {
                        plugin.ignoredTestPlayers.add(uuid)
                        sender.sendMessage("§c[!] §e${target.name} §fahora es invisible para los entes .EXE")
                    }
                    1
                }
            )
        )

        // --- FORCE START ---
        rootNode.then(
            Commands.literal("forcestart")
            .executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                var session = plugin.sessionManager.getSession(p)
                
                if (session == null && plugin.serverMode == "MULTIARENA") {
                    session = plugin.sessionManager.activeSessions.values.firstOrNull { 
                        it.currentState == GameState.LOBBY || 
                        it.currentState == GameState.VOTING || 
                        it.currentState == GameState.BREAK 
                    }
                    if (session == null) {
                        session = plugin.sessionManager.createSession("Votando...")
                    }
                    
                    val playersToJoin = Bukkit.getOnlinePlayers().filter { plugin.sessionManager.getSession(it) == null }
                    playersToJoin.forEach { plugin.sessionManager.joinSession(it, session!!.id) }
                }

                if (session == null) {
                    p.sendMessage("§c[!] No estás en ninguna sesión, plebe.")
                    return@executes 0
                }
                session.forceStart = true
                p.sendMessage("§a[!] §eInicio forzado activado. La partida comenzará ignorando el límite de jugadores.")
                1
            }
        )

        // --- GEOFFREY ---
        rootNode.then(
            Commands.literal("geoffrey")
            .then(Commands.literal("start").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val entity = GeoffreyEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                activeGeoffreys[instanceCounter++] = entity
                p.sendMessage("§4[!] §cAnomalía §lGEOFFREY.EXE §r§ciniciada.")
                1
            })
        )

        // --- SPAWN ALL (CAOS TOTAL) ---
        rootNode.then(
            Commands.literal("spawnall")
            .executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val loc = p.location
                p.sendMessage("§4§l[!] ADVERTENCIA: §cIniciando colapso de realidad... 1 ANOMALÍA DETECTADA.")

                activeGeoffreys[instanceCounter++] = GeoffreyEXE(plugin).apply { spawn(loc.clone().add(5.0, 0.0, 0.0)) }

                p.sendMessage(ColorTranslator.translate("<dark_red><bold>APOCALIPSIS EXE <reset><red>GEOFFREY.EXE HA APARECIDO. No sobrevivirás..."))
                1
            }
        )

        // --- STOP ---
        rootNode.then(
            Commands.literal("stop")
            .executes { ctx ->
                val sender = ctx.source.sender

                activeGeoffreys.values.forEach { it.remove() }; activeGeoffreys.clear()

                sender.sendMessage("§a§l[✔] §aProtocolo de contención exitoso. Todas las anomalías eliminadas.")
                1
            }
        )

        return rootNode.build()
    }
}
