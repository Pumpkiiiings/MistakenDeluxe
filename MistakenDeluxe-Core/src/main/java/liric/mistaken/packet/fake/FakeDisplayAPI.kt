package liric.mistaken.packet.fake

import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack

class FakeDisplayAPI {

    /**
     * Spawnea un TextDisplay Client-Side robusto (a prueba de versiones).
     * Usa la API de Bukkit para construirlo, pero lo aÃ­sla mediante PacketEvents
     * para que solo sea visible a los jugadores especificados.
     * @param viewers Lista de jugadores que podrÃ¡n verlo.
     * @param location UbicaciÃ³n en el mundo.
     * @param builder Bloque de configuraciÃ³n para modificar el Display usando la API nativa (text, scale, etc).
     * @return El TextDisplay creado.
     */
    fun buildTextDisplay(viewers: List<Player>, location: Location, builder: (TextDisplay) -> Unit): TextDisplay {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(Mistaken::class.java)
        val display = location.world.spawn(location, TextDisplay::class.java) { entity ->
            entity.isPersistent = false
            entity.setGravity(false)
            builder(entity)
            
            // Ocultar inmediatamente de todo el mundo usando nuestro filtro de paquetes
            plugin.visibilityManager.hideFromAllExcept(entity, viewers)
        }
        return display
    }

    /**
     * Spawnea un BlockDisplay Client-Side robusto.
     * @param viewers Lista de jugadores que podrÃ¡n verlo.
     * @param location UbicaciÃ³n en el mundo.
     * @param builder Bloque de configuraciÃ³n para transformar el display.
     * @return El BlockDisplay creado.
     */
    fun buildBlockDisplay(viewers: List<Player>, location: Location, builder: (BlockDisplay) -> Unit): BlockDisplay {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(Mistaken::class.java)
        val display = location.world.spawn(location, BlockDisplay::class.java) { entity ->
            entity.isPersistent = false
            entity.setGravity(false)
            builder(entity)
            
            plugin.visibilityManager.hideFromAllExcept(entity, viewers)
        }
        return display
    }

    /**
     * Spawnea un ItemDisplay Client-Side robusto.
     * @param viewers Lista de jugadores que podrÃ¡n verlo.
     * @param location UbicaciÃ³n en el mundo.
     * @param builder Bloque de configuraciÃ³n.
     * @return El ItemDisplay creado.
     */
    fun buildItemDisplay(viewers: List<Player>, location: Location, builder: (ItemDisplay) -> Unit): ItemDisplay {
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(Mistaken::class.java)
        val display = location.world.spawn(location, ItemDisplay::class.java) { entity ->
            entity.isPersistent = false
            entity.setGravity(false)
            builder(entity)
            
            plugin.visibilityManager.hideFromAllExcept(entity, viewers)
        }
        return display
    }
}

