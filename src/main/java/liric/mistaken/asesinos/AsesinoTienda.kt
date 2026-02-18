package liric.mistaken.asesinos

import dev.triumphteam.gui.builder.item.ItemBuilder
import liric.mistaken.Mistaken
import liric.mistaken.menu.MenuBase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.persistence.PersistentDataType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * AsesinoTienda: Interfaz de adquisición de entes.
 * Optimización: Hybrid Cache de componentes y filtrado de permisos O(1).
 */
class AsesinoTienda : MenuBase("asesinos_tienda") {

    companion object {
        private val baseDataCache = ConcurrentHashMap<String, KillerBaseData>()
        private var clickSoundCache: Sound = Sound.UI_BUTTON_CLICK

        // Listas de permisos para filtrado rápido
        private val EXCLUSIVE_IDS = setOf("devesto", "mariachi", "romeo", "67dev", "coolkid")
        private val SPECIAL_IDS = setOf("teto", "miku", "charlie", "colorandelectricity")
    }

    /**
     * Contenedor de datos estáticos pre-procesados.
     */
    data class KillerBaseData(
        val displayName: Component,
        val material: Material,
        val precio: Int,
        val staticLore: List<Component>
    )

    init {
        preLoadBaseData()
    }

    /**
     * Cachea los datos pesados (YAML y MiniMessage) para no procesarlos en cada apertura.
     */
    private fun preLoadBaseData() {
        val sName = config.getString("ajustes.sonido-click", "UI_BUTTON_CLICK")
        clickSoundCache = try {
            Sound.valueOf(sName?.uppercase() ?: "UI_BUTTON_CLICK")
        } catch (e: Exception) {
            Sound.UI_BUTTON_CLICK
        }

        val killerConfig = plugin.configManager.getAsesinos()
        val section = killerConfig.getConfigurationSection("asesinos") ?: return

        baseDataCache.clear()

        for (id in section.getKeys(false)) {
            val sub = section.getConfigurationSection(id) ?: continue

            // Pre-deserializar componentes
            val nameComp = mm.deserialize(sub.getString("nombre", id.uppercase())!!)
            val precio = sub.getInt("precio", 0)
            val mat = Material.matchMaterial(sub.getString("icono_material", "STONE")!!) ?: Material.STONE

            val sLore = mutableListOf<Component>()
            sub.getStringList("lore_tienda").forEach { line ->
                sLore.add(mm.deserialize("<gray>$line"))
            }
            sLore.add(Component.empty())
            sLore.add(plugin.messageConfig.getMessage(null, "tienda.habilidades-titulo"))

            // Cachear nombres de habilidades
            for (i in 1..4) {
                sub.getString("items.habilidad${i}_nombre")?.let { hab ->
                    sLore.add(mm.deserialize(" <dark_gray>• <aqua>$hab"))
                }
            }

            baseDataCache[id] = KillerBaseData(nameComp, mat, precio, sLore)
        }
    }

    override fun setupItems(player: Player) {
        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedKiller(uuid)

        // Cachear etiquetas comunes del idioma del jugador
        val claseLabel = plugin.messageConfig.getMessage(player, "tienda.clase-especial")
        val selLabel = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val poseidoLabel = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val comprarLabel = plugin.messageConfig.getMessage(player, "tienda.estado-comprar")

        var slotIndex = 0

        // Iterar sobre las clases registradas en el manager
        for (killerId in plugin.asesinoManager.getClasesDisponibles().keys) {
            if (slotIndex >= slots.size) break
            if (!tienePermisoParaVer(player, killerId)) continue

            val base = baseDataCache[killerId] ?: continue

            val fullLore = mutableListOf<Component>().apply {
                add(claseLabel)
                add(Component.empty())
                addAll(base.staticLore)
                add(Component.empty())
            }

            val tiene = data.tieneAsesino(uuid, killerId)
            val esSeleccionado = selected.equals(killerId, ignoreCase = true)

            // Lógica de estado visual
            when {
                esSeleccionado -> fullLore.add(selLabel)
                tiene -> fullLore.add(poseidoLabel)
                else -> {
                    fullLore.add(plugin.messageConfig.getMessage(player, "tienda.estado-precio",
                        Placeholder.parsed("amount", base.precio.toString())))
                    fullLore.add(comprarLabel)
                }
            }

            gui?.setItem(slots[slotIndex], ItemBuilder.from(base.material)
                .name(base.displayName)
                .lore(fullLore.toList())
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem { event ->
                    handlePurchaseLogic(player, killerId, base.precio)
                }
            )

            slotIndex++
        }
    }

    private fun handlePurchaseLogic(player: Player, killerId: String, precio: Int) {
        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val actual = data.getSelectedKiller(uuid)
        val tiene = data.tieneAsesino(uuid, killerId)

        if (killerId.equals(actual, ignoreCase = true)) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (tiene) {
            // Seleccionar asesino existente
            data.setSelectedKiller(uuid, killerId)
            // Persistencia en PDC para compatibilidad con otros sistemas NMS
            player.persistentDataContainer.set(plugin.assassinKey, PersistentDataType.STRING, killerId)

            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.seleccionado",
                Placeholder.parsed("name", killerId)))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)

            // Refrescar el menú para actualizar los ítems visualmente
            abrir(player)
        } else {
            // Lógica de compra mediante Vault
            val economy = Mistaken.economy
            if (economy != null && economy.has(player, precio.toDouble())) {
                economy.withdrawPlayer(player, precio.toDouble())
                data.comprarAsesino(uuid, killerId)

                player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.comprado",
                    Placeholder.parsed("name", killerId)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f)

                abrir(player)
            } else {
                player.sendMessage(plugin.messageConfig.getMessage(player, "errors.no-money"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }
        }
    }

    private fun tienePermisoParaVer(player: Player, killerId: String): Boolean {
        val id = killerId.lowercase()
        return when {
            id in EXCLUSIVE_IDS -> player.hasPermission("mistaken.skins.exclusivo")
            id in SPECIAL_IDS -> player.hasPermission("mistaken.skins.especiales")
            else -> true
        }
    }

    /**
     * Recarga los datos estáticos del caché.
     */
    fun reload() {
        this.reloadGui() // Método de MenuBase
        preLoadBaseData()
    }
}
