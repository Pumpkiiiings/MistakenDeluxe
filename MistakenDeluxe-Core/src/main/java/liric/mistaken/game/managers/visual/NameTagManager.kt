package liric.mistaken.game.managers.visual

import liric.mistaken.Mistaken
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.entity.Display.Billboard
import org.bukkit.scoreboard.Team
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import pumpking.lib.color.ColorTranslator
import java.util.UUID

class NameTagManager(private val plugin: Mistaken) {

    private val displays = HashMap<UUID, TextDisplay>()
    private val hasPAPI by lazy { Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null }

    init {
        setupGlobalTeam()
    }

    private fun setupGlobalTeam() {
        // En caso de que se use el mainScoreboard
        val board = Bukkit.getScoreboardManager().mainScoreboard
        var team = board.getTeam("mistaken_hide")
        if (team == null) {
            team = board.registerNewTeam("mistaken_hide")
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
        
        Bukkit.getOnlinePlayers().forEach { team.addEntry(it.name) }
    }

    fun setupPlayer(player: Player) {
        // Añadir este jugador a los scoreboards personales de TODOS los jugadores online
        Bukkit.getOnlinePlayers().forEach { online ->
            val board = online.scoreboard
            var team = board.getTeam("mistaken_hide")
            if (team == null) {
                team = board.registerNewTeam("mistaken_hide")
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
            }
            if (!team.hasEntry(player.name)) {
                team.addEntry(player.name)
            }
        }

        if (displays.containsKey(player.uniqueId)) {
            removePlayer(player)
        }

        val display = player.world.spawn(player.location, TextDisplay::class.java) { entity ->
            entity.isSeeThrough = false
            entity.billboard = Billboard.CENTER
            entity.isDefaultBackground = false
            entity.backgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0)
            entity.text(net.kyori.adventure.text.Component.empty())
        }
        
        player.addPassenger(display)
        displays[player.uniqueId] = display
    }

    fun removePlayer(player: Player) {
        Bukkit.getOnlinePlayers().forEach { online ->
            val board = online.scoreboard
            board.getTeam("mistaken_hide")?.removeEntry(player.name)
        }

        displays.remove(player.uniqueId)?.remove()
    }

    fun removeAll() {
        displays.values.forEach { it.remove() }
        displays.clear()
    }

    fun updatePlayer(player: Player) {
        val display = displays[player.uniqueId] ?: return
        if (!display.isValid) return

        // Asegurarse de que esté montado
        if (!player.passengers.contains(display)) {
            player.addPassenger(display)
        }

        
        if (plugin.spectatorManager.isSpectator(player) || player.gameMode == GameMode.SPECTATOR || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) {
            display.text(net.kyori.adventure.text.Component.empty())
            return
        }

        val session = plugin.sessionManager.getSession(player)
        val isIngame = session != null

        val configPath = if (isIngame) "nametags.ingame" else "nametags.global"

        val lines = plugin.config.getStringList("$configPath.lines")
        val size = plugin.config.getDouble("$configPath.size", 1.0).toFloat()
        val shadow = plugin.config.getBoolean("$configPath.shadow", false)
        val bgColorStr = plugin.config.getString("$configPath.background-color", "transparent")?.lowercase() ?: "transparent"

        val bgColor = if (bgColorStr == "transparent") {
            org.bukkit.Color.fromARGB(0, 0, 0, 0)
        } else if (bgColorStr.startsWith("#")) {
            try {
                val hex = bgColorStr.removePrefix("#")
                when (hex.length) {
                    6 -> {
                        val rgb = hex.toInt(16)
                        org.bukkit.Color.fromARGB(255, (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                    }
                    8 -> {
                        val argb = hex.toLong(16)
                        org.bukkit.Color.fromARGB(((argb shr 24) and 0xFF).toInt(), ((argb shr 16) and 0xFF).toInt(), ((argb shr 8) and 0xFF).toInt(), (argb and 0xFF).toInt())
                    }
                    else -> org.bukkit.Color.fromARGB(0, 0, 0, 0)
                }
            } catch (e: Exception) {
                org.bukkit.Color.fromARGB(0, 0, 0, 0)
            }
        } else {
            org.bukkit.Color.fromARGB(0, 0, 0, 0)
        }

        val colorStr = if (isIngame) {
            if (session!!.isKiller(player.uniqueId)) "<red>" else "<green>"
        } else {
            "<gray>"
        }

        val health = String.format(java.util.Locale.US, "%.1f", player.health)

        val processedLines = lines.map { line ->
            var currentLine = line
                .replace("%name%", player.name)
                .replace("%color%", colorStr)
                .replace("%health%", health)
            
            if (hasPAPI) {
                currentLine = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, currentLine)
            }
            currentLine
        }.joinToString("<br>")

        display.text(ColorTranslator.translate(processedLines))
        display.backgroundColor = bgColor
        display.isShadowed = shadow
        display.transformation = Transformation(
            Vector3f(0f, 0.4f, 0f),
            AxisAngle4f(0f, 0f, 0f, 1f),
            Vector3f(size, size, size),
            AxisAngle4f(0f, 0f, 0f, 1f)
        )
    }
}
