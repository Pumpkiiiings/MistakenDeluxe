package liric.mistaken.game.logic

import liric.mistaken.game.GameSession
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.entity.Player

class GameWorldController(private val game: GameSession) {

    fun addProgress(block: Block, amount: Int, player: Player?) {
        if (player != null && game.esAsesino(player.uniqueId)) return
        val loc = block.location
        if (game.plugin.generatorManager.isCompleted(loc)) return

        game.plugin.generatorManager.addProgress(loc, amount)

        if (game.plugin.generatorManager.isCompleted(loc)) {
            if (player != null) {
                game.plugin.statsManager.incrementStat(player.uniqueId, "generators_repaired")
            }
            if (!game.changedBlocks.containsKey(loc)) game.changedBlocks[loc] = block.type
            block.type = Material.SEA_LANTERN
            block.world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1f)

            game.broadcastLocalized("game.generator-repaired",
                Placeholder.parsed("current", game.plugin.generatorManager.getCompletedCount().toString()),
                Placeholder.parsed("total", game.plugin.generatorManager.getTotalGenerators().toString())
            )
            game.playerController.checkWinCondition()
        }
    }

    fun limpiarMapa() {
        game.plugin.generatorManager.resetGenerators()
        game.changedBlocks.forEach { (loc, material) -> loc.block.type = material }
        game.changedBlocks.clear()

        for (p in game.plugin.server.onlinePlayers) {
            game.uiController.setLuckPermsPrefix(p, "")
            p.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            p.health = 20.0
            p.isSwimming = false
        }
        game.voteManager.resetVotes()
    }
}
