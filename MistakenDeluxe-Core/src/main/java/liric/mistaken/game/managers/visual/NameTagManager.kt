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
        val board = Bukkit.getScoreboardManager().mainScoreboard
        var team = board.getTeam("mistaken_hide")
        if (team == null) {
            team = board.registerNewTeam("mistaken_hide")
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
        
        // Agregar a los que ya estén online
        Bukkit.getOnlinePlayers().forEach { team.addEntry(it.name) }
    }

    fun setupPlayer(player: Player) {
        val board = Bukkit.getScoreboardManager().mainScoreboard
        val team = board.getTeam("mistaken_hide")
        team?.addEntry(player.name)

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
        val board = Bukkit.getScoreboardManager().mainScoreboard
        board.getTeam("mistaken_hide")?.removeEntry(player.name)

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
        val bgColorStr = plugin.config.getString("$configPath.background-color", "0,0,0,0") ?: "0,0,0,0"

        val bgParts = bgColorStr.split(",").map { it.trim().toIntOrNull() ?: 0 }
        val bgColor = org.bukkit.Color.fromARGB(
            if (bgParts.isNotEmpty()) bgParts[0] else 0,
            if (bgParts.size > 1) bgParts[1] else 0,
            if (bgParts.size > 2) bgParts[2] else 0,
            if (bgParts.size > 3) bgParts[3] else 0
        )

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
            Vector3f(0f, 2.3f, 0f),
            AxisAngle4f(0f, 0f, 0f, 1f),
            Vector3f(size, size, size),
            AxisAngle4f(0f, 0f, 0f, 1f)
        )
    }
}
