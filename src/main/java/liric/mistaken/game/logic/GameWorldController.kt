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
        val generatorManager = game.plugin.generatorManager ?: return

        if (generatorManager.isCompleted(loc)) return
        generatorManager.addProgress(loc, amount)

        if (generatorManager.isCompleted(loc)) {
            // Folia Ready: Modificar bloques siempre en el hilo de la región
            game.plugin.server.regionScheduler.run(game.plugin, loc, { _ ->
                if (!game.changedBlocks.containsKey(loc)) game.changedBlocks[loc] = block.type
                block.type = Material.SEA_LANTERN
                block.world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1f)
            })

            game.broadcastLocalized("game.generator-repaired",
                Placeholder.parsed("current", generatorManager.getCompletedCountInWorld(loc.world).toString()),
                Placeholder.parsed("total", generatorManager.getTotalGeneratorsInWorld(loc.world).toString())
            )
            game.playerController.checkWinCondition()
        }
    }

    fun limpiarMapa() {
        game.plugin.generatorManager?.resetGenerators()

        // Iteramos las ubicaciones y delegamos al RegionScheduler para la seguridad en Folia
        game.changedBlocks.forEach { (loc, material) ->
            game.plugin.server.regionScheduler.run(game.plugin, loc, { _ ->
                loc.block.type = material
            })
        }
        game.changedBlocks.clear()

        for (p in game.getPlayers()) {
            p.scheduler.run(game.plugin, { _ ->
                game.uiController.setLuckPermsPrefix(p, "")
                p.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
                p.health = 20.0
                p.isSwimming = false
            }, null)
        }
        game.voteManager.resetVotes()
    }
}
