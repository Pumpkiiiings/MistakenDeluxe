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
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteTienda: Interfaz de gestión de clases humanas con soporte Multi-Idioma.
 * Optimización: Eliminación de cache estática para soporte real de múltiples lenguajes simultáneos.
 */
class SupervivienteTienda : MenuBase("supervivientes_tienda") {

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        // 1. Obtener archivos de traducción y lógica
        val langSurvivors = plugin.messageConfig.getSpecificFile(player, "supervivientes")
        val globalSurvivors = plugin.configManager.getSupervivientesConfig(player)

        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedSurvivor(uuid)

        // Labels traducidos según el idioma del jugador
        val labelHumano = plugin.messageConfig.getMessage(player, "tienda.clase-humana")
        val labelSeleccionado = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val labelPoseido = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val labelComprar = plugin.messageConfig.getMessage(player, "tienda.estado-comprar-superviviente")
        val labelHabilidades = plugin.messageConfig.getMessage(player, "tienda.habilidades-titulo")

        var slotIndex = 0

        // Iterar sobre las clases del manager de supervivientes
        for (survivorId in plugin.supervivienteManager.catalogo.keys) {
            if (slotIndex >= slots.size) break

            // --- RECOLECCIÓN DE DATOS DINÁMICOS ---

            // Visuales: Del archivo lang/{lang}/supervivientes.yml
            val nombreVisual = langSurvivors.getString("supervivientes.$survivorId.nombre") ?: survivorId
            val loreTienda = langSurvivors.getStringList("supervivientes.$survivorId.lore_tienda")

            // Lógicos: Del archivo global (Precios e Iconos)
            val precio = globalSurvivors.getInt("supervivientes.$survivorId.precio", 0)
            val matStr = globalSurvivors.getString("supervivientes.$survivorId.icono_material", "IRON_CHESTPLATE")!!
            val iconoMat = Material.matchMaterial(matStr) ?: Material.IRON_CHESTPLATE

            // --- CONSTRUCCIÓN DEL LORE ---
            val fullLore = mutableListOf<Component>()
            fullLore.add(labelHumano)
            fullLore.add(Component.empty())

            // Lore descriptivo traducido
            loreTienda.forEach { fullLore.add(mm.deserialize("<reset><i><gray>$it")) }
            fullLore.add(Component.empty())

            // Habilidades traducidas
            fullLore.add(labelHabilidades)
            for (i in 1..4) {
                val habName = langSurvivors.getString("supervivientes.$survivorId.items.habilidad${i}_nombre")
                if (habName != null) fullLore.add(mm.deserialize("<reset> <dark_gray>• <white>$habName"))
            }
            fullLore.add(Component.empty())

            // Estado de compra/selección
            val tiene = data.tieneSuperviviente(uuid, survivorId)
            val esSeleccionado = selected.equals(survivorId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(plugin.messageConfig.getMessage(player, "tienda.estado-price",
                        Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            // --- INYECCIÓN EN EL MENÚ ---
            gui.setItem(slots[slotIndex], ItemBuilder.from(iconoMat)
                .name(mm.deserialize(nombreVisual))
                .lore(fullLore as List<Component>)
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem {
                    handleLogic(player, survivorId, precio, tiene)
                }
            )

            slotIndex++
        }
    }

    private fun handleLogic(player: Player, id: String, precio: Int, tiene: Boolean) {
        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val actual = data.getSelectedSurvivor(uuid)

        if (id.equals(actual, ignoreCase = true)) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (tiene) {
            // Acción: Seleccionar
            data.setSelectedSurvivor(uuid, id)

            // Persistencia en PDC de Paper
            val key = NamespacedKey(plugin, "selected_survivor")
            player.persistentDataContainer.set(key, PersistentDataType.STRING, id)

            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.seleccionado", Placeholder.parsed("name", id)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)

            abrir(player) // Refrescar UI
        } else {
            // Acción: Comprar
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
