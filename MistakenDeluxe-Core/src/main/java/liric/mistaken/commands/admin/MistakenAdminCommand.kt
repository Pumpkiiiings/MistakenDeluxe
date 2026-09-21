package liric.mistaken.commands.admin

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.config.engine.core.ConfigManager
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.io.File
import liric.mistaken.utils.color.ColorTranslator

object MistakenAdminCommand {

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        val rootNode = Commands.literal("mistaken")

        rootNode.then(
            Commands.literal("help")
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val player = sender as? Player
                    sender.sendMessage(MessageService.getComponent(player, "admin.help-player"))
                    if (sender.hasPermission("mistaken.admin")) {
                        sender.sendMessage(MessageService.getComponent(player, "admin.help-admin"))
                    }
                    1
                }
        )

        // --- RAMA GAME ---
        val gameNode = Commands.literal("game").requires { source -> source.sender.hasPermission("mistaken.admin") }
        
        gameNode.then(
            Commands.literal("start").executes { ctx ->
                val sender = ctx.source.sender
                val player = sender as? Player
                var session = player?.let { plugin.sessionManager.getSession(it) }

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
                    sender.sendMessage(MessageService.getComponent(player, "errors.not-in-session"))
                    return@executes 0
                }

                if (session.currentState == GameState.INGAME) {
                    sender.sendMessage(MessageService.getComponent(player, "admin.start-already-ingame"))
                } else {
                    sender.sendMessage(MessageService.getComponent(player, "admin.start-forcing"))
                    if (session.currentState == GameState.LOBBY || session.currentState == GameState.VOTING || session.currentState == GameState.BREAK) {
                        session.stateController.startVotingProcess()
                        session.timer = 5
                    }
                }
                1
            }
        )

        gameNode.then(
            Commands.literal("stop").executes { ctx ->
                val sender = ctx.source.sender
                val player = sender as? Player
                val gm = player?.let { plugin.sessionManager.getSession(it) }

                if (gm == null || gm.currentState == GameState.LOBBY) {
                    sender.sendMessage(MessageService.getComponent(player, "errors.no-active-game"))
                } else {
                    gm.stateController.endGame("admin.stop-broadcast", false)
                    sender.sendMessage(MessageService.getComponent(player, "admin.stop-success"))
                }
                1
            }
        )

        gameNode.then(
            Commands.literal("setmode")
                .then(
                    Commands.argument("mode", StringArgumentType.word())
                        .suggests { _, builder ->
                            MistakenMode.entries.forEach { mode ->
                                if (mode.name.lowercase().startsWith(builder.remainingLowerCase)) {
                                    builder.suggest(mode.name)
                                }
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val player = sender as? Player
                            val gm = player?.let { plugin.sessionManager.getSession(it) }
                            
                            if (player == null || gm == null) {
                                sender.sendMessage(MessageService.getComponent(player, "errors.not-in-session"))
                                return@executes 0
                            }
                            
                            val modeStr = StringArgumentType.getString(ctx, "mode")
                            try {
                                val mode = MistakenMode.valueOf(modeStr.uppercase())
                                gm.currentMode = mode
                                gm.modeForced = true
                                sender.sendMessage(MessageService.getComponent(player, "admin.mode-forced",
                                    Placeholder.parsed("mode", mode.name),
                                    Placeholder.parsed("session", gm.id)))
                                player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1f)
                            } catch (e: Exception) {
                                sender.sendMessage(MessageService.getComponent(player, "errors.invalid-mode"))
                            }
                            1
                        }
                )
        )
        
        rootNode.then(gameNode)


        // --- RAMA ROLE ---
        val roleNode = Commands.literal("role").requires { source -> source.sender.hasPermission("mistaken.admin") }
        
        roleNode.then(
            Commands.literal("setkiller")
                .then(
                    Commands.argument("id", StringArgumentType.word())
                        .suggests { _, builder ->
                            plugin.killerManager.getAvailableClasses().keys.forEach {
                                if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it)
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val player = sender as? Player ?: return@executes 0
                            val id = StringArgumentType.getString(ctx, "id")
                            
                            val killer = plugin.killerManager.getClassById(id)
                            if (killer == null) {
                                player.sendMessage(MessageService.getComponent(player, "errors.killer-not-found", Placeholder.parsed("type", id)))
                            } else {
                                plugin.killerManager.registerKiller(player, killer)
                            }
                            1
                        }
                )
        )

        roleNode.then(
            Commands.literal("setsurvivor")
                .then(
                    Commands.argument("id", StringArgumentType.word())
                        .suggests { _, builder ->
                            plugin.survivorManager.getAvailableClasses().keys.forEach {
                                if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it)
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val player = sender as? Player ?: return@executes 0
                            val id = StringArgumentType.getString(ctx, "id")
                            
                            val clase = plugin.survivorManager.getClassById(id)
                            if (clase == null) {
                                player.sendMessage(MessageService.getComponent(player, "errors.survivor-not-found", Placeholder.parsed("type", id)))
                            } else {
                                plugin.survivorManager.registrarSurvivor(player, clase as liric.mistaken.roles.survivors.Survivor)
                            }
                            1
                        }
                )
        )

        roleNode.then(
            Commands.literal("forcekiller")
                .then(
                    Commands.argument("target", StringArgumentType.word())
                        .suggests { _, builder ->
                            Bukkit.getOnlinePlayers().forEach { if (it.name.lowercase().startsWith(builder.remainingLowerCase)) builder.suggest(it.name) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val player = sender as? Player
                            val targetName = StringArgumentType.getString(ctx, "target")
                            val target = Bukkit.getPlayer(targetName)
                            
                            if (target == null) {
                                sender.sendMessage(MessageService.getComponent(player, "errors.player-not-found"))
                                return@executes 0
                            }
                            
                            val gm = player?.let { plugin.sessionManager.getSession(it) }
                            if (gm == null) {
                                sender.sendMessage(MessageService.getComponent(player, "errors.forcekiller-not-in-game"))
                                return@executes 0
                            }
                            
                            if (gm.currentState == GameState.INGAME || gm.currentState == GameState.ENDING || gm.currentState == GameState.STARTING) {
                                sender.sendMessage(MessageService.getComponent(player, "errors.forcekiller-already-started"))
                                return@executes 0
                            }
                            
                            gm.forcedKillerUUID = target.uniqueId
                            sender.sendMessage(MessageService.getComponent(player, "admin.forcekiller-success", 
                                Placeholder.parsed("player", target.name),
                                Placeholder.parsed("arena", gm.id)))
                            1
                        }
                )
        )

        roleNode.then(
            Commands.literal("reloadkiller")
                .then(
                    Commands.argument("id", StringArgumentType.word())
                        .suggests { _, builder ->
                            plugin.killerManager.getAvailableClasses().keys.forEach {
                                if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it)
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val player = sender as? Player
                            val id = StringArgumentType.getString(ctx, "id")
                            
                            plugin.killerManager.reloadKiller(id)
                            sender.sendMessage(MessageService.getComponent(player, "admin.reloadkiller-success", Placeholder.parsed("id", id)))
                            1
                        }
                )
        )

        roleNode.then(
            Commands.literal("removekiller")
                .executes { ctx ->
                    val sender = ctx.source.sender
                    val player = sender as? Player ?: return@executes 0
                    plugin.killerManager.removeKiller(player)
                    1
                }
                .then(
                    Commands.argument("target", StringArgumentType.word())
                        .suggests { _, builder ->
                            Bukkit.getOnlinePlayers().forEach { if (it.name.lowercase().startsWith(builder.remainingLowerCase)) builder.suggest(it.name) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val player = sender as? Player
                            val targetName = StringArgumentType.getString(ctx, "target")
                            val target = Bukkit.getPlayer(targetName)
                            
                            if (target != null) {
                                plugin.killerManager.removeKiller(target)
                                sender.sendMessage(MessageService.getComponent(player, "admin.removekiller-success", Placeholder.parsed("player", target.name)))
                            }
                            1
                        }
                )
        )
        
        rootNode.then(roleNode)


        // --- RAMA PLAYER ---
        val playerNode = Commands.literal("player").requires { source -> source.sender.hasPermission("mistaken.admin") }

        playerNode.then(
            Commands.literal("setstamina")
                .then(
                    Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 100.0))
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val player = sender as? Player ?: return@executes 0
                            val amount = DoubleArgumentType.getDouble(ctx, "amount")
                            
                            val user = plugin.playerDataManager.getUserData(player.uniqueId)
                            if (user != null) {
                                user.stamina = amount
                                player.foodLevel = (amount / 5).toInt()
                                player.sendMessage(MessageService.getComponent(player, "admin.stamina-set",
                                    Placeholder.parsed("player", player.name),
                                    Placeholder.parsed("amount", amount.toInt().toString())))
                            }
                            1
                        }
                )
        )

        playerNode.then(
            Commands.literal("edit").executes { ctx ->
                val sender = ctx.source.sender
                val player = sender as? Player ?: return@executes 0
                val uuid = player.uniqueId
                
                if (plugin.staffEditMode.contains(uuid)) {
                    plugin.staffEditMode.remove(uuid)
                    player.sendMessage(MessageService.getComponent(player, "game.edit-disable"))
                    player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f)
                } else {
                    plugin.staffEditMode.add(uuid)
                    player.sendMessage(MessageService.getComponent(player, "game.edit-enable"))
                    player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)
                }
                1
            }
        )

        playerNode.then(
            Commands.literal("data")
                .then(
                    Commands.literal("transfer")
                        .then(
                            Commands.argument("file", StringArgumentType.word())
                                .suggests { _, builder ->
                                    builder.suggest("players.yml")
                                    builder.buildFuture()
                                }
                                .executes { ctx ->
                                    val sender = ctx.source.sender
                                    val fileName = StringArgumentType.getString(ctx, "file")

                                    if (fileName != "players.yml") {
                                        sender.sendMessage(ColorTranslator.translate("<red>Solo se soporta players.yml por ahora."))
                                        return@executes 0
                                    }

                                    val file = File(plugin.dataFolder, fileName)
                                    if (!file.exists()) {
                                        sender.sendMessage(ColorTranslator.translate("<red>No se encontró el archivo $fileName"))
                                        return@executes 0
                                    }

                                    sender.sendMessage(ColorTranslator.translate("<yellow>Iniciando transferencia de datos a MySQL... Esto puede tardar unos segundos.</yellow>"))

                                    plugin.server.asyncScheduler.runNow(plugin) { _ ->
                                        try {
                                            val configProvider = ConfigManager.get(fileName)
                                            configProvider.load()
                                            val yaml = configProvider.getRaw()
                                            val uuids = yaml.getKeys(false)
                                            var count = 0

                                            for (uuidStr in uuids) {
                                                val section = yaml.getConfigurationSection(uuidStr) ?: continue

                                                val lang = section.getString("lang", "es") ?: "es"
                                                val comprados = section.getStringList("comprados").joinToString(",")
                                                val seleccionado = section.getString("seleccionado", "none") ?: "none"
                                                val survComprados = section.getStringList("supervivientes_comprados").joinToString(",")
                                                val survSeleccionado = section.getString("superviviente_seleccionado", "civil") ?: "civil"
                                                val nick = section.getString("nick", "") ?: ""
                                                val skin = section.getString("skin_source", "") ?: ""

                                                plugin.databaseManager.savePlayerDataRaw(
                                                    uuidStr, lang, comprados, seleccionado, survComprados, survSeleccionado, nick, skin
                                                )
                                                count++
                                            }

                                            sender.sendMessage(ColorTranslator.translate("<green><bold>ÉXITO!</bold> Se han migrado los datos de $count jugadores a la base de datos.</green>"))
                                            file.renameTo(File(plugin.dataFolder, "players_OLD_BACKUP.yml"))

                                        } catch (e: Exception) {
                                            sender.sendMessage(ColorTranslator.translate("<red>Error durante la migración: ${e.message}"))
                                            e.printStackTrace()
                                        }
                                    }
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
        )
        
        rootNode.then(playerNode)


        // --- RAMA CONFIG ---
        val configNode = Commands.literal("config").requires { source -> source.sender.hasPermission("mistaken.admin") }

        configNode.then(
            Commands.literal("reload")
                .then(
                    Commands.argument("target", StringArgumentType.word())
                        .suggests { _, builder ->
                            listOf("scripts", "killers", "survivors", "messages", "config", "all").forEach {
                                if (it.startsWith(builder.remainingLowerCase)) builder.suggest(it)
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val sender = ctx.source.sender
                            val player = sender as? Player
                            val target = StringArgumentType.getString(ctx, "target").lowercase()

                            plugin.server.asyncScheduler.runNow(plugin) { _ ->
                                var msg = "admin.reload-success"
                                
                                when (target) {
                                    "scripts" -> {
                                        plugin.server.globalRegionScheduler.execute(plugin) {
                                            plugin.killerManager.reloadAll()
                                            plugin.survivorManager.reloadAll()
                                        }
                                    }
                                    "killers" -> plugin.server.globalRegionScheduler.execute(plugin) { plugin.killerManager.reloadAll() }
                                    "survivors" -> plugin.server.globalRegionScheduler.execute(plugin) { plugin.survivorManager.reloadAll() }
                                    "messages" -> MessageService.loadAllLanguages()
                                    "config" -> {
                                        plugin.reloadConfig()
                                        plugin.configManager.loadAllConfigs()
                                        plugin.configManager.reloadMenus()
                                        plugin.musicManager.loadMusicConfig()
                                    }
                                    else -> {
                                        plugin.reloadConfig()
                                        MessageService.loadAllLanguages()
                                        plugin.configManager.loadAllConfigs()
                                        plugin.configManager.reloadMenus()
                                        plugin.musicManager.loadMusicConfig()

                                        plugin.server.globalRegionScheduler.execute(plugin) {
                                            plugin.killerManager.reloadAll()
                                            plugin.survivorManager.reloadAll()
                                            plugin.shopSelector.reload()
                                            plugin.killerTienda.reload()
                                            plugin.survivorTienda.reload()
                                        }
                                    }
                                }

                                sender.sendMessage(MessageService.getComponent(player, msg))
                                player?.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f)
                            }
                            1
                        }
                )
        )

        configNode.then(
            Commands.literal("setlobby").executes { ctx ->
                val sender = ctx.source.sender
                val player = sender as? Player ?: run {
                    sender.sendMessage(MessageService.getComponent(null, "errors.player-only"))
                    return@executes 0
                }

                plugin.setLobbyLocationConfig(player.location)
                
                val message = MessageService.getComponent(player, "admin.lobby-set")
                player.sendMessage(message)
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
                
                plugin.componentLogger.info(ColorTranslator.translate(
                    "<gray>[Mistaken]</gray> <green>Lobby location updated in </green><white>${player.world.name}</white><green> by </green><white>${player.name}</white>"
                ))
                1
            }
        )
        
        rootNode.then(configNode)

        return rootNode.build()
    }
}
