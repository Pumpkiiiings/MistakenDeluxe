package liric.mistaken.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import liric.mistaken.Mistaken
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * [LIRIC-MISTAKEN 2.0]
 * ArenaCommand: Gestión administrativa de mapas.
 * Optimizado con la API de Brigadier para Paper 1.21.4.
 */
class ArenaCommand(private val plugin: Mistaken) : BasicCommand {

    private val allowedGens = setOf(
        Material.RAW_IRON_BLOCK,
        Material.IRON_BLOCK,
        Material.AMETHYST_BLOCK
    )

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        val player = sender as? Player ?: run {
            sender.sendMessage("Este comando es exclusivo para jugadores.")
            return
        }

        // Filtro de seguridad: Si no es admin, fingimos que el comando no existe
        if (!sender.hasPermission("mistaken.admin")) {
            sender.sendMessage(Mistaken.mm.deserialize("<red>Unknown command. Type \"/help\" for help."))
            return
        }

        if (args.size < 2) {
            sendHelp(player)
            return
        }

        val sub = args[0].lowercase()
        val arenaName = args[1]
        val arena = plugin.arenaManager.getArena(arenaName)

        // Validar existencia excepto para creación
        if (arena == null && sub != "create") {
            player.sendMessage(plugin.messageConfig.getMessage(player, "errors.arena-not-found",
                Placeholder.parsed("name", arenaName)))
            return
        }

        when (sub) {
            "create" -> {
                plugin.arenaManager.createArena(arenaName)
                player.sendMessage(plugin.messageConfig.getMessage(player, "arena.created",
                    Placeholder.parsed("name", arenaName)))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1f, 1f)
                player.sendMessage(Mistaken.mm.deserialize("<gray>Nota: Asegúrate de tener el archivo <white>$arenaName.slime</white> en su carpeta."))
            }

            "delete" -> {
                plugin.arenaManager.deleteArena(arenaName)
                player.sendMessage(plugin.messageConfig.getMessage(player, "arena.deleted",
                    Placeholder.parsed("name", arenaName)))
                player.playSound(player.location, Sound.BLOCK_ANVIL_BREAK, 1f, 1f)
            }

            "setspawn" -> {
                if (args.size < 3) {
                    player.sendMessage(plugin.messageConfig.getMessage(player, "arena.usage-setspawn",
                        Placeholder.parsed("name", arenaName)))
                    return
                }
                val type = args[2].lowercase()
                if (type == "asesino" || type == "survivor") {
                    plugin.arenaManager.setSpawn(arenaName, type, player.location)
                    player.sendMessage(plugin.messageConfig.getMessage(player, "arena.setspawn",
                        Placeholder.parsed("type", type),
                        Placeholder.parsed("name", arenaName)))
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                } else {
                    player.sendMessage(plugin.messageConfig.getMessage(player, "errors.invalid-spawn-type"))
                }
            }

            "setgenerator" -> {
                val target = player.getTargetBlockExact(5)
                if (target == null || target.type !in allowedGens) {
                    player.sendMessage(plugin.messageConfig.getMessage(player, "errors.invalid-gen-block"))
                    return
                }
                plugin.arenaManager.addGenerator(arenaName, target.location)
                player.sendMessage(plugin.messageConfig.getMessage(player, "arena.setgenerator",
                    Placeholder.parsed("name", arenaName)))
                player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f)
            }

            "delgenerator" -> {
                val target = player.getTargetBlockExact(5) ?: return
                arena?.let {
                    val currentGens = it.generators.toMutableList()
                    val removed = currentGens.removeIf { loc ->
                        loc.blockX == target.x && loc.blockY == target.y && loc.blockZ == target.z
                    }

                    if (removed) {
                        plugin.arenaManager.saveGenerators(arenaName, currentGens)
                        player.sendMessage(plugin.messageConfig.getMessage(player, "arena.delgenerator",
                            Placeholder.parsed("name", arenaName)))
                        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
                    } else {
                        player.sendMessage(plugin.messageConfig.getMessage(player, "errors.not-a-generator"))
                    }
                }
            }

            "check" -> {
                arena?.let {
                    val ready = it.asesinoSpawn != null && it.survivorSpawns.isNotEmpty() && it.generators.isNotEmpty()
                    val statusKey = if (ready) "arena.status-ready" else "arena.status-incomplete"
                    val statusText = plugin.messageConfig.getRawString(player, statusKey, "Unknown")

                    player.sendMessage(plugin.messageConfig.getMessage(player, "arena.check-header",
                        Placeholder.parsed("name", arenaName),
                        Placeholder.parsed("status", statusText)))

                    player.sendMessage(plugin.messageConfig.getMessage(player, "arena.check-survivors",
                        Placeholder.parsed("count", it.survivorSpawns.size.toString())))

                    player.sendMessage(plugin.messageConfig.getMessage(player, "arena.check-generators",
                        Placeholder.parsed("count", it.generators.size.toString())))

                    val killerIcon = if (it.asesinoSpawn != null) "<green>✔" else "<red>✘"
                    player.sendMessage(plugin.messageConfig.getMessage(player, "arena.check-killer",
                        Placeholder.parsed("icon", killerIcon)))

                    player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1f, 1.5f)
                }
            }
            else -> sendHelp(player)
        }
    }

    private fun sendHelp(p: Player) {
        p.sendMessage(plugin.messageConfig.getMessage(p, "arena.help-header"))
        listOf("create", "delete", "check", "setspawn", "setgenerator", "delgenerator").forEach { sub ->
            p.sendMessage(plugin.messageConfig.getMessage(p, "arena.help-line-$sub"))
        }
        p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f)
    }

    /**
     * Autocompletado optimizado nativo de Brigadier.
     */
    override fun suggest(stack: CommandSourceStack, args: Array<String>): List<String> {
        if (!stack.sender.hasPermission("mistaken.admin")) return emptyList()

        return when (args.size) {
            1 -> listOf("create", "delete", "check", "setspawn", "setgenerator", "delgenerator")
                .filter { it.startsWith(args[0], true) }
            2 -> plugin.arenaManager.getArenas().keys
                .filter { it.startsWith(args[1], true) }
            3 -> if (args[0].equals("setspawn", true)) listOf("asesino", "survivor").filter { it.startsWith(args[2], true) }
            else emptyList()
            else -> emptyList()
        }
    }
}
