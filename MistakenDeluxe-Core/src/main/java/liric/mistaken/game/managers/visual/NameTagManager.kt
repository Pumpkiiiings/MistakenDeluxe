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
            entity.isSeeThrough = false // No se ve a través de las paredes
            entity.billboard = Billboard.CENTER // Siempre mira a la cámara
            entity.isDefaultBackground = false
            entity.backgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0)
            entity.text(ColorTranslator.translate("<gray>${player.name}"))
            
            // Subir el texto aprox 2.3 bloques
            entity.transformation = Transformation(
                Vector3f(0f, 2.3f, 0f),
                AxisAngle4f(0f, 0f, 0f, 1f),
                Vector3f(1f, 1f, 1f),
                AxisAngle4f(0f, 0f, 0f, 1f)
            )
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
        var color = "<white>"
        
        if (session != null) {
            if (session.isKiller(player.uniqueId)) {
                color = "<red>"
            } else {
                color = "<green>"
            }
        } else {
            color = "<gray>"
        }

        display.text(ColorTranslator.translate("$color${player.name}"))
    }
}
