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
import liric.mistaken.api.requirements.RequirementEngine
import liric.mistaken.config.engine.core.MessageService

class KillerShop : MenuBase("killers_shop") {



    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        val globalMecanicas = plugin.configManager.getKillerConfig("global")

        val preferredSlots = config.getIntegerList("ajustes.slots-disponibles").toMutableList()

        
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

        val labelSeleccionado = MessageService.getComponent(player, "shop.estado-seleccionado")
        val labelPoseido = MessageService.getComponent(player, "shop.estado-poseido")
        val labelComprar = MessageService.getComponent(player, "shop.estado-comprar")
        val labelAbilityes = MessageService.getComponent(player, "shop.abilityes-titulo")

        val killersCatalogo = plugin.killerManager.catalogo.keys

        for (killerId in killersCatalogo) {
            val killerConfig = plugin.configManager.getKillerConfig(killerId)
            val permisoRequerido = killerConfig.getString("permiso")
            if (permisoRequerido != null && !player.hasPermission(permisoRequerido)) continue

            if (killerId.equals("smiler", ignoreCase = true) || killerId.equals("warden", ignoreCase = true)) {
                val hasAccess = player.name.equals("Pumpkiiings", ignoreCase = true) || 
                                player.name.equals("Pumpkiings", ignoreCase = true) ||
                                player.hasPermission("group.dueno") ||
                                player.hasPermission("group.owner")
                if (!hasAccess) continue
            }

            
            val targetSlot = if (fixedSlots.containsKey(killerId)) {
                fixedSlots[killerId]!!
            } else if (preferredSlots.isNotEmpty()) {
                preferredSlots.removeAt(0)
            } else {
                gui.inventory.firstEmpty()
            }

            if (targetSlot == -1) continue 

            val nombreVisual = MessageService.getStrictString(player, "killers.$killerId.nombre", "killers_info")
            val descripcion = MessageService.getStrictStringList(player, "killers.$killerId.descripcion", "killers_info")
            val loreShop = MessageService.getStrictStringList(player, "killers.$killerId.lore_shop", "killers_info")

            val precio = killerConfig.getInt("precio", 0)
            val matStr = killerConfig.getString("icono_material", "STONE")!!
            val iconoMat = Material.matchMaterial(matStr.uppercase()) ?: Material.STONE

            val fullLore = mutableListOf<Component>()

            descripcion.forEach { fullLore.add(parseSafe(it)) }
            fullLore.add(Component.empty())

            loreShop.forEach { fullLore.add(parseSafe(it)) }
            fullLore.add(Component.empty())

            fullLore.add(labelAbilityes)
            val weaponName = MessageService.getRawString(player, "killers.$killerId.skill_names.weapon", "", "killers_info")
            if (weaponName.isNotEmpty()) {
                fullLore.add(parseSafe(" <dark_gray>•</dark_gray> <white>$weaponName</white>"))
            }
            for (i in 1..4) {
                val habName = MessageService.getRawString(player, "killers.$killerId.skill_names.skill$i", "", "killers_info")
                if (habName.isNotEmpty()) {
                    fullLore.add(parseSafe(" <dark_gray>•</dark_gray> <white>$habName</white>"))
                }
            }
            fullLore.add(Component.empty())

            val tiene = data.hasKiller(uuid, killerId)
            val esSeleccionado = selected.equals(killerId, ignoreCase = true)

            val reqMessages = RequirementEngine.getRequirementMessages(player, "killers", killerId)
            reqMessages.forEach { fullLore.add(parseSafe(it)) }

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(MessageService.getComponent(player, "shop.estado-precio", Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            val guiItem = ItemBuilder.from(iconoMat)
                .name(parseSafe(nombreVisual))
                .lore(fullLore.toList())
                .asGuiItem { event ->
                    event.isCancelled = true
                    if (reqMessages.isNotEmpty()) {
                        player.sendMessage(parseSafe("<red>No cumples los requisitos para este killer.</red>"))
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
                        return@asGuiItem
                    }
                    handlePurchaseLogic(player, killerId, precio, tiene)
                }.also { it.itemStack.editMeta { meta -> meta.addItemFlags(*ItemFlag.entries.toTypedArray()) } }

            gui.setItem(targetSlot, guiItem)
        }
        
        val botonAtrasSlot = config.getInt("ajustes.boton-atras.slot", 49)
        val botonAtrasMat = config.getString("ajustes.boton-atras.material", "ARROW")!!
        val botonAtrasNombre = config.getString("ajustes.boton-atras.nombre", "<red>Volver")!!
        val matAtras = Material.matchMaterial(botonAtrasMat.uppercase()) ?: Material.ARROW
        
        val backItem = ItemBuilder.from(matAtras)
            .name(parseSafe(botonAtrasNombre))
            .asGuiItem { event ->
                event.isCancelled = true
                ShopSelector().abrir(player)
            }.also { it.itemStack.editMeta { meta -> meta.addItemFlags(*ItemFlag.entries.toTypedArray()) } }
            
        gui.setItem(botonAtrasSlot, backItem)
    }

    private fun handlePurchaseLogic(player: Player, killerId: String, precio: Int, tiene: Boolean) {
        val uuid = player.uniqueId
        val data = plugin.playerDataManager
        val actual = data.getSelectedKiller(uuid)

        if (killerId.equals(actual, ignoreCase = true)) {
            player.sendMessage(MessageService.getComponent(player, "shop.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (tiene) {
            data.setSelectedKiller(uuid, killerId)
            player.persistentDataContainer.set(plugin.assassinKey, PersistentDataType.STRING, killerId)
            player.sendMessage(MessageService.getComponent(player, "shop.seleccionado", Placeholder.parsed("name", killerId)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
            return
        }

        val econ = Mistaken.Companion.economy

        if (econ == null) {
            player.sendMessage(parseSafe("<red><b>[!]</b> Error interno: El sistema de economía no está conectado.</red>"))
            plugin.componentLogger.error("Purchase failed due to disconnected Economy: Player ${player.name}, Killer $killerId")
            return
        }

        val costo = precio.toDouble()

        if (econ.getBalance(player) >= costo) {
            val success = econ.withdraw(player, costo)

            if (success) {
                data.buyKiller(uuid, killerId)
                player.sendMessage(MessageService.getComponent(player, "shop.comprado", Placeholder.parsed("name", killerId)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)
                abrir(player)
            } else {
                player.sendMessage(MessageService.getComponent(player, "shop_errores.error_bancario", Placeholder.parsed("error", "Transacción fallida.")))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }

        } else {
            player.sendMessage(MessageService.getComponent(player, "errors.no-money"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
        }
    }


}
