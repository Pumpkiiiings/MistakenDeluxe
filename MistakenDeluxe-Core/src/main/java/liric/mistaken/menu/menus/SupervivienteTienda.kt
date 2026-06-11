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
 * OPTIMIZADO: Lectura híbrida (Mecánicas Globales + Info Localizada).
 */
class SupervivienteTienda : MenuBase("supervivientes_tienda") {

    private val survivorKey by lazy { NamespacedKey(plugin, "selected_survivor") }

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        // 1. Cargamos las dos fuentes de datos
        val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info") // Texto
        val globalMecanicas = plugin.configManager.getSupervivientes() // Números e Ítems

        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedSurvivor(uuid)

        // Labels generales desde messages.yml
        val labelHumano = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.clase-humana")
        val labelSeleccionado = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.estado-seleccionado")
        val labelPoseido = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.estado-poseido")
        val labelComprar = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.estado-comprar-superviviente")
        val labelHabilidades = pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.habilidades-titulo")

        var slotIndex = 0

        for (survivorId in plugin.supervivienteManager.getClasesDisponibles().keys) {
            if (slotIndex >= slots.size) break

            // --- 🎨 DATOS VISUALES (Desde supervivientes_info.yml) ---
            // Ruta: supervivientes.<id>.nombre
            val nombreVisual = langInfo.getString("supervivientes.$survivorId.nombre") ?: survivorId.uppercase()
            // Ruta: supervivientes.<id>.lore_tienda
            val loreTienda = langInfo.getStringList("supervivientes.$survivorId.lore_tienda")

            // --- ⚙️ DATOS MECÁNICOS (Desde supervivientes.yml) ---
            // Ruta: supervivientes.<id>.precio
            val precio = globalMecanicas.getInt("supervivientes.$survivorId.precio", 0)
            // Ruta: supervivientes.<id>.icono_material
            val matStr = globalMecanicas.getString("supervivientes.$survivorId.icono_material", "IRON_CHESTPLATE")!!
            val iconoMat = Material.matchMaterial(matStr) ?: Material.IRON_CHESTPLATE

            // --- 🔨 CONSTRUCCIÓN DEL LORE ---
            val fullLore = mutableListOf<Component>().apply {
                add(labelHumano)
                add(Component.empty())

                // Descripción del personaje
                loreTienda.forEach { line ->
                    add(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda_errores.lore_tienda_format", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("line", line)))
                }

                add(Component.empty())
                add(labelHabilidades)

                // Listar habilidades (Nombres desde INFO)
                // Ruta: supervivientes.<id>.habilidades_nombres.habilidadX
                for (i in 1..3) {
                    val habName = langInfo.getString("supervivientes.$survivorId.habilidades_nombres.habilidad$i")
                    if (!habName.isNullOrEmpty()) {
                        add(mm.deserialize(" <dark_gray>•</dark_gray> <white>$habName</white>"))
                    }
                }
                add(Component.empty())
            }

            // Estado de Compra/Selección
            val tiene = data.tieneSuperviviente(uuid, survivorId)
            val esSeleccionado = selected.equals(survivorId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    fullLore.add(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.estado-precio", Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            // --- RENDERIZADO ---
            gui.setItem(slots[slotIndex], ItemBuilder.from(iconoMat)
                .name(mm.deserialize(nombreVisual))
                .lore(fullLore.toList())
                .flags(*ItemFlag.entries.toTypedArray())
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

        // 1. Ya seleccionado
        if (id.equals(actual, ignoreCase = true)) {
            player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        // 2. Ya comprado -> Seleccionar
        if (tiene) {
            data.setSelectedSurvivor(uuid, id)
            player.persistentDataContainer.set(survivorKey, PersistentDataType.STRING, id)

            // Obtenemos el nombre bonito para el mensaje de confirmación
            val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
            val nombreVisual = langInfo.getString("supervivientes.$id.nombre") ?: id

            player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.seleccionado", Placeholder.component("name", mm.deserialize(nombreVisual))))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player)
            return
        }

        // 3. Comprar (Vault)
        val econ = Mistaken.Companion.economy
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

                player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda.comprado", Placeholder.component("name", mm.deserialize(nombreVisual))))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                abrir(player)
            } else {
                player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "tienda_errores.error_bancario", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("error", response.errorMessage ?: "Unknown error")))
            }
        } else {
            player.sendMessage(pumpking.lib.service.PumpkingServiceManager.messages.getComponent(player, "errors.no-money"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
        }
    }
}


