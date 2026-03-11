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
 * Optimizado para Paper 1.21.4+ y blindado contra desyncs de Vault.
 */
class AsesinoTienda : MenuBase("asesinos_tienda") {

    companion object {
        private val EXCLUSIVE_IDS = setOf("devesto", "mariachi", "romeo", "bendy", "coolkid", "sowoul")
        private val SPECIAL_IDS = setOf("teto", "miku", "charlie", "colorandelectricity")
    }

    override fun setupItems(player: Player, gui: Gui, config: FileConfiguration) {
        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")
        val globalMecanicas = plugin.configManager.getAsesinos()

        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedKiller(uuid)

        // CORRECCIÓN: Usamos getMessage() en lugar de getMessageComponent()
        val labelSeleccionado = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val labelPoseido = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val labelComprar = plugin.messageConfig.getMessage(player, "tienda.estado-comprar")
        val labelHabilidades = plugin.messageConfig.getMessage(player, "tienda.habilidades-titulo")

        var slotIndex = 0

        for (killerId in plugin.asesinoManager.getClasesDisponibles().keys) {
            if (slotIndex >= slots.size) break
            if (!tienePermisoParaVer(player, killerId)) continue

            // --- 🎨 DATOS VISUALES ---
            val nombreVisual = langInfo.getString("asesinos.$killerId.nombre") ?: killerId
            val descripcion = langInfo.getStringList("asesinos.$killerId.descripcion")
            val loreTienda = langInfo.getStringList("asesinos.$killerId.lore_tienda")

            // --- ⚙️ DATOS MECÁNICOS ---
            val precio = globalMecanicas.getInt("asesinos.$killerId.precio", 0)
            val matStr = globalMecanicas.getString("asesinos.$killerId.icono_material", "STONE")!!
            val iconoMat = Material.matchMaterial(matStr) ?: Material.STONE

            // --- 🔨 CONSTRUCCIÓN DEL LORE ---
            val fullLore = mutableListOf<Component>()

            descripcion.forEach { fullLore.add(mm.deserialize(it)) }
            fullLore.add(Component.empty())
            loreTienda.forEach { fullLore.add(mm.deserialize("<i><gray>$it</gray></i>")) }
            fullLore.add(Component.empty())

            fullLore.add(labelHabilidades)
            for (i in 1..4) {
                val habName = langInfo.getString("asesinos.$killerId.habilidades_nombres.habilidad$i")
                if (habName != null) fullLore.add(mm.deserialize(" <dark_gray>•</dark_gray> <white>$habName</white>"))
            }
            fullLore.add(Component.empty())

            val tiene = data.tieneAsesino(uuid, killerId)
            val esSeleccionado = selected.equals(killerId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(labelSeleccionado)
                tiene -> fullLore.add(labelPoseido)
                else -> {
                    // CORRECCIÓN: Usamos getMessage()
                    fullLore.add(plugin.messageConfig.getMessage(player, "tienda.estado-precio", Placeholder.parsed("amount", precio.toString())))
                    fullLore.add(labelComprar)
                }
            }

            // --- RENDERIZADO EN EL GUI ---
            gui.setItem(slots[slotIndex], ItemBuilder.from(iconoMat)
                .name(mm.deserialize(nombreVisual))
                .lore(fullLore.toList())
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem { event ->
                    event.isCancelled = true // Seguridad TriumphGUI extra
                    handlePurchaseLogic(player, killerId, precio, tiene)
                }
            )

            slotIndex++
        }
    }

    private fun handlePurchaseLogic(player: Player, killerId: String, precio: Int, tiene: Boolean) {
        val uuid = player.uniqueId
        val data = plugin.playerDataManager
        val actual = data.getSelectedKiller(uuid)

        // 1. Si ya lo tiene seleccionado
        if (killerId.equals(actual, ignoreCase = true)) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        // 2. Si ya es dueño pero no está seleccionado
        if (tiene) {
            data.setSelectedKiller(uuid, killerId)
            player.persistentDataContainer.set(plugin.assassinKey, PersistentDataType.STRING, killerId)
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.seleccionado", Placeholder.parsed("name", killerId)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
            abrir(player) // Recargar menú
            return
        }

        // 3. 🚨 LÓGICA DE COMPRA ESTRICTA (VAULT SAFE) 🚨
        val econ = Mistaken.economy

        if (econ == null) {
            player.sendMessage(mm.deserialize("<red><b>[!]</b> Error interno: El sistema de economía (Vault) no está conectado.</red>"))
            plugin.componentLogger.error(mm.deserialize("<red>Se intentó una compra de $killerId por el jugador ${player.name} pero 'Mistaken.economy' es NULL.</red>"))
            return
        }

        val costo = precio.toDouble() // Convertimos siempre a Double explícito para Vault

        // Usamos la instancia exacta del Player para evitar desync de UUID/Nombres
        if (econ.has(player, costo)) {

            // Retiramos el dinero y GUARDAMOS LA RESPUESTA
            val response = econ.withdrawPlayer(player, costo)

            if (response.transactionSuccess()) {
                // ✅ COMPRA 100% EXITOSA APROBADA POR ESSENTIALS/CMI
                data.comprarAsesino(uuid, killerId)
                player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.comprado", Placeholder.parsed("name", killerId)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)
                abrir(player) // Recargar menú para actualizar el ícono
            } else {
                // ❌ FALLÓ EL RETIRO DESDE EL LADO DE LA ECONOMÍA
                player.sendMessage(mm.deserialize("<red>Hubo un problema con tu banco al cobrarte: ${response.errorMessage}</red>"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }

        } else {
            // ❌ NO TIENE DINERO
            val balanceActual = econ.getBalance(player)

            player.sendMessage(plugin.messageConfig.getMessage(player, "errors.no-money"))

            // MENSAJE DE DEPURACIÓN ÚTIL (Solo lo verán si fallan la compra)
            // player.sendMessage(mm.deserialize("<gray><i>(Debug: El costo es <gold>$$costo</gold> pero el banco detecta que tienes <gold>$$balanceActual</gold>)</i></gray>"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
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
