package liric.mistaken.preview.engine

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers
import liric.mistaken.preview.PreviewAddon
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Monta al jugador en un cerdo invisible estático (Servidor) y le obliga a usar su cámara,
 * bloqueando así el movimiento del cuerpo pero permitiendo que mueva la cabeza libremente.
 * Se usa un cerdo real para evitar que el servidor patee al jugador por interactuar consigo mismo.
 */
class PreviewCamera(private val player: Player) {

    private var tripod: Pig? = null
    private val plugin = PreviewAddon.instance
    private val mistaken = PreviewAddon.instance.mistaken

    private var originalLoc: Location? = null

    companion object {
        val activeCameras = ConcurrentHashMap<UUID, PreviewCamera>()
    }

    fun spawnAt(location: Location) {
        originalLoc = player.location
        
        // 1. Teletransportar al jugador físicamente a la cámara
        player.teleport(location)
        player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, Int.MAX_VALUE, 0, false, false, false))

        // 2. Ejecutar asincrónicamente o en el scheduler de región si Folia
        plugin.server.regionScheduler.execute(plugin, location, Runnable {
            tripod = location.world?.spawnEntity(location, EntityType.PIG) as? Pig
            tripod?.apply {
                isInvisible = true
                setAI(false)
                setGravity(false)
                isSilent = true
                isInvulnerable = true
                isCollidable = false
                
                // 3. Paquetes a través de PacketEvents
                val pm = PacketEvents.getAPI().playerManager
                pm.sendPacket(player, WrapperPlayServerCamera(entityId))
                pm.sendPacket(player, WrapperPlayServerSetPassengers(entityId, intArrayOf(player.entityId)))
            }
            activeCameras[player.uniqueId] = this@PreviewCamera
        })
    }

    fun remove() {
        activeCameras.remove(player.uniqueId)
        val pm = PacketEvents.getAPI().playerManager
        pm.sendPacket(player, WrapperPlayServerCamera(player.entityId))
        
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)
        originalLoc?.let { player.teleport(it) }

        tripod?.let {
            plugin.server.regionScheduler.execute(plugin, it.location, Runnable {
                it.remove()
            })
        }
        tripod = null
    }

    fun getLocation(): Location = tripod?.location ?: player.location
}
