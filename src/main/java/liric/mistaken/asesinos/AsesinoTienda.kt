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
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoTienda: Interfaz de adquisición de entes con soporte Multi-Idioma.
 */
class AsesinoTienda : MenuBase("asesinos_tienda") {

    companion object {
        // IDs para filtrado por permisos O(1)
        private val EXCLUSIVE_IDS = setOf("devesto", "mariachi", "romeo", "bendy", "67dev")
        private val SPECIAL_IDS = setOf("teto", "miku", "charlie", "colorandelectricity")
    }

    /**
     * 🔥 FIX: La firma ahora coincide con MenuBase (player, gui, config)
     */
    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        // 1. Datos visuales del idioma del jugador (lang/es/asesinos.yml)
        val langAsesinos = plugin.messageConfig.getSpecificFile(player, "asesinos")
        // 2. Datos lógicos globales (raíz/asesinos.yml)
        val globalAsesinos = plugin.configManager.getAsesinosConfig(player)

        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedKiller(uuid)

        // Labels comunes traducidos
        val labelSeleccionado = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val labelPoseido = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val labelComprar = plugin.messageConfig.getMessage(player, "tienda.estado-comprar")
        val labelHabilidades = plugin.messageConfig.getMessage(player, "tienda.habilidades-titulo")

        var slotIndex = 0

        // Usamos el catálogo del manager
        for (killerId in plugin.asesinoManager.catalogo.keys) {
            if (slotIndex >= slots.size) break
            if (!tienePermisoParaVer(player, killerId)) continue

            // --- RECOLECCIÓN DE DATOS DINÁMICOS ---
            val nombreVisual = langAsesinos.getString("asesinos.$killerId.nombre") ?: killerId
            val descripcion = langAsesinos.getStringList("asesinos.$killerId.descripcion")
            val loreTienda = langAsesinos.getStringList("asesinos.$killerId.lore_tienda")

            val precio = globalAsesinos.getInt("asesinos.$killerId.precio", 0)
            val matStr = globalAsesinos.getString("asesinos.$killerId.icono_material", "STONE")!!
            val iconoMat = Material.matchMaterial(matStr) ?: Material.STONE

            // --- CONSTRUCCIÓN DEL LORE (Como lista de Component) ---
            val fullLore = mutableListOf<Component>()

            // Descripción
            descripcion.forEach { fullLore.add(mm.deserialize(it)) }
            fullLore.add(Component.empty())

            // Frase decorativa
            loreTienda.forEach { fullLore.add(mm.deserialize("<i><gray>$it")) }
            fullLore.add(Component.empty())

            // Habilidades
            fullLore.add(labelHabilidades)
            for (i in 1..4) {
                val habName = langAsesinos.getString("asesinos.$killerId.items.habilidad${i}_nombre")
                if (habName != null) fullLore.add(mm.deserialize(" <dark_gray>• <white>$habName"))
            }
            fullLore.add(Component.empty())

            // Estado
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

            // --- INYECCIÓN EN EL GUI ---
            // 🔥 FIX: .lore() ahora recibe la lista correctamente
            gui.setItem(slots[slotIndex], ItemBuilder.from(iconoMat)
                .name(mm.deserialize(nombreVisual))
                .lore(fullLore as List<Component>) // Casteo explícito para evitar error
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
