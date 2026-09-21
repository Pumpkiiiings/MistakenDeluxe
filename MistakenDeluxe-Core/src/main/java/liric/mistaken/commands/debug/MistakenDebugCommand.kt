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
        val rootNode = Commands.literal("mdebug")
            .requires { source -> source.sender.hasPermission("mistaken.admin") }
            
        rootNode.then(
            Commands.literal("help")
                .executes { ctx ->
                    val sender = ctx.source.sender
                    sender.sendMessage(ColorTranslator.translate(
                        """
                        <#3BFFC7>Mistaken Debug <#888888>| <#FF3344>Ayuda
                        <#CCCCCC>» <#FF3344>/mdebug help <#888888>- <white>This menu
                        <#CCCCCC>» <#FF3344>/mdebug arena (check|setup) <#888888>- <white>Arena debug
                        <#CCCCCC>» <#FF3344>/mdebug session (list|info) <#888888>- <white>Session debug
                        <#CCCCCC>» <#FF3344>/mdebug player <name> <#888888>- <white>Player data
                        <#CCCCCC>» <#FF3344>/mdebug visual (sb|tab) <#888888>- <white>Visuals debug
                        <#CCCCCC>» <#FF3344>/mdebug lms (start|all|end) <#888888>- <white>Last Man Standing test
                        <#CCCCCC>» <#FF3344>/mdebug cinematic (intro|outro) <killer> <#888888>- <white>Play cinematics
                        <#CCCCCC>» <#FF3344>/mdebug hitbox <#888888>- <white>Toggle 3D hitboxes
                        <#CCCCCC>» <#FF3344>/mdebug geoffrey <#888888>- <white>Boss test
                        <#CCCCCC>» <#FF3344>/mdebug perks <#888888>- <white>Open perk draft menu (debug)
                        <#CCCCCC>» <#FF3344>/mdebug gui <menu> <#888888>- <white>Open any GUI by name
                        """.trimIndent()
                    ))
                    1
                }
        )

        
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
                    playersToJoin.forEach { plugin.sessionManager.joinSession(it, session.id) }
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
                    playersToJoin.forEach { plugin.sessionManager.joinSession(it, session.id) }
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
            Commands.literal("player")
            .then(
                Commands.argument("target", StringArgumentType.word())
                .suggests { _, builder: SuggestionsBuilder ->
                    Bukkit.getOnlinePlayers().forEach { builder.suggest(it.name) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val targetName = StringArgumentType.getString(ctx, "target")
                    val target = Bukkit.getPlayer(targetName)
                    
                    if (target == null) {
                        sender.sendMessage("§c[!] Player not found.")
                        return@executes 0
                    }
                    
                    val session = plugin.sessionManager.getSession(target)
                    val state = session?.currentState?.name ?: "No Session (or Server Lobby)"
                    
                    var role = "Unknown"
                    if (session != null) {
                        if (session.isKiller(target.uniqueId)) {
                            val killerClass = plugin.killerManager.getKillerOfPlayer(target)
                            role = "KILLER (${killerClass?.id ?: "None"})"
                        } else {
                            val survivorClass = plugin.survivorManager.getSurvivorClass(target)
                            role = "SURVIVOR (${survivorClass?.id ?: "None"})"
                        }
                    }
                    
                    val stats = plugin.statsManager.getStats(target.uniqueId)
                    val data = plugin.playerDataManager.getUserData(target.uniqueId)
                    
                    sender.sendMessage("§e=== Debug Info: §a${target.name} §e===")
                    sender.sendMessage("§7Status: §f$state")
                    sender.sendMessage("§7Role: §f$role")
                    sender.sendMessage("§7Language: §f${data?.language ?: "N/A"}")
                    sender.sendMessage("§7Stamina: §f${data?.stamina ?: 100.0}")
                    sender.sendMessage("§7Kills: §f${stats.kills.get()}")
                    sender.sendMessage("§7Wins (Survivor): §f${stats.winsSurvivor.get()}")
                    sender.sendMessage("§7Wins (Killer): §f${stats.winsAssassin.get()}")
                    1
                }
            )
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

        // --- HITBOX AND CINEMATIC (Moved from old commands) ---
        rootNode.then(
            Commands.literal("hitbox")
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val isNowEnabled = liric.mistaken.utils.misc.HitboxVisualizer.toggle()
                    val state = if (isNowEnabled) "<green><bold>ENABLED</bold></green>" else "<red><bold>DISABLED</bold></red>"
                    sender.sendMessage(ColorTranslator.translate("<gray>[<yellow>DEBUG</yellow>] <white>Hitbox Visualizer: $state"))
                    1
                }
        )

        val killersList = listOf(
            "sowoul", "pizzano", "errorestatico", "charlieinferno",
            "colorandelectricity", "rome", "romeodebuff", "slasher",
            "herobrine", "nullasesino", "entity303", "bendy", "kasaneteto", "mariachi"
        )
        
        rootNode.then(
            Commands.literal("cinematic")
                .then(
                    Commands.literal("intro")
                        .then(
                            Commands.argument("killer", StringArgumentType.word())
                                .suggests { _, builder ->
                                    killersList.forEach { if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .executes { ctx ->
                                    val source = ctx.source.sender as? Player ?: return@executes 0
                                    val killerId = StringArgumentType.getString(ctx, "killer")
                                    val killerDummy = object : liric.mistaken.roles.killers.Killer(killerId, "<gold><bold>${killerId.uppercase()}</bold></gold>") {
                                        override fun equip(player: Player) {}
                                        override fun useSkill(player: Player, slot: Int) {}
                                        override fun showTrail(player: Player) {}
                                        override fun showPhysicalTrail(player: Player) {}
                                    }
                                    source.sendMessage(ColorTranslator.translate("<green>Playing <bold>INTRO</bold> for: <yellow>$killerId"))
                                    plugin.cinematicManager.playKillerIntro(source, killerDummy, listOf(source))
                                    1
                                }
                        )
                )
                .then(
                    Commands.literal("outro")
                        .then(
                            Commands.argument("killer", StringArgumentType.word())
                                .suggests { _, builder ->
                                    killersList.forEach { if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .executes { ctx ->
                                    val source = ctx.source.sender as? Player ?: return@executes 0
                                    val killerId = StringArgumentType.getString(ctx, "killer")
                                    val killerDummy = object : liric.mistaken.roles.killers.Killer(killerId, "<gold><bold>${killerId.uppercase()}</bold></gold>") {
                                        override fun equip(player: Player) {}
                                        override fun useSkill(player: Player, slot: Int) {}
                                        override fun showTrail(player: Player) {}
                                        override fun showPhysicalTrail(player: Player) {}
                                    }
                                    source.sendMessage(ColorTranslator.translate("<red>Playing <bold>OUTRO</bold> for: <yellow>$killerId"))
                                    plugin.cinematicManager.playKillerOutro(source, killerDummy, listOf(source))
                                    1
                                }
                        )
                )
        )

        // --- PERKS DEBUG ---
        rootNode.then(
            Commands.literal("perks")
                .executes { ctx ->
                    val player = ctx.source.sender as? Player ?: return@executes 0
                    val options = plugin.perkManager.getDraftOptions(3)
                    if (options.isEmpty()) {
                        player.sendMessage(ColorTranslator.translate("<red>[DEBUG] No perks registered."))
                        return@executes 0
                    }
                    // Clear any existing perk first so the menu is fully interactive
                    plugin.perkManager.clearPerkForPlayer(player.uniqueId)
                    liric.mistaken.menu.menus.PerkDraftMenu(plugin).open(player, options)
                    player.sendMessage(ColorTranslator.translate("<gray>[<yellow>DEBUG<gray>] <white>Opening perk draft with <#3BFFC7>${options.size} random perks."))
                    1
                }
        )

        // --- GUI DEBUG ---
        val guiNames = listOf("shop", "killers", "survivors", "private_lobby", "perks")
        rootNode.then(
            Commands.literal("gui")
                .then(
                    Commands.argument("menu", StringArgumentType.word())
                        .suggests { _, builder ->
                            guiNames.forEach { if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val player = ctx.source.sender as? Player ?: return@executes 0
                            val menuArg = StringArgumentType.getString(ctx, "menu").lowercase()
                            when (menuArg) {
                                "shop" -> {
                                    plugin.shopSelector.abrir(player)
                                    player.sendMessage(ColorTranslator.translate("<gray>[<yellow>DEBUG<gray>] <white>Opened: <#3BFFC7>Shop Selector"))
                                }
                                "killers" -> {
                                    liric.mistaken.menu.menus.KillerShop().abrir(player)
                                    player.sendMessage(ColorTranslator.translate("<gray>[<yellow>DEBUG<gray>] <white>Opened: <#3BFFC7>Killer Shop"))
                                }
                                "survivors" -> {
                                    liric.mistaken.menu.menus.SurvivorShop().abrir(player)
                                    player.sendMessage(ColorTranslator.translate("<gray>[<yellow>DEBUG<gray>] <white>Opened: <#3BFFC7>Survivor Shop"))
                                }
                                "perks" -> {
                                    val options = plugin.perkManager.getDraftOptions(3)
                                    plugin.perkManager.clearPerkForPlayer(player.uniqueId)
                                    liric.mistaken.menu.menus.PerkDraftMenu(plugin).open(player, options)
                                    player.sendMessage(ColorTranslator.translate("<gray>[<yellow>DEBUG<gray>] <white>Opened: <#3BFFC7>Perk Draft"))
                                }
                                else -> {
                                    player.sendMessage(ColorTranslator.translate("<red>[DEBUG] Unknown menu: <white>$menuArg"))
                                    player.sendMessage(ColorTranslator.translate("<gray>Available: <white>${guiNames.joinToString(", ")}"))
                                }
                            }
                            1
                        }
                )
        )

        return rootNode.build()
    }
}
