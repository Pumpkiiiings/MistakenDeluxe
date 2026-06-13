package liric.mistaken.commands.debug

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.entities.AmongUsEXE
import liric.mistaken.game.entities.Axolotl
import liric.mistaken.game.entities.EyedroomsEXE
import liric.mistaken.game.entities.GeoffreyEXE
import liric.mistaken.game.entities.ObservantEXE
import liric.mistaken.game.entities.PouEXE
import liric.mistaken.game.entities.WitherStorm
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

object MistakenTestCommand {

    private val activeGeoffreys = ConcurrentHashMap<Int, GeoffreyEXE>()
    private val activeSus = ConcurrentHashMap<Int, AmongUsEXE>()
    private val activePous = ConcurrentHashMap<Int, PouEXE>()
    private val activeAxos = ConcurrentHashMap<Int, Axolotl>()
    private val activeObservants = ConcurrentHashMap<Int, ObservantEXE>()
    private val activeEyedrooms = ConcurrentHashMap<Int, EyedroomsEXE>()
    private val activeWitherStorms = ConcurrentHashMap<Int, WitherStorm>()

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
                        it.currentState == liric.mistaken.game.enums.GameState.LOBBY || 
                        it.currentState == liric.mistaken.game.enums.GameState.VOTING || 
                        it.currentState == liric.mistaken.game.enums.GameState.BREAK 
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

        // --- AMONG US (SUS) ---
        rootNode.then(
            Commands.literal("amongus")
            .then(Commands.literal("start").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val entity = AmongUsEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                activeSus[instanceCounter++] = entity
                p.sendMessage("§c[!] §fHay un §lIMPOSTOR §fentre nosotros...")
                1
            })
        )

        // --- POU ---
        rootNode.then(
            Commands.literal("pou")
            .then(Commands.literal("start").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val entity = PouEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                activePous[instanceCounter++] = entity
                p.sendMessage("§6[!] §eTu mascota virtual §lPOU.EXE §eha despertado.")
                1
            })
        )

        // --- AXOLOTL ---
        rootNode.then(
            Commands.literal("axolotl")
            .then(Commands.literal("start").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val entity = Axolotl(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                activeAxos[instanceCounter++] = entity
                p.sendMessage("§b[!] §3El §lAXOLOTL GIGANTE §3ha emergido.")
                1
            })
        )

        // --- OBSERVANT ---
        rootNode.then(
            Commands.literal("observant")
            .then(Commands.literal("start").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val entity = ObservantEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-10))) }
                activeObservants[instanceCounter++] = entity
                p.sendMessage("§8[!] §7Sientes que alguien te observa... §lOBSERVANT.EXE")
                1
            })
        )

        // --- EYEDROOMS ---
        rootNode.then(
            Commands.literal("eyedrooms")
            .then(Commands.literal("start").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val entity = EyedroomsEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                activeEyedrooms[instanceCounter++] = entity
                p.sendMessage("§5[!] §dEl ojo de los Backrooms te está vigilando... §lEYEDROOMS.EXE")
                1
            })
        )

        // --- WITHER STORM ---
        rootNode.then(
            Commands.literal("witherstorm")
            .then(Commands.literal("start").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val entity = WitherStorm(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-10))) }
                activeWitherStorms[instanceCounter++] = entity
                p.sendMessage("§5[!] §d¡ALERTA SÍSMICA! §5§lWITHER STORM §dha llegado.")
                1
            })
        )

        // --- SPAWN ALL (CAOS TOTAL) ---
        rootNode.then(
            Commands.literal("spawnall")
            .executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val loc = p.location
                p.sendMessage("§4§l[!] ADVERTENCIA: §cIniciando colapso de realidad... 7 ANOMALÍAS DETECTADAS.")

                activeGeoffreys[instanceCounter++] = GeoffreyEXE(plugin).apply { spawn(loc.clone().add(5.0, 0.0, 0.0)) }
                activeSus[instanceCounter++] = AmongUsEXE(plugin).apply { spawn(loc.clone().add(-5.0, 0.0, 0.0)) }
                activePous[instanceCounter++] = PouEXE(plugin).apply { spawn(loc.clone().add(0.0, 0.0, 5.0)) }
                activeAxos[instanceCounter++] = Axolotl(plugin).apply { spawn(loc.clone().add(0.0, 0.0, -5.0)) }
                activeObservants[instanceCounter++] = ObservantEXE(plugin).apply { spawn(loc.clone().add(0.0, 10.0, 0.0)) }
                activeEyedrooms[instanceCounter++] = EyedroomsEXE(plugin).apply { spawn(loc.clone().add(5.0, 5.0, 5.0)) }
                activeWitherStorms[instanceCounter++] = WitherStorm(plugin).apply { spawn(loc.clone().add(0.0, 15.0, 0.0)) }

                p.showTitle(
                    Title.title(
                    plugin.mm.deserialize("<dark_red><bold>APOCALIPSIS EXE"),
                    plugin.mm.deserialize("<red>7 ENTES HAN APARECIDO. No sobrevivirás...")
                ))
                1
            }
        )

        // --- STOP ---
        rootNode.then(
            Commands.literal("stop")
            .executes { ctx ->
                val sender = ctx.source.sender

                activeGeoffreys.values.forEach { it.remove() }; activeGeoffreys.clear()
                activeSus.values.forEach { it.remove() }; activeSus.clear()
                activePous.values.forEach { it.remove() }; activePous.clear()
                activeAxos.values.forEach { it.remove() }; activeAxos.clear()
                activeObservants.values.forEach { it.remove() }; activeObservants.clear()
                activeEyedrooms.values.forEach { it.remove() }; activeEyedrooms.clear()
                activeWitherStorms.values.forEach { it.remove() }; activeWitherStorms.clear()

                sender.sendMessage("§a§l[✔] §aProtocolo de contención exitoso. Todas las anomalías eliminadas.")
                1
            }
        )

        return rootNode.build()
    }
}