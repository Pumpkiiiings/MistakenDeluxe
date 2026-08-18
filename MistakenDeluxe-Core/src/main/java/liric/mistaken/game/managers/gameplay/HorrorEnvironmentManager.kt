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
        // Bioma para la noche estática (cielo forzado a negro)
        val customBiomeNight = CustomBiome.builder()
            .resourceKey(ResourceKey.of("mistaken", "horrorbiome_night"))
            .fogColor("#050505") 
            .foliageColor("#303030")
            .skyColor("#000000")
            .waterColor("#101010")
            .waterFogColor("#000000")
            .register()

        // Bioma para el día, tarde, o dinámico (cielo natural para permitir transición de día a noche, pero conserva niebla densa)
        val customBiomeDay = CustomBiome.builder()
            .resourceKey(ResourceKey.of("mistaken", "horrorbiome_day"))
            .fogColor("#050505") 
            .foliageColor("#303030")
            .register()

        val virtualBiomeNight = VirtualBiome.builder()
            .biome(customBiomeNight)
            .conditional { player, _ ->
                val session = plugin.sessionManager.getSession(player.uniqueId)
                if (session != null) {
                    val arena = plugin.arenaManager.getArena(session.currentMapName)
                    arena?.timeMode == "night"
                } else false
            }
            .priority(PacketHandler.Priority.HIGH)
            .build()

        val virtualBiomeDay = VirtualBiome.builder()
            .biome(customBiomeDay)
            .conditional { player, _ ->
                val session = plugin.sessionManager.getSession(player.uniqueId)
                if (session != null) {
                    val arena = plugin.arenaManager.getArena(session.currentMapName)
                    arena?.timeMode != "night"
                } else false
            }
            .priority(PacketHandler.Priority.NORMAL)
            .build()

        packetHandler = PacketHandler.of(plugin)
            .appendBiome(virtualBiomeNight)
            .appendBiome(virtualBiomeDay)
            .register()
    }

    fun shutdown() {
        packetHandler?.unregister()
    }
}
