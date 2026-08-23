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
import liric.mistaken.utils.color.ColorTranslator
import java.util.function.Consumer

object MistakenDebugCommand {

    private val activeGeoffreys = ConcurrentHashMap<Int, GeoffreyEXE>()

    private var instanceCounter = 0

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        val rootNode = Commands.literal("mistakendebug")
            .requires { it.sender.hasPermission("mistaken.admin") }

        
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
                        sender.sendMessage("§c[!] Player not found or offline.")
                        return@executes 0
                    }

                    val uuid = target.uniqueId
                    if (plugin.ignoredTestPlayers.contains(uuid)) {
                        plugin.ignoredTestPlayers.remove(uuid)
                        sender.sendMessage("§a[!] §e${target.name} §fis no longer ignored by anomalies.")
                    } else {
                        plugin.ignoredTestPlayers.add(uuid)
                        sender.sendMessage("§c[!] §e${target.name} §fis now invisible to .EXE entities.")
                    }
                    1
                }
            )
        )

        
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
                    p.sendMessage("§c[!] You are not in any active session.")
                    return@executes 0
                }
                session.forceStart = true
                p.sendMessage("§a[!] §eForce start activated. The game will begin ignoring the player limit.")
                1
            }
        )

        
        rootNode.then(
            Commands.literal("debugstart")
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
                    p.sendMessage("§c[!] You are not in any active session.")
                    return@executes 0
                }
                session.forceStart = true
                session.isDebugStart = true
                p.sendMessage("§a[!] §eDebug mode activated. The game will not end on its own until /mistakendebug endgame is used.")
                1
            }
        )

        
        rootNode.then(
            Commands.literal("role")
            .then(
                Commands.argument("type", StringArgumentType.word())
                .suggests { _, builder: SuggestionsBuilder ->
                    builder.suggest("killer")
                    builder.suggest("survivor")
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val roleType = StringArgumentType.getString(ctx, "type").lowercase()
                    val session = plugin.sessionManager.getSession(p)

                    if (session == null) {
                        p.sendMessage("§c[!] You are not in any active session.")
                        return@executes 0
                    }
                    
                    if (session.currentState != GameState.LOBBY && session.currentState != GameState.VOTING && session.currentState != GameState.BREAK) {
                        p.sendMessage("§c[!] You can only force your role before the game starts.")
                        return@executes 0
                    }

                    if (roleType == "killer") {
                        session.forcedKillerUUID = p.uniqueId
                        session.forcedSurvivorUUIDs.remove(p.uniqueId)
                        p.sendMessage("§a[!] §eYou will be the §cKILLER §ein this game.")
                    } else if (roleType == "survivor") {
                        session.forcedSurvivorUUIDs.add(p.uniqueId)
                        if (session.forcedKillerUUID == p.uniqueId) session.forcedKillerUUID = null
                        p.sendMessage("§a[!] §eYou will be a §aSURVIVOR §ein this game.")
                    } else {
                        p.sendMessage("§c[!] Invalid role. Use 'killer' or 'survivor'.")
                    }
                    1
                }
            )
        )

        
        rootNode.then(
            Commands.literal("endgame")
            .executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val session = plugin.sessionManager.getSession(p)

                if (session == null) {
                    p.sendMessage("§c[!] You are not in any active session.")
                    return@executes 0
                }
                
                if (session.currentState == GameState.INGAME) {
                    session.stateController.endGame("game.victory-survivors", false, forceDebugEnd = true)
                    p.sendMessage("§a[!] §eGame forcefully ended.")
                } else {
                    p.sendMessage("§c[!] The game is not currently in progress.")
                }
                1
            }
        )

        
        rootNode.then(
            Commands.literal("lms")
            .then(
                Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    
                    
                    liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 255, 255, 255, 0.9f, 30)
                    liric.mistaken.utils.hooks.ObserverHook.playScreenshake(p, 1.5f, 40)
                    
                    
                    p.playSound(p.location, "mistaken:lms", org.bukkit.SoundCategory.RECORDS, 1f, 1f)
                    
                    
                    p.scheduler.runDelayed(plugin, Consumer { _ ->
                        liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 255, 0, 0, 0.2f, 1200) 
                    }, null, 30L)
                    
                    p.sendMessage("§a[!] LMS effects and music started (Test duration: 1 min).")
                    1
                }
                .then(Commands.literal("all").executes { ctx ->
                    val sender = ctx.source.sender
                    Bukkit.getOnlinePlayers().forEach { p ->
                        p.stopAllSounds()
                        p.scheduler.runDelayed(plugin, Consumer { _ ->
                            liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 255, 255, 255, 0.9f, 30)
                            liric.mistaken.utils.hooks.ObserverHook.playScreenshake(p, 1.5f, 40)
                            p.playSound(p.location, "mistaken:lms", org.bukkit.SoundCategory.RECORDS, 1f, 1f)
                            p.scheduler.runDelayed(plugin, Consumer { _ ->
                                liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 255, 0, 0, 0.2f, 1200) 
                            }, null, 30L)
                        }, null, 20L)
                    }
                    sender.sendMessage("§a[!] LMS effects and music started for all players.")
                    1
                })
            )
            .then(Commands.literal("end").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                
                p.stopSound("mistaken:lms", org.bukkit.SoundCategory.RECORDS)
                liric.mistaken.utils.hooks.ObserverHook.playScreenTint(p, 0, 0, 0, 0f, 1) 
                p.sendMessage("§c[!] LMS effects stopped.")
                1
            })
        )

        
        rootNode.then(
            Commands.literal("geoffrey")
            .then(Commands.literal("start").executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val entity = GeoffreyEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                activeGeoffreys[instanceCounter++] = entity
                p.sendMessage("§4[!] §cAnomaly §lGEOFFREY.EXE §r§cspawned.")
                1
            })
        )

        
        rootNode.then(
            Commands.literal("spawnall")
            .executes { ctx ->
                val p = ctx.source.sender as? Player ?: return@executes 0
                val loc = p.location
                p.sendMessage("§4§l[!] WARNING: §cInitiating reality collapse... 1 ANOMALY DETECTED.")

                activeGeoffreys[instanceCounter++] = GeoffreyEXE(plugin).apply { spawn(loc.clone().add(5.0, 0.0, 0.0)) }

                p.sendMessage(ColorTranslator.translate("<dark_red><bold>EXE APOCALYPSE <reset><red>GEOFFREY.EXE HAS APPEARED. You will not survive..."))
                1
            }
        )

        
        rootNode.then(
            Commands.literal("stop")
            .executes { ctx ->
                val sender = ctx.source.sender

                activeGeoffreys.values.forEach { it.remove() }; activeGeoffreys.clear()

                sender.sendMessage("§a§l[✔] §aContainment protocol successful. All anomalies eliminated.")
                1
            }
        )

        return rootNode.build()
    }
}
