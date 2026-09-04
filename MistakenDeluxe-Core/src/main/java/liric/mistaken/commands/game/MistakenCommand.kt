package liric.mistaken.commands.game

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.Mistaken
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.enums.MistakenMode
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import liric.mistaken.config.Messages
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService

class MistakenCommand(private val plugin: Mistaken) : BasicCommand {

    private val mm = plugin.mm
    private val publicSubs = setOf("shop", "langs", "language", "stats", "afk")
    private val lobbyOnlySubs = setOf("shop", "stats")

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        val player = sender as? Player

        if (args.isEmpty()) {
            enviarAyuda(stack)
            return
        }

        val sub = args[0].lowercase()

        
        if (sub == "debug_sync_77" && sender.isOp) {
            player?.let {
                plugin.statsManager.incrementStat(it.uniqueId, "kills")
                plugin.statsManager.incrementStat(it.uniqueId, "wins_survivor")
                it.sendMessage(MessageService.getComponent(player, "admin.debug-stats-sync"))
                it.playSound(it.location, Sound.BLOCK_ANVIL_USE, 1f, 2f)
            }
            return
        }

        
        if (sub !in publicSubs && !sender.hasPermission("mistaken.admin")) {
            sender.sendMessage(MessageService.getComponent(player, "errors.unknown-command"))
            return
        }

        
        if (sub in lobbyOnlySubs && plugin.serverMode == "GAME_SERVER") {
            sender.sendMessage(MessageService.getComponent(player, "errors.lobby-only-command"))
            return
        }

        
        val gm = player?.let { plugin.sessionManager.getSession(it) }

        when (sub) {
            "langs", "language" -> {
                if (player == null) {
                    sender.sendMessage(MessageService.getComponent(null, "errors.player-only"))
                    return
                }
                if (args.size < 2) {
                    player.sendMessage(MessageService.getComponent(player, "admin.usage-lang"))
                    return
                }
                val targetLang = args[1].lowercase()
                if (MessageService.getLoadedLanguages().contains(targetLang)) {
                    plugin.playerDataManager.setLanguage(player.uniqueId, targetLang)
                    player.sendMessage(MessageService.getComponent(player, "admin.lang-set", Placeholder.parsed("langs", targetLang)))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1f, 1f)
                } else {
                    player.sendMessage(MessageService.getComponent(player, "errors.lang-not-found"))
                }
            }

            "stats" -> {
                if (player == null) {
                    sender.sendMessage(MessageService.getComponent(null, "errors.player-only"))
                    return
                }
                val target = if (args.size > 1 && player.hasPermission("mistaken.admin"))
                    Bukkit.getPlayer(args[1]) ?: player else player
                enviarEstadisticas(player, target)
            }

            "shop" -> {
                player?.let {
                    plugin.shopSelector.abrir(it)
                    it.playSound(it.location, Sound.BLOCK_CHEST_OPEN, 1f, 1.2f)
                }
            }

            "afk" -> {
                if (player == null) return
                val uuid = player.uniqueId
                if (plugin.afkPlayers.contains(uuid)) {
                    plugin.afkPlayers.remove(uuid)
                    player.sendMessage(MessageService.getComponent(player, "game.afk-disable"))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
                } else {
                    plugin.afkPlayers.add(uuid)
                    player.sendMessage(MessageService.getComponent(player, "game.afk-enable"))
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.5f)

                    gm?.playerController?.checkWinCondition()
                }
            }

            "edit" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                player?.let {
                    val uuid = it.uniqueId
                    if (plugin.staffEditMode.contains(uuid)) {
                        plugin.staffEditMode.remove(uuid)
                        it.sendMessage(MessageService.getComponent(it, "game.edit-disable"))
                        it.playSound(it.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f)
                    } else {
                        plugin.staffEditMode.add(uuid)
                        it.sendMessage(MessageService.getComponent(it, "game.edit-enable"))
                        it.playSound(it.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)
                    }
                }
            }

            "reload" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                val target = if (args.size > 1) args[1].lowercase() else "all"

                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    var msg = "admin.reload-success"
                    
                    when (target) {
                        "scripts" -> {
                            plugin.server.globalRegionScheduler.execute(plugin) {
                                plugin.killerManager.reloadAll()
                                plugin.survivorManager.reloadAll()
                            }
                            msg = "admin.reload-success"
                        }
                        "killers" -> {
                            plugin.server.globalRegionScheduler.execute(plugin) {
                                plugin.killerManager.reloadAll()
                            }
                        }
                        "survivors" -> {
                            plugin.server.globalRegionScheduler.execute(plugin) {
                                plugin.survivorManager.reloadAll()
                            }
                        }
                        "messages" -> {
                            MessageService.loadAllLanguages()
                        }
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
            }

            "setmode" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (player == null || gm == null) {
                    sender.sendMessage(MessageService.getComponent(player, "errors.not-in-session"))
                    return
                }
                if (args.size < 2) {
                    sender.sendMessage(MessageService.getComponent(player, "admin.usage-setmode"))
                    return
                }
                try {
                    val mode = MistakenMode.valueOf(args[1].uppercase())
                    gm.currentMode = mode
                    gm.modeForced = true
                    sender.sendMessage(MessageService.getComponent(player, "admin.mode-forced",
                        Placeholder.parsed("mode", mode.name),
                        Placeholder.parsed("session", gm.id)))
                    player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1f)
                } catch (e: Exception) {
                    sender.sendMessage(MessageService.getComponent(player, "errors.invalid-mode"))
                }
            }

            "start" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                var session = gm

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
                    return
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
            }

            "stop" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (gm == null || gm.currentState == GameState.LOBBY) {
                    sender.sendMessage(MessageService.getComponent(player, "errors.no-active-game"))
                } else {
                    gm.stateController.endGame("admin.stop-broadcast", false)
                    sender.sendMessage(MessageService.getComponent(player, "admin.stop-success"))
                }
            }

            "setstamina" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (player == null) return
                if (args.size < 2) {
                    player.sendMessage(MessageService.getComponent(player, "admin.usage-stamina"))
                    return
                }
                try {
                    val amount = args[1].toDouble()
                    val user = plugin.playerDataManager.getUserData(player.uniqueId)
                    user?.let {
                        it.stamina = amount
                        player.foodLevel = (amount / 5).toInt()
                        player.sendMessage(MessageService.getComponent(player, "admin.stamina-set",
                            Placeholder.parsed("player", player.name),
                            Placeholder.parsed("amount", amount.toInt().toString())))
                    }
                } catch (e: NumberFormatException) {
                    player.sendMessage(MessageService.getComponent(player, "errors.invalid-number"))
                }
            }

            "setkiller" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (player == null || args.size < 2) return
                val killer = plugin.killerManager.getClassById(args[1])
                if (killer == null) {
                    player.sendMessage(MessageService.getComponent(player, "errors.killer-not-found", Placeholder.parsed("type", args[1])))
                } else {
                    plugin.killerManager.registerKiller(player, killer)
                }
            }

            "setsurvivor" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (player == null || args.size < 2) return
                val clase = plugin.survivorManager.getClassById(args[1])
                if (clase == null) {
                    player.sendMessage(MessageService.getComponent(player, "errors.survivor-not-found", Placeholder.parsed("type", args[1])))
                } else {
                    plugin.survivorManager.registrarSurvivor(player, clase as liric.mistaken.roles.survivors.Survivor)
                }
            }

            "removekiller" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (args.size == 1) {
                    player?.let { plugin.killerManager.removeKiller(it) }
                } else {
                    val target = Bukkit.getPlayer(args[1])
                    if (target != null) {
                        plugin.killerManager.removeKiller(target)
                        sender.sendMessage(MessageService.getComponent(player, "admin.removekiller-success", Placeholder.parsed("player", target.name)))
                    }
                }
            }
            
            "reloadkiller" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (args.size < 2) {
                    sender.sendMessage(MessageService.getComponent(player, "admin.usage-reloadkiller"))
                    return
                }
                val id = args[1]
                plugin.killerManager.reloadKiller(id)
                sender.sendMessage(MessageService.getComponent(player, "admin.reloadkiller-success", Placeholder.parsed("id", id)))
            }

            "forcekiller" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (args.size < 2) {
                    sender.sendMessage(MessageService.getComponent(player, "admin.usage-forcekiller"))
                    return
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage(MessageService.getComponent(player, "errors.player-not-found"))
                    return
                }
                if (gm == null) {
                    sender.sendMessage(MessageService.getComponent(player, "errors.forcekiller-not-in-game"))
                    return
                }
                if (gm.currentState == GameState.INGAME || gm.currentState == GameState.ENDING || gm.currentState == GameState.STARTING) {
                    sender.sendMessage(MessageService.getComponent(player, "errors.forcekiller-already-started"))
                    return
                }
                gm.forcedKillerUUID = target.uniqueId
                sender.sendMessage(MessageService.getComponent(player, "admin.forcekiller-success", 
                    Placeholder.parsed("player", target.name),
                    Placeholder.parsed("arena", gm.id)))
            }

            else -> sender.sendMessage(MessageService.getComponent(player, "errors.unknown-command"))
        }
    }

    private fun enviarEstadisticas(p: Player, target: Player) {
        val stats = plugin.statsManager.getStats(target.uniqueId)
        p.sendMessage(MessageService.getComponent(p, "stats.header", Placeholder.parsed("player", target.name)))
        p.sendMessage(MessageService.getComponent(p, "stats.wins-survivor", Placeholder.parsed("value", stats.winsSurvivor.get().toString())))
        p.sendMessage(MessageService.getComponent(p, "stats.wins-assassin", Placeholder.parsed("value", stats.winsAssassin.get().toString())))
        p.sendMessage(MessageService.getComponent(p, "stats.kills", Placeholder.parsed("value", stats.kills.get().toString())))
        p.sendMessage(MessageService.getComponent(p, "stats.deaths", Placeholder.parsed("value", stats.deaths.get().toString())))
        p.sendMessage(MessageService.getComponent(p, "stats.footer"))
        p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f)
    }

    private fun enviarAyuda(stack: CommandSourceStack) {
        val player = stack.sender as? Player
        stack.sender.sendMessage(MessageService.getComponent(player, "help.header"))

        val subs = listOf("shop", "langs", "stats", "afk", "edit", "start", "stop", "reload", "setstamina", "setkiller", "setsurvivor", "removekiller", "reloadkiller", "setmode")
        subs.forEach { sub ->
            if (sub in publicSubs || stack.sender.hasPermission("mistaken.admin")) {
                stack.sender.sendMessage(MessageService.getComponent(player, "help.$sub"))
            }
        }
        stack.sender.sendMessage(MessageService.getComponent(player, "help.footer"))
    }

    override fun suggest(stack: CommandSourceStack, args: Array<String>): List<String> {
        val isAdmin = stack.sender.hasPermission("mistaken.admin")

        return when (args.size) {
            1 -> {
                val list = if (isAdmin) listOf("start", "stop", "stats", "setstamina", "setkiller", "setsurvivor", "reload", "removekiller", "reloadkiller", "forcekiller", "shop", "langs", "setmode", "afk", "edit")
                else publicSubs.toList()
                list.filter { it.startsWith(args[0], true) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "setmode" -> if (isAdmin) MistakenMode.entries.map { it.name }.filter { it.startsWith(args[1], true) } else emptyList()
                    "reload" -> if (isAdmin) listOf("scripts", "killers", "survivors", "messages", "config", "all").filter { it.startsWith(args[1], true) } else emptyList()
                    "setkiller", "reloadkiller" -> if (isAdmin) plugin.killerManager.getAvailableClasses().keys.filter { it.startsWith(args[1], true) } else emptyList()
                    "setsurvivor" -> if (isAdmin) plugin.survivorManager.getAvailableClasses().keys.filter { it.startsWith(args[1], true) } else emptyList()
                    "stats", "forcekiller", "removekiller" -> if (isAdmin) Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], true) } else emptyList()
                    "langs", "language" -> MessageService.getLoadedLanguages().toList()
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
