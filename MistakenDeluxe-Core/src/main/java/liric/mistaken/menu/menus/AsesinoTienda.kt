package liric.mistaken.menu.menus

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

        val labelSeleccionado = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.estado-seleccionado")
        val labelPoseido = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.estado-poseido")
        val labelComprar = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.estado-comprar")
        val labelHabilidades = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.habilidades-titulo")

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

            val nombreVisual = pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(player, "asesinos.$killerId.nombre", "asesinos_info")
            val descripcion = pumpking.lib.service.PumpkingServiceManager.messages.getStrictStringList(player, "asesinos.$killerId.descripcion", "asesinos_info")
            val loreTienda = pumpking.lib.service.PumpkingServiceManager.messages.getStrictStringList(player, "asesinos.$killerId.lore_tienda", "asesinos_info")

            val precio = globalMecanicas.getInt("asesinos.$killerId.precio", 0)
            val matStr = globalMecanicas.getString("asesinos.$killerId.icono_material", "STONE")!!
            val iconoMat = Material.matchMaterial(matStr.uppercase()) ?: Material.STONE

            val fullLore = mutableListOf<Component>()

            descripcion.forEach { fullLore.add(parseSafe(it)) }
            fullLore.add(Component.empty())

            loreTienda.forEach { fullLore.add(parseSafe(it)) }
            fullLore.add(Component.empty())

            fullLore.add(labelHabilidades)
            for (i in 1..4) {
                val habName = pumpking.lib.service.PumpkingServiceManager.messages.getRawString(player, "asesinos.$killerId.habilidades_nombres.habilidad$i", "", "asesinos_info")
                if (habName.isNotEmpty()) {
                    fullLore.add(parseSafe(" <dark_gray>•</dark_gray> <white>$habName</white>"))
                }
            }
            fullLore.add(Component.empty())

            val tiene = data.tieneAsesino(uuid, killerId)
            val esSeleccionado = selected.equals(killerId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.estado-precio", Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            gui.setItem(targetSlot, ItemBuilder.from(iconoMat)
                .name(parseSafe(nombreVisual))
                .lore(fullLore.toList())
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem { event ->
                    event.isCancelled = true
                    handlePurchaseLogic(player, killerId, precio, tiene)
                }
            )
        }
        
        val botonAtrasSlot = config.getInt("ajustes.boton-atras.slot", 49)
        val botonAtrasMat = config.getString("ajustes.boton-atras.material", "ARROW")!!
        val botonAtrasNombre = config.getString("ajustes.boton-atras.nombre", "<red>Volver")!!
        val matAtras = Material.matchMaterial(botonAtrasMat.uppercase()) ?: Material.ARROW
        gui.setItem(botonAtrasSlot, ItemBuilder.from(matAtras)
            .name(parseSafe(botonAtrasNombre))
            .asGuiItem { event ->
                event.isCancelled = true
                ShopSelector().abrir(player)
            })
    }

    private fun handlePurchaseLogic(player: Player, killerId: String, precio: Int, tiene: Boolean) {
        val uuid = player.uniqueId
        val data = plugin.playerDataManager
        val actual = data.getSelectedKiller(uuid)

        if (killerId.equals(actual, ignoreCase = true)) {
            player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (tiene) {
            data.setSelectedKiller(uuid, killerId)
            player.persistentDataContainer.set(plugin.assassinKey, PersistentDataType.STRING, killerId)
            player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.seleccionado", Placeholder.parsed("name", killerId)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
            return
        }

        val econ = Mistaken.Companion.economy

        if (econ == null) {
            player.sendMessage(parseSafe("<red><b>[!]</b> Error interno: El sistema de economía (Vault) no está conectado.</red>"))
            plugin.componentLogger.error("[ERROR] [Economy] Purchase failed due to disconnected Vault: Player ${player.name}, Assassin $killerId")
            return
        }

        val costo = precio.toDouble()

        if (econ.has(player, costo)) {
            val response = econ.withdrawPlayer(player, costo)

            if (response.transactionSuccess()) {
                data.comprarAsesino(uuid, killerId)
                player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.comprado", Placeholder.parsed("name", killerId)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)
                abrir(player)
            } else {
                player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda_errores.error_bancario", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("error", response.errorMessage ?: "Unknown error")))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }

        } else {
            player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "errors.no-money"))
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


