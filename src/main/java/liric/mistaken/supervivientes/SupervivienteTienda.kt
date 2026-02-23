package liric.mistaken.supervivientes

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.menu.MenuBase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteTienda: Menú de selección de humanos.
 * Optimizado para leer de archivos divididos e inyectar componentes MiniMessage.
 */
class SupervivienteTienda : MenuBase("supervivientes_tienda") {

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        // 1. Info local (Textos) y Global (Fierros/Mecánicas)
        val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes")
        val globalMecanicas = plugin.configManager.getSupervivientes()

        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedSurvivor(uuid)

        // Labels del idioma
        val labelHumano = plugin.messageConfig.getMessage(player, "tienda.clase-humana")
        val labelSeleccionado = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val labelPoseido = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val labelComprar = plugin.messageConfig.getMessage(player, "tienda.estado-comprar-superviviente")
        val labelHabilidades = plugin.messageConfig.getMessage(player, "tienda.habilidades-titulo")

        var slotIndex = 0

        for (survivorId in plugin.supervivienteManager.getClasesDisponibles().keys) {
            if (slotIndex >= slots.size) break

            // --- DATOS VISUALES ---
            val nombreVisual = langInfo.getString("supervivientes.$survivorId.nombre") ?: survivorId
            val loreTienda = langInfo.getStringList("supervivientes.$survivorId.lore_tienda")

            // --- DATOS MECÁNICOS ---
            val precio = globalMecanicas.getInt("supervivientes.$survivorId.precio", 0)
            val matStr = globalMecanicas.getString("supervivientes.$survivorId.icono_material", "IRON_CHESTPLATE")!!
            val iconoMat = Material.matchMaterial(matStr) ?: Material.IRON_CHESTPLATE

            // --- LORE DINÁMICO ---
            val fullLore = mutableListOf<Component>().apply {
                add(labelHumano)
                add(Component.empty())
                loreTienda.forEach { add(mm.deserialize("<reset><i><gray>$it")) }
                add(Component.empty())
                add(labelHabilidades)
                for (i in 1..3) {
                    val habName = langInfo.getString("supervivientes.$survivorId.items.habilidad${i}_nombre")
                    if (habName != null) add(mm.deserialize("<reset> <dark_gray>• <white>$habName"))
                }
                add(Component.empty())
            }

            val tiene = data.tieneSuperviviente(uuid, survivorId)
            val esSeleccionado = selected.equals(survivorId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(plugin.messageConfig.getMessage(player, "tienda.estado-precio",
                        Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            // --- RENDER ---
            gui.setItem(slots[slotIndex], ItemBuilder.from(iconoMat)
                .name(mm.deserialize(nombreVisual))
                .lore(fullLore.toList())
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem {
                    handleLogic(player, survivorId, precio, tiene)
                }
            )

            slotIndex++
        }
    }

    private fun handleLogic(player: Player, id: String, precio: Int, tiene: Boolean) {
        val uuid = player.uniqueId
        val data = plugin.playerDataManager

        if (id.equals(data.getSelectedSurvivor(uuid), ignoreCase = true)) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (tiene) {
            data.setSelectedSurvivor(uuid, id)
            val key = NamespacedKey(plugin, "selected_survivor")
            player.persistentDataContainer.set(key, PersistentDataType.STRING, id)
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.seleccionado", Placeholder.parsed("name", id)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
        } else {
            val economy = Mistaken.economy
            if (economy != null && economy.has(player, precio.toDouble())) {
                economy.withdrawPlayer(player, precio.toDouble())
                data.comprarSuperviviente(uuid, id)
                player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.comprado", Placeholder.parsed("name", id)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                abrir(player)
            } else {
                player.sendMessage(plugin.messageConfig.getMessage(player, "errors.no-money"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }
        }
    }
}
