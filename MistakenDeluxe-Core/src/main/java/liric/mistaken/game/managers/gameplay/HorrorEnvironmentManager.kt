package liric.mistaken.game.managers.gameplay

import dev.wyck.biome.CustomBiome
import dev.wyck.keys.ResourceKey
import dev.wyck.renderer.packet.PacketHandler
import dev.wyck.renderer.packet.data.VirtualBiome
import liric.mistaken.Mistaken

class HorrorEnvironmentManager(private val plugin: Mistaken) {

    private var packetHandler: PacketHandler? = null

    init {
        setupHorrorBiome()
    }

    private fun setupHorrorBiome() {
        val customBiome = CustomBiome.builder()
            .resourceKey(ResourceKey.of("mistaken", "horrorbiome"))
            .fogColor("#050505") 
            .foliageColor("#303030")
            .skyColor("#000000")
            .waterColor("#101010")
            .waterFogColor("#000000")
            .register()

        val virtualBiome = VirtualBiome.builder()
            .biome(customBiome)
            .conditional { player, _ ->
                plugin.sessionManager.getSession(player.uniqueId) != null
            }
            .priority(PacketHandler.Priority.NORMAL)
            .build()

        packetHandler = PacketHandler.of(plugin)
            .appendBiome(virtualBiome)
            .register()
    }

    fun shutdown() {
        packetHandler?.unregister()
    }
}
