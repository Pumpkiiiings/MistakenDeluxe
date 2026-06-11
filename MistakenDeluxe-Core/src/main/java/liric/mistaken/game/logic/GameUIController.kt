package liric.mistaken.game.logic

import liric.mistaken.game.GameSession
import liric.mistaken.game.enums.GameState
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.PrefixNode
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameUIController(private val game: GameSession) {

    private val personalBars = ConcurrentHashMap<UUID, BossBar>()
    private val lastProcessedText = ConcurrentHashMap<UUID, String>()

    fun clearBossBars() {
        personalBars.forEach { (uuid, bar) ->
            game.plugin.server.getPlayer(uuid)?.hideBossBar(bar)
        }
        personalBars.clear()
        lastProcessedText.clear()
    }

    fun updatePersonalBar(p: Player, online: Int) {
        val uuid = p.uniqueId
        val stateName = game.currentState.name.lowercase()

        val mins = game.timer / 60
        val secs = game.timer % 60
        val timeStr = if (game.currentState == GameState.INGAME || game.currentState == GameState.STARTING) {
            String.format("%02d:%02d", mins, secs)
        } else {
            game.timer.toString()
        }

        val lobbyWord = game.pumpking.lib.service.PumpkingServiceManager.messages.getRawString(p, "words.lobby", "Lobby", "messages")

        val mapDisplay = if (game.currentState == GameState.LOBBY || game.currentState == GameState.VOTING || game.currentState == GameState.BREAK) {
            lobbyWord
        } else {
            game.currentMapName
        }

        val signature = "S:$stateName|T:$timeStr|O:$online|M:$mapDisplay|MD:${game.currentMode.name}"

        if (lastProcessedText[uuid] == signature) return
        lastProcessedText[uuid] = signature

        val barComponent = game.plugin.messageConfig.getMessageFromFile(
            p, "messages", "bossbar.$stateName",
            Placeholder.parsed("time", timeStr),
            Placeholder.parsed("map", mapDisplay),
            Placeholder.parsed("mode", game.currentMode.name),
            Placeholder.parsed("online", online.toString())
        )

        val bar = personalBars.getOrPut(uuid) {
            val colorStr = game.pumpking.lib.service.PumpkingServiceManager.messages.getRawString(p, "bossbar.colors.$stateName", "WHITE", "messages")
            val color = try { BossBar.Color.valueOf(colorStr.uppercase()) } catch (e: Exception) { BossBar.Color.WHITE }

            val newBar = BossBar.bossBar(barComponent, 1.0f, color, BossBar.Overlay.PROGRESS)
            p.showBossBar(newBar)
            newBar
        }

        bar.name(barComponent)
        val colorStr = game.pumpking.lib.service.PumpkingServiceManager.messages.getRawString(p, "bossbar.colors.$stateName", "WHITE", "messages")
        try { bar.color(BossBar.Color.valueOf(colorStr.uppercase())) } catch (_: Exception) {}
    }

    fun playRoleTitle(p: Player, isKiller: Boolean) {
        val rp = if (isKiller) "killer" else "survivor"
        p.showTitle(Title.title(
            game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "roles.$rp.title"),
            game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "roles.$rp.subtitle")
        ))
    }

    fun playModeTitle(players: Collection<Player>) {
        players.forEach { p ->
            p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)
            p.showTitle(Title.title(
                game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "modes.${game.currentMode.name.lowercase()}.title"),
                game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "modes.${game.currentMode.name.lowercase()}.subtitle"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ))
        }
    }

    fun broadcastLMS(lastSurvivor: Player) {
        val parsedSurvivorName = Placeholder.parsed("player", lastSurvivor.name)

        game.plugin.server.onlinePlayers.forEach { p ->
            val isKiller = game.esAsesino(p.uniqueId)
            val isTheSurvivor = p.uniqueId == lastSurvivor.uniqueId

            val titleMain = game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "lms.title")

            val subtitle = when {
                isTheSurvivor -> game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "lms.subtitle.survivor")
                isKiller -> game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "lms.subtitle.killer")
                else -> game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "lms.subtitle.other", parsedSurvivorName)
            }

            val chatMsg = if (isTheSurvivor) {
                game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "lms.chat.survivor")
            } else {
                game.pumpking.lib.service.PumpkingServiceManager.messages.getComponent(p, "lms.chat.other", parsedSurvivorName)
            }

            p.sendMessage(chatMsg)
            p.showTitle(Title.title(titleMain, subtitle, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(1000))))
            p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f)
            p.playSound(p.location, Sound.AMBIENT_CAVE, 1f, 0.5f)
            p.playSound(p.location, "mistaken:lms", SoundCategory.RECORDS, 1f, 1f)
        }
    }

    fun playAmbientForPlayer(p: Player, killersOnline: List<Player>) {
        var closestKiller: Player? = null
        var minDist = Double.MAX_VALUE
        val pLoc = p.location

        for (k in killersOnline) {
            if (k.world != pLoc.world) continue
            val dist = pLoc.distanceSquared(k.location)
            if (dist < minDist) {
                minDist = dist
                closestKiller = k
            }
        }
        closestKiller?.let { game.ambientManager.playSurvivorAmbience(p) }
    }

    fun checkHeartbeat(p: Player, killer: Player) {
        if (p.world != killer.world) return
        val d2 = p.location.distanceSquared(killer.location)
        if (d2 <= 225.0) {
            val pitch = if (d2 < 36.0) 1.4f else 0.8f
            p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, pitch)
        }
    }

    fun setLuckPermsPrefix(player: Player, colorTag: String) {
        game.plugin.server.asyncScheduler.runNow(game.plugin) { _ ->
            try {
                val lp = LuckPermsProvider.get()
                lp.userManager.modifyUser(player.uniqueId) { user ->
                    user.data().clear { it is PrefixNode }
                    if (colorTag.isNotEmpty()) {
                        user.data().add(PrefixNode.builder(colorTag, 100).build())
                    }
                }
            } catch (ignored: Exception) {}
        }
    }
}

