package liric.mistaken.menu.menus

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

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteTienda: Menú de selección de humanos.
 */
class SupervivienteTienda : MenuBase("supervivientes_tienda") {

    private val survivorKey by lazy { NamespacedKey(plugin, "selected_survivor") }

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        val supervivienteManager = plugin.supervivienteManager ?: return

        val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
        val globalMecanicas = plugin.configManager.getSupervivientes()

        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedSurvivor(uuid)

        val labelHumano = plugin.messageConfig.getMessage(player, "tienda.clase-humana")
        val labelSeleccionado = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val labelPoseido = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val labelComprar = plugin.messageConfig.getMessage(player, "tienda.estado-comprar-superviviente")
        val labelHabilidades = plugin.messageConfig.getMessage(player, "tienda.habilidades-titulo")

        var slotIndex = 0

        for (survivorId in supervivienteManager.getClasesDisponibles().keys) {
            if (slotIndex >= slots.size) break

            val nombreVisual = langInfo.getString("supervivientes.$survivorId.nombre") ?: survivorId.uppercase()
            val loreTienda = langInfo.getStringList("supervivientes.$survivorId.lore_tienda")

            val precio = globalMecanicas.getInt("supervivientes.$survivorId.precio", 0)
            val matStr = globalMecanicas.getString("supervivientes.$survivorId.icono_material", "IRON_CHESTPLATE")!!
            val iconoMat = Material.matchMaterial(matStr) ?: Material.IRON_CHESTPLATE

            val fullLore = mutableListOf<Component>().apply {
                add(labelHumano)
                add(Component.empty())

                loreTienda.forEach { line ->
                    add(mm.deserialize("<i><gray>$line</gray></i>"))
                }

                add(Component.empty())
                add(labelHabilidades)

                for (i in 1..3) {
                    val habName = langInfo.getString("supervivientes.$survivorId.habilidades_nombres.habilidad$i")
                    if (!habName.isNullOrEmpty()) {
                        add(mm.deserialize(" <dark_gray>•</dark_gray> <white>$habName</white>"))
                    }
                }
                add(Component.empty())
            }

            val tiene = data.tieneSuperviviente(uuid, survivorId)
            val esSeleccionado = selected.equals(survivorId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(plugin.messageConfig.getMessage(player, "tienda.estado-precio", Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            gui.setItem(slots[slotIndex], ItemBuilder.from(iconoMat)
                .name(mm.deserialize(nombreVisual))
                .lore(fullLore.toList())
                .flags(*ItemFlag.values())
                .asGuiItem { event ->
                    event.isCancelled = true
                    handleLogic(player, survivorId, precio, tiene)
                }
            )
            slotIndex++
        }
    }

    private fun handleLogic(player: Player, id: String, precio: Int, tiene: Boolean) {
        val uuid = player.uniqueId
        val data = plugin.playerDataManager
        val actual = data.getSelectedSurvivor(uuid)

        if (id.equals(actual, ignoreCase = true)) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (tiene) {
            data.setSelectedSurvivor(uuid, id)
            player.persistentDataContainer.set(survivorKey, PersistentDataType.STRING, id)

            val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
            val nombreVisual = langInfo.getString("supervivientes.$id.nombre") ?: id

            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.seleccionado", Placeholder.component("name", mm.deserialize(nombreVisual))))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
            return
        }

        val econ = Mistaken.economy
        if (econ == null) {
            player.sendMessage(mm.deserialize("<red>Error: Vault no está conectado.</red>"))
            return
        }

        val costo = precio.toDouble()

        if (econ.has(player, costo)) {
            val response = econ.withdrawPlayer(player, costo)
            if (response.transactionSuccess()) {
                data.comprarSuperviviente(uuid, id)

                val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
                val nombreVisual = langInfo.getString("supervivientes.$id.nombre") ?: id

                player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.comprado", Placeholder.component("name", mm.deserialize(nombreVisual))))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                abrir(player)
            } else {
                player.sendMessage(mm.deserialize("<red>Error bancario: ${response.errorMessage}</red>"))
            }
        } else {
            player.sendMessage(plugin.messageConfig.getMessage(player, "errors.no-money"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
        }
    }
}
