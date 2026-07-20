package liric.mistaken.packet.fake

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.player.GameMode
import com.github.retrooper.packetevents.protocol.player.TextureProperty
import com.github.retrooper.packetevents.protocol.player.UserProfile
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import java.util.Optional
import liric.mistaken.packet.PacketFactory

class FakeNPCAPI {

    /**
     * Crea un NPC completamente Client-Side.
     * @param player El jugador que lo verÃƒÂ¡.
     * @param location UbicaciÃƒÂ³n del NPC.
     * @param name Nombre (mostrarÃƒÂ¡ la tag si no estÃƒÂ¡ oculta por Scoreboard Teams).
     * @param textureValue Textura de skin (Base64).
     * @param textureSignature Firma de la skin.
     * @return El ID de Entidad generado.
     */
    fun spawnNPC(player: Player, location: Location, name: String, textureValue: String? = null, textureSignature: String? = null): Int {
        val entityId = PacketFactory.generateEntityId()
        val fakeUUID = UUID.randomUUID()
        val pm = PacketEvents.getAPI().playerManager

        val profile = UserProfile(fakeUUID, name)
        if (textureValue != null && textureSignature != null) {
            profile.textureProperties.add(TextureProperty("textures", textureValue, textureSignature))
        }

        // 1. INFO UPDATE (Carga la skin y el perfil)
        val infoData = WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            profile,
            true,
            1,
            GameMode.SURVIVAL,
            Component.text(name),
            null
        )
        val infoPacket = WrapperPlayServerPlayerInfoUpdate(
            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
            infoData
        )
        pm.sendPacket(player, infoPacket)

        // 2. SPAWN FISICO
        val pos = SpigotConversionUtil.fromBukkitLocation(location)
        val spawnPacket = WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(fakeUUID),
            EntityTypes.PLAYER,
            pos.position,
            pos.pitch,
            pos.yaw,
            pos.yaw,
            0,
            Optional.empty<Vector3d>()
        )
        pm.sendPacket(player, spawnPacket)

        // 3. HEAD LOOK (Girar cabeza correctamente)
        val headLook = WrapperPlayServerEntityHeadLook(entityId, pos.yaw)
        pm.sendPacket(player, headLook)

        return entityId
    }
}


