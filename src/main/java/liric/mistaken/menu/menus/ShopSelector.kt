package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.menu.MenuBase
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag

/**
 *[LIRIC-MISTAKEN 2.0]
 * ShopSelector: Menú principal de selección de tiendas.
 *
 * Optimización:
 * - Llamadas Null-Safe a los sub-menús en caso de servidores restringidos.
 * - Sin singletons compartidos (evita bugs visuales entre jugadores).
 * - Carga asíncrona-segura de configuraciones de idioma.
 */
class ShopSelector : MenuBase("tienda_principal") {

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {

        // 1. Cargar ajustes de sonido de forma robusta
        // Evita excepciones de consola si el owner del server se equivoca al escribir el sonido en el YAML.
        val soundName = config.getString("ajustes.sonido-click", "UI_BUTTON_CLICK")?.uppercase() ?: "UI_BUTTON_CLICK"
        val clickSound = runCatching { Sound.valueOf(soundName) }.getOrDefault(Sound.UI_BUTTON_CLICK)

        // 2. ITEM: ASESINOS
        val pathA = "items.asesinos"
        val matA = Material.matchMaterial(config.getString("$pathA.material", "NETHERITE_SWORD")!!) ?: Material.NETHERITE_SWORD

        val itemAsesinos = ItemBuilder.from(matA)
            .name(mm.deserialize(config.getString("$pathA.nombre", "<red>Asesinos")!!))
            .lore(config.getStringList("$pathA.lore").map { mm.deserialize(it) })
            .flags(*ItemFlag.values()) // Uso nativo para ocultar encantamientos y atributos
            .asGuiItem {
                // Folia: Los eventos de inventario ya se ejecutan en el hilo nativo de la entidad
                player.playSound(player.location, clickSound, 1f, 1f)

                // Llamada segura (?.) por si la tienda fue apagada en un proxy para ahorrar RAM
                plugin.asesinoTienda?.abrir(player) ?: player.sendMessage(mm.deserialize("<red>La tienda de Asesinos está desactivada en este servidor.</red>"))
            }

        // 3. ITEM: SUPERVIVIENTES
        val pathS = "items.supervivientes"
        val matS = Material.matchMaterial(config.getString("$pathS.material", "IRON_CHESTPLATE")!!) ?: Material.IRON_CHESTPLATE

        val itemSurvivors = ItemBuilder.from(matS)
            .name(mm.deserialize(config.getString("$pathS.nombre", "<green>Supervivientes")!!))
            .lore(config.getStringList("$pathS.lore").map { mm.deserialize(it) })
            .flags(*ItemFlag.values())
            .asGuiItem {
                player.playSound(player.location, clickSound, 1f, 1f)

                // Llamada segura
                plugin.supervivienteTienda?.abrir(player) ?: player.sendMessage(mm.deserialize("<red>La tienda de Supervivientes está desactivada en este servidor.</red>"))
            }

        // 4. Colocar los ítems en sus slots (definidos en el YAML de ese idioma)
        gui.setItem(config.getInt("$pathA.slot", 11), itemAsesinos)
        gui.setItem(config.getInt("$pathS.slot", 15), itemSurvivors)
    }
}
