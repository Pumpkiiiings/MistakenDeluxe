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

class AsesinoTienda : MenuBase("asesinos_tienda") {

    companion object {
        private val DEV_IDS = setOf("romeo", "bendy", "pizzano", "sowoul", "teto", "miku", "charlie", "devesto")
        private val VIP_IDS = setOf("colorandelectricity", "errorestatico")
    }

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")
        val globalMecanicas = plugin.configManager.getAsesinos()

        val preferredSlots = config.getIntegerList("ajustes.slots-disponibles").toMutableList()

        // 🔥 NUEVO: Slots fijos configurados desde el YAML
        val fixedSlotsSection = config.getConfigurationSection("ajustes.slots-fijos")
        val fixedSlots = mutableMapOf<String, Int>()
        if (fixedSlotsSection != null) {
            for (key in fixedSlotsSection.getKeys(false)) {
                fixedSlots[key.lowercase()] = fixedSlotsSection.getInt(key)
            }
        }

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedKiller(uuid)

        val labelSeleccionado = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val labelPoseido = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val labelComprar = plugin.messageConfig.getMessage(player, "tienda.estado-comprar")
        val labelHabilidades = plugin.messageConfig.getMessage(player, "tienda.habilidades-titulo")

        val asesinosCatalogo = plugin.asesinoManager.getClasesDisponibles().keys

        for (killerId in asesinosCatalogo) {
            if (!tienePermisoParaVer(player, killerId)) continue

            // 🔥 NUEVO: Detección de slot fijo
            val targetSlot = if (fixedSlots.containsKey(killerId)) {
                fixedSlots[killerId]!!
            } else if (preferredSlots.isNotEmpty()) {
                preferredSlots.removeAt(0)
            } else {
                gui.inventory.firstEmpty()
            }

            if (targetSlot == -1) continue // Si no hay espacio en el inventario, lo salta

            val nombreVisual = langInfo.getString("asesinos.$killerId.nombre") ?: "<red>$killerId"
            val descripcion = langInfo.getStringList("asesinos.$killerId.descripcion")
            val loreTienda = langInfo.getStringList("asesinos.$killerId.lore_tienda")

            val precio = globalMecanicas.getInt("asesinos.$killerId.precio", 0)
            val matStr = globalMecanicas.getString("asesinos.$killerId.icono_material", "STONE")!!
            val iconoMat = Material.matchMaterial(matStr.uppercase()) ?: Material.STONE

            val fullLore = mutableListOf<Component>()

            descripcion.forEach { fullLore.add(mm.deserialize(it)) }
            fullLore.add(Component.empty())

            loreTienda.forEach { fullLore.add(mm.deserialize("<i><gray>$it</gray></i>")) }
            fullLore.add(Component.empty())

            fullLore.add(labelHabilidades)
            for (i in 1..4) {
                val habName = langInfo.getString("asesinos.$killerId.habilidades_nombres.habilidad$i")
                if (habName != null) {
                    fullLore.add(mm.deserialize(" <dark_gray>•</dark_gray> <white>$habName</white>"))
                }
            }
            fullLore.add(Component.empty())

            val tiene = data.tieneAsesino(uuid, killerId)
            val esSeleccionado = selected.equals(killerId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(plugin.messageConfig.getMessage(player, "tienda.estado-precio", Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            gui.setItem(targetSlot, ItemBuilder.from(iconoMat)
                .name(mm.deserialize(nombreVisual))
                .lore(fullLore.toList())
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem { event ->
                    event.isCancelled = true
                    handlePurchaseLogic(player, killerId, precio, tiene)
                }
            )
        }
    }

    private fun handlePurchaseLogic(player: Player, killerId: String, precio: Int, tiene: Boolean) {
        val uuid = player.uniqueId
        val data = plugin.playerDataManager
        val actual = data.getSelectedKiller(uuid)

        if (killerId.equals(actual, ignoreCase = true)) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (tiene) {
            data.setSelectedKiller(uuid, killerId)
            player.persistentDataContainer.set(plugin.assassinKey, PersistentDataType.STRING, killerId)
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.seleccionado", Placeholder.parsed("name", killerId)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
            return
        }

        val econ = Mistaken.economy

        if (econ == null) {
            player.sendMessage(mm.deserialize("<red><b>[!]</b> Error interno: El sistema de economía (Vault) no está conectado.</red>"))
            plugin.componentLogger.error("Intento de compra fallido por Vault desconectado: Jugador ${player.name}, Asesino $killerId")
            return
        }

        val costo = precio.toDouble()

        if (econ.has(player, costo)) {
            val response = econ.withdrawPlayer(player, costo)

            if (response.transactionSuccess()) {
                data.comprarAsesino(uuid, killerId)
                player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.comprado", Placeholder.parsed("name", killerId)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)
                abrir(player)
            } else {
                player.sendMessage(mm.deserialize("<red>Hubo un problema con tu banco al cobrarte: ${response.errorMessage}</red>"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }

        } else {
            player.sendMessage(plugin.messageConfig.getMessage(player, "errors.no-money"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
        }
    }

    private fun tienePermisoParaVer(player: Player, id: String): Boolean {
        return when (id.lowercase()) {
            in DEV_IDS -> player.hasPermission("mistaken.skins.dev")
            in VIP_IDS -> player.hasPermission("mistaken.skins.exclusivo")
            else -> true
        }
    }
}
