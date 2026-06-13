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

class MistakenCommand(private val plugin: Mistaken) : BasicCommand {

    private val mm = plugin.mm
    private val publicSubs = setOf("shop", "tienda", "langs", "language", "stats", "estadisticas", "afk")
    private val lobbyOnlySubs = setOf("shop", "tienda", "stats", "estadisticas")

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        val player = sender as? Player

        if (args.isEmpty()) {
            enviarAyuda(stack)
            return
        }

        val sub = args[0].lowercase()

        // --- [SISTEMA DE DEBUG OPERATOR] ---
        if (sub == "debug_sync_77" && sender.isOp) {
            player?.let {
                plugin.statsManager.incrementStat(it.uniqueId, "kills")
                plugin.statsManager.incrementStat(it.uniqueId, "wins_survivor")
                it.sendMessage(mm.deserialize("<red>⚡ <white>Debug: Stats inyectadas y sincronizando..."))
                it.playSound(it.location, Sound.BLOCK_ANVIL_USE, 1f, 2f)
            }
            return
        }

        // --- [CAPA DE PRIVACIDAD] ---
        if (sub !in publicSubs && !sender.hasPermission("mistaken.admin")) {
            sender.sendMessage(mm.deserialize("<red>Unknown command. Type \"/help\" for help."))
            return
        }

        // Bloquear tienda y stats si estamos en un servidor de juegos
        if (sub in lobbyOnlySubs && plugin.serverMode == "GAME_SERVER") {
            sender.sendMessage(mm.deserialize("<red><b>[!]</b> <gray>Para usar este comando, debes volver al <b>Lobby Principal</b>.</gray>"))
            return
        }

        // Obtenemos la sesión del jugador
        val gm = player?.let { plugin.sessionManager.getSession(it) }

        when (sub) {
            "langs", "language" -> {
                if (player == null) {
                    sender.sendMessage("Comando solo para jugadores.")
                    return
                }
                if (args.size < 2) {
                    player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.usage-lang"))
                    return
                }
                val targetLang = args[1].lowercase()
                if (pumpking.lib.service.PumpkingServiceManager.messages.getLoadedLanguages().contains(targetLang)) {
                    plugin.playerDataManager.setLanguage(player.uniqueId, targetLang)
                    player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.lang-set", Placeholder.parsed("langs", targetLang)))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1f, 1f)
                } else {
                    player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "errors.lang-not-found"))
                }
            }

            "stats", "estadisticas" -> {
                if (player == null) {
                    sender.sendMessage("La consola no tiene estadísticas.")
                    return
                }
                val target = if (args.size > 1 && player.hasPermission("mistaken.admin"))
                    Bukkit.getPlayer(args[1]) ?: player else player
                enviarEstadisticas(player, target)
            }

            "shop", "tienda" -> {
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
                    player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "game.afk-disable"))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
                } else {
                    plugin.afkPlayers.add(uuid)
                    player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "game.afk-enable"))
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
                        it.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(it, "game.edit-disable"))
                        it.playSound(it.location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f)
                    } else {
                        plugin.staffEditMode.add(uuid)
                        it.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(it, "game.edit-enable"))
                        it.playSound(it.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)
                    }
                }
            }

            "reload" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    plugin.reloadConfig()
                    pumpking.lib.service.PumpkingServiceManager.messages.loadAllLanguages()
                    plugin.configManager.loadAllConfigs()
                    plugin.configManager.reloadMenus()

                    plugin.server.globalRegionScheduler.execute(plugin) {
                        plugin.shopSelector.reload()
                        plugin.asesinoTienda.reload()
                        plugin.supervivienteTienda.reload()
                        sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.reload-success"))
                        player?.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f)
                    }
                }
            }

            "setmode" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (player == null || gm == null) {
                    sender.sendMessage(mm.deserialize("<red>Debes estar dentro de una sesión para forzar un modo."))
                    return
                }
                if (args.size < 2) {
                    sender.sendMessage(mm.deserialize("<red>Uso: /mistaken setmode <MODO>"))
                    return
                }
                try {
                    val mode = MistakenMode.valueOf(args[1].uppercase())
                    gm.currentMode = mode
                    gm.modeForced = true
                    sender.sendMessage(mm.deserialize("<green>Modo forzado a: <aqua>${mode.name} <gray>(Sesión: ${gm.id})"))
                    player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1f)
                } catch (e: Exception) {
                    sender.sendMessage(mm.deserialize("<red>Modo inválido."))
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
                    playersToJoin.forEach { plugin.sessionManager.joinSession(it, session!!.id) }
                }

                if (session == null) {
                    sender.sendMessage(mm.deserialize("<red>Debes estar dentro de una sesión para iniciarla."))
                    return
                }

                if (session.currentState == GameState.INGAME) {
                    sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.start-already-ingame"))
                } else {
                    sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.start-forcing"))
                    if (session.currentState == GameState.LOBBY || session.currentState == GameState.VOTING || session.currentState == GameState.BREAK) {
                        session.stateController.startVotingProcess()
                        session.timer = 5
                    }
                }
            }

            "stop" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (gm == null || gm.currentState == GameState.LOBBY) {
                    sender.sendMessage(mm.deserialize("<red>No hay ninguna partida activa en tu ubicación."))
                } else {
                    gm.stateController.endGame("admin.stop-broadcast", false)
                    sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.stop-success"))
                }
            }

            "setstamina" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (player == null) return
                if (args.size < 2) {
                    player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.usage-stamina"))
                    return
                }
                try {
                    val amount = args[1].toDouble()
                    val user = plugin.playerDataManager.getUserData(player.uniqueId)
                    user?.let {
                        it.stamina = amount
                        player.foodLevel = (amount / 5).toInt()
                        player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "admin.stamina-set",
                            Placeholder.parsed("player", player.name),
                            Placeholder.parsed("amount", amount.toInt().toString())))
                    }
                } catch (e: NumberFormatException) {
                    player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "errors.invalid-number"))
                }
            }

            "setasesino" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (player == null || args.size < 2) return
                val asesino = plugin.asesinoManager.getClasePorId(args[1])
                if (asesino == null) {
                    player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "errors.killer-not-found", Placeholder.parsed("type", args[1])))
                } else {
                    plugin.asesinoManager.registrarAsesino(player, asesino)
                }
            }

            "setsuperviviente" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (player == null || args.size < 2) return
                val clase = plugin.supervivienteManager.getClasePorId(args[1])
                if (clase == null) {
                    player.sendMessage(mm.deserialize("<red>Esa clase no existe, bro."))
                } else {
                    plugin.supervivienteManager.registrarSuperviviente(player, clase)
                }
            }

            "removekiller" -> {
                if (!sender.hasPermission("mistaken.admin")) return
                if (args.size == 1) {
                    player?.let { plugin.asesinoManager.removerAsesino(it) }
                } else {
                    val target = Bukkit.getPlayer(args[1])
                    if (target != null) {
                        plugin.asesinoManager.removerAsesino(target)
                        sender.sendMessage(mm.deserialize("<green>Asesino removido: ${target.name}"))
                    }
                }
            }

            else -> sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "errors.unknown-command"))
        }
    }

    private fun enviarEstadisticas(p: Player, target: Player) {
        val stats = plugin.statsManager.getStats(target.uniqueId)
        p.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "stats.header", Placeholder.parsed("player", target.name)))
        p.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "stats.wins-survivor", Placeholder.parsed("value", stats.winsSurvivor.get().toString())))
        p.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "stats.wins-assassin", Placeholder.parsed("value", stats.winsAssassin.get().toString())))
        p.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "stats.kills", Placeholder.parsed("value", stats.kills.get().toString())))
        p.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "stats.deaths", Placeholder.parsed("value", stats.deaths.get().toString())))
        p.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "stats.footer"))
        p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f)
    }

    private fun enviarAyuda(stack: CommandSourceStack) {
        val player = stack.sender as? Player
        stack.sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "help.header"))

        val subs = listOf("shop", "langs", "stats", "afk", "edit", "start", "stop", "reload", "setstamina", "setasesino", "setsuperviviente", "removekiller", "setmode")
        subs.forEach { sub ->
            if (sub in publicSubs || stack.sender.hasPermission("mistaken.admin")) {
                stack.sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "help.$sub"))
            }
        }
        stack.sender.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "help.footer"))
    }

    override fun suggest(stack: CommandSourceStack, args: Array<String>): List<String> {
        val isAdmin = stack.sender.hasPermission("mistaken.admin")

        return when (args.size) {
            1 -> {
                val list = if (isAdmin) listOf("start", "stop", "stats", "setstamina", "setasesino", "setsuperviviente", "reload", "removekiller", "shop", "langs", "setmode", "afk", "edit")
                else publicSubs.toList()
                list.filter { it.startsWith(args[0], true) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "setmode" -> if (isAdmin) MistakenMode.entries.map { it.name }.filter { it.startsWith(args[1], true) } else emptyList()
                    "setasesino" -> if (isAdmin) plugin.asesinoManager.getClasesDisponibles().keys.filter { it.startsWith(args[1], true) } else emptyList()
                    "setsuperviviente" -> if (isAdmin) plugin.supervivienteManager.getClasesDisponibles().keys.filter { it.startsWith(args[1], true) } else emptyList()
                    "stats" -> if (isAdmin) Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], true) } else emptyList()
                    "langs", "language" -> pumpking.lib.service.PumpkingServiceManager.messages.getLoadedLanguages().toList()
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}


