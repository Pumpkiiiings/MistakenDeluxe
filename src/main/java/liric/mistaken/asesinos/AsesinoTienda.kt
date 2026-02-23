package liric.mistaken.asesinos

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.Mistaken
import liric.mistaken.menu.MenuBase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoTienda: Interfaz de compra con soporte Multi-Idioma real.
 * Separa visuales (info) de mecánicas (global).
 */
class AsesinoTienda : MenuBase("asesinos_tienda") {

    companion object {
        private val EXCLUSIVE_IDS = setOf("devesto", "mariachi", "romeo", "bendy", "coolkid")
        private val SPECIAL_IDS = setOf("teto", "miku", "charlie", "colorandelectricity")
    }

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        // 1. Cargamos archivos de info (visual) y mecánicas (global)
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")
        val globalMecanicas = plugin.configManager.getAsesinos()

        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedKiller(uuid)

        // Labels traducidos según el idioma del vato
        val labelSeleccionado = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val labelPoseido = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val labelComprar = plugin.messageConfig.getMessage(player, "tienda.estado-comprar")
        val labelHabilidades = plugin.messageConfig.getMessage(player, "tienda.habilidades-titulo")

        var slotIndex = 0

        // Usamos las clases registradas en el manager
        for (killerId in plugin.asesinoManager.getClasesDisponibles().keys) {
            if (slotIndex >= slots.size) break
            if (!tienePermisoParaVer(player, killerId)) continue

            // --- 🎨 DATOS VISUALES (Del archivo de idioma) ---
            val nombreVisual = langInfo.getString("asesinos.$killerId.nombre") ?: killerId
            val descripcion = langInfo.getStringList("asesinos.$killerId.descripcion")
            val loreTienda = langInfo.getStringList("asesinos.$killerId.lore_tienda")

            // --- ⚙️ DATOS MECÁNICOS (Del archivo global) ---
            val precio = globalMecanicas.getInt("asesinos.$killerId.precio", 0)
            val matStr = globalMecanicas.getString("asesinos.$killerId.icono_material", "STONE")!!
            val iconoMat = Material.matchMaterial(matStr) ?: Material.STONE

            // --- 🔨 CONSTRUCCIÓN DEL LORE ---
            val fullLore = mutableListOf<Component>()

            // Inyectamos descripción y diseño
            descripcion.forEach { fullLore.add(mm.deserialize(it)) }
            fullLore.add(Component.empty())
            loreTienda.forEach { fullLore.add(mm.deserialize("<i><gray>$it")) }
            fullLore.add(Component.empty())

            // Habilidades (Nombres traducidos)
            fullLore.add(labelHabilidades)
            for (i in 1..4) {
                val habName = langInfo.getString("asesinos.$killerId.habilidades_nombres.habilidad$i")
                if (habName != null) fullLore.add(mm.deserialize(" <dark_gray>• <white>$habName"))
            }
            fullLore.add(Component.empty())

            // Estado de la cuenta
            val tiene = data.tieneAsesino(uuid, killerId)
            val esSeleccionado = selected.equals(killerId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(plugin.messageConfig.getMessage(player, "tienda.estado-precio",
                        Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            // --- RENDERIZADO EN EL GUI ---
            gui.setItem(slots[slotIndex], ItemBuilder.from(iconoMat)
                .name(mm.deserialize(nombreVisual))
                .lore(fullLore.toList()) // .toList() para evitar errores de tipo
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem {
                    handlePurchaseLogic(player, killerId, precio, tiene)
                }
            )

            slotIndex++
        }
    }

    private fun handlePurchaseLogic(player: Player, killerId: String, precio: Int, tiene: Boolean) {
        val uuid = player.uniqueId
        val data = plugin.playerDataManager

        if (tiene) {
            data.setSelectedKiller(uuid, killerId)
            player.persistentDataContainer.set(plugin.assassinKey, PersistentDataType.STRING, killerId)
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.seleccionado", Placeholder.parsed("name", killerId)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
        } else {
            val econ = Mistaken.economy
            if (econ != null && econ.has(player, precio.toDouble())) {
                econ.withdrawPlayer(player, precio.toDouble())
                data.comprarAsesino(uuid, killerId)
                player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.comprado", Placeholder.parsed("name", killerId)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)
                abrir(player)
            } else {
                player.sendMessage(plugin.messageConfig.getMessage(player, "errors.no-money"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }
        }
    }

    private fun tienePermisoParaVer(player: Player, id: String): Boolean {
        return when (id.lowercase()) {
            in EXCLUSIVE_IDS -> player.hasPermission("mistaken.skins.exclusivo")
            in SPECIAL_IDS -> player.hasPermission("mistaken.skins.especiales")
            else -> true
        }
    }
}
