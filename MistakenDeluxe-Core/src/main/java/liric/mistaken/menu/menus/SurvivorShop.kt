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
import liric.mistaken.api.requirements.RequirementEngine
import liric.mistaken.config.engine.core.MessageService


class SurvivorShop : MenuBase("survivors_shop") {

    private val survivorKey by lazy { NamespacedKey(plugin, "selected_survivor") }

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        // 1. Cargamos las dos fuentes de datos
        val globalMecanicas = plugin.configManager.getSurvivorConfig("global") // NÃºmeros e Ãtems

        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedSurvivor(uuid)

        // Labels generales desde messages.yml
        val labelHumano = MessageService.getComponent(player, "shop.clase-humana")
        val labelSeleccionado = MessageService.getComponent(player, "shop.estado-seleccionado")
        val labelPoseido = MessageService.getComponent(player, "shop.estado-poseido")
        val labelComprar = MessageService.getComponent(player, "shop.estado-comprar-survivor")
        val labelAbilityes = MessageService.getComponent(player, "shop.abilityes-titulo")

        var slotIndex = 0

        for (survivorId in plugin.survivorManager.catalogo.keys) {
            if (slotIndex >= slots.size) break

            val survivorConfig = plugin.configManager.getSurvivorConfig(survivorId)
            val permisoRequerido = survivorConfig.getString("permiso")
            if (permisoRequerido != null && !player.hasPermission(permisoRequerido)) continue

            // --- 🎨 DATOS VISUALES (Desde survivors_info.yml) ---
            // Ruta: survivors.<id>.nombre
            val nombreVisual = MessageService.getStrictString(player, "survivors.$survivorId.nombre", "survivors_info")
            
            val loreShop = MessageService.getStrictStringList(player, "survivors.$survivorId.lore_shop", "survivors_info")

            // --- ⚙️ DATOS MECÁNICOS (Desde survivors.yml) ---
            // Ruta: survivors.<id>.precio
            val precio = survivorConfig.getInt("precio", 0)
            // Ruta: survivors.<id>.icono_material
            val matStr = survivorConfig.getString("icono_material", "IRON_CHESTPLATE")!!
            val iconoMat = Material.matchMaterial(matStr.uppercase()) ?: Material.IRON_CHESTPLATE

            // --- ðŸ”¨ CONSTRUCCIÃ“N DEL LORE ---
            val fullLore = mutableListOf<Component>().apply {
                add(labelHumano)
                add(Component.empty())

                // DescripciÃ³n del personaje
                loreShop.forEach { line ->
                    add(parseSafe(line))
                }

                add(Component.empty())
                add(labelAbilityes)

                // Listar abilityes (Nombres desde INFO)
                // Ruta: survivors.<id>.skill_names.abilityX
                for (i in 1..3) {
                    val habName = MessageService.getRawString(player, "survivors.$survivorId.skill_names.ability$i", "", "survivors_info")
                    if (habName.isNotEmpty()) {
                        add(parseSafe(" <dark_gray>â€¢</dark_gray> <white>$habName</white>"))
                    }
                }
                add(Component.empty())
            }

            // Estado de Compra/SelecciÃ³n
            val tiene = data.tieneSurvivor(uuid, survivorId)
            val esSeleccionado = selected.equals(survivorId, ignoreCase = true)

            val reqMessages = RequirementEngine.getRequirementMessages(player, "survivors", survivorId)
            reqMessages.forEach { fullLore.add(parseSafe(it)) }

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(MessageService.getComponent(player, "shop.estado-precio", Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            // --- RENDERIZADO ---
            gui.setItem(slots[slotIndex], ItemBuilder.from(iconoMat)
                .name(parseSafe(nombreVisual))
                .lore(fullLore.toList())
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem { event ->
                    event.isCancelled = true
                    if (reqMessages.isNotEmpty()) {
                        player.sendMessage(parseSafe("<red>No cumples los requisitos para este survivor.</red>"))
                        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
                        return@asGuiItem
                    }
                    handleLogic(player, survivorId, precio, tiene)
                }
            )
            slotIndex++
        }

        val botonAtrasMat = config.getString("ajustes.atras.material", "ARROW")!!
        val botonAtrasNombre = config.getString("ajustes.atras.nombre", "AtrÃ¡s")!!
        val botonAtrasSlot = config.getInt("ajustes.atras.slot", 40)
        val matAtras = Material.matchMaterial(botonAtrasMat.uppercase()) ?: Material.ARROW
        gui.setItem(botonAtrasSlot, ItemBuilder.from(matAtras)
            .name(parseSafe(botonAtrasNombre))
            .asGuiItem { event ->
                event.isCancelled = true
                ShopSelector().abrir(player)
            })
    }

    private fun handleLogic(player: Player, id: String, precio: Int, tiene: Boolean) {
        val uuid = player.uniqueId
        val data = plugin.playerDataManager
        val actual = data.getSelectedSurvivor(uuid)

        // 1. Ya seleccionado
        if (id.equals(actual, ignoreCase = true)) {
            player.sendMessage(MessageService.getComponent(player, "shop.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        // 2. Ya comprado -> Seleccionar
        if (tiene) {
            data.setSelectedSurvivor(uuid, id)
            player.persistentDataContainer.set(survivorKey, PersistentDataType.STRING, id)

            // Obtenemos el nombre bonito para el mensaje de confirmaciÃ³n
            val nombreVisual = MessageService.getStrictString(player, "survivors.$id.nombre", "survivors_info")

            player.sendMessage(MessageService.getComponent(player, "shop.seleccionado", Placeholder.component("name", parseSafe(nombreVisual))))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
            return
        }

        // 3. Comprar (Vault)
        val econ = Mistaken.Companion.economy
        if (econ == null) {
            player.sendMessage(parseSafe("<red>Error: Vault no estÃ¡ conectado.</red>"))
            return
        }

        val costo = precio.toDouble()

        if (econ.has(player, costo)) {
            val response = econ.withdrawPlayer(player, costo)
            if (response.transactionSuccess()) {
                data.comprarSurvivor(uuid, id)

                val nombreVisual = MessageService.getStrictString(player, "survivors.$id.nombre", "survivors_info")

                player.sendMessage(MessageService.getComponent(player, "shop.comprado", Placeholder.component("name", parseSafe(nombreVisual))))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                abrir(player)
            } else {
                player.sendMessage(MessageService.getComponent(player, "shop_errores.error_bancario", Placeholder.parsed("error", response.errorMessage ?: "Unknown error")))
            }
        } else {
            player.sendMessage(MessageService.getComponent(player, "errors.no-money"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
        }
    }
}




