package liric.mistaken.supervivientes

import dev.triumphteam.gui.builder.item.ItemBuilder
import liric.mistaken.Mistaken
import liric.mistaken.menu.MenuBase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.persistence.PersistentDataType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * SupervivienteTienda: Interfaz de gestión de clases humanas.
 * Optimización: Hybrid Cache (Componentes pre-renderizados) y persistencia en PDC.
 */
class SupervivienteTienda : MenuBase("supervivientes_tienda") {

    companion object {
        private val baseDataCache = ConcurrentHashMap<String, SurvivorBaseData>()
        private var clickSoundCache: Sound = Sound.BLOCK_NOTE_BLOCK_CHIME
    }

    /**
     * Contenedor de datos estáticos para evitar re-lecturas de YAML.
     */
    data class SurvivorBaseData(
        val displayName: Component,
        val material: Material,
        val precio: Int,
        val staticLore: List<Component>
    )

    init {
        preLoadBaseData()
    }

    /**
     * Carga y procesa los datos del archivo de configuración de supervivientes.
     */
    private fun preLoadBaseData() {
        val soundName = config.getString("ajustes.sonido-click", "BLOCK_NOTE_BLOCK_CHIME")
        clickSoundCache = try {
            Sound.valueOf(soundName?.uppercase() ?: "BLOCK_NOTE_BLOCK_CHIME")
        } catch (e: Exception) {
            Sound.BLOCK_NOTE_BLOCK_CHIME
        }

        val survConfig = plugin.configManager.getSupervivientes()
        val section = survConfig.getConfigurationSection("supervivientes") ?: return

        baseDataCache.clear()

        for (id in section.getKeys(false)) {
            val sub = section.getConfigurationSection(id) ?: continue

            val displayName = mm.deserialize(sub.getString("nombre", id.uppercase())!!)
            val precio = sub.getInt("precio", 0)
            val mat = Material.matchMaterial(sub.getString("icono_material", "LEATHER_CHESTPLATE")!!) ?: Material.LEATHER_CHESTPLATE

            val sLore = mutableListOf<Component>()
            sub.getStringList("lore_tienda").forEach { line ->
                sLore.add(mm.deserialize("<italic><dark_gray>$line"))
            }

            sLore.add(Component.empty())
            sLore.add(plugin.messageConfig.getMessage(null, "tienda.habilidades-titulo"))

            // Pre-cargamos los nombres de las habilidades
            for (i in 1..4) {
                sub.getString("items.habilidad${i}_nombre")?.let { hab ->
                    sLore.add(mm.deserialize(" <dark_gray>• <white>$hab"))
                }
            }

            baseDataCache[id] = SurvivorBaseData(displayName, mat, precio, sLore)
        }
    }

    /**
     * Actualiza el caché si se recarga el plugin.
     */
    fun reload() {
        this.reloadGui()
        preLoadBaseData()
    }

    override fun setupItems(player: Player) {
        val slots = config.getIntegerList("ajustes.slots-disponibles")
        if (slots.isEmpty()) return

        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val selected = data.getSelectedSurvivor(uuid)

        // Cachear etiquetas traducidas para este jugador
        val humanoLabel = plugin.messageConfig.getMessage(player, "tienda.clase-humana")
        val estadoSeleccionado = plugin.messageConfig.getMessage(player, "tienda.estado-seleccionado")
        val estadoPoseido = plugin.messageConfig.getMessage(player, "tienda.estado-poseido")
        val comprarLabel = plugin.messageConfig.getMessage(player, "tienda.estado-comprar-superviviente")

        var currentSlotIndex = 0

        // Iterar sobre las clases registradas en el manager
        for (survivorId in plugin.supervivienteManager.getClasesDisponibles().keys) {
            if (currentSlotIndex >= slots.size) break

            val base = baseDataCache[survivorId] ?: continue

            val fullLore = mutableListOf<Component>().apply {
                add(humanoLabel)
                add(Component.empty())
                addAll(base.staticLore)
                add(Component.empty())
            }

            val tiene = data.tieneSuperviviente(uuid, survivorId)
            val esSeleccionado = selected.equals(survivorId, ignoreCase = true)

            when {
                esSeleccionado -> fullLore.add(estadoSeleccionado)
                tiene -> fullLore.add(estadoPoseido)
                else -> {
                    fullLore.add(plugin.messageConfig.getMessage(player, "tienda.estado-precio",
                        Placeholder.parsed("amount", base.precio.toString())))
                    fullLore.add(comprarLabel)
                }
            }

            gui?.setItem(slots[currentSlotIndex], ItemBuilder.from(base.material)
                .name(base.displayName)
                .lore(fullLore.toList())
                .flags(*ItemFlag.entries.toTypedArray())
                .asGuiItem { _ ->
                    handleLogic(player, survivorId, base.precio)
                }
            )

            currentSlotIndex++
        }
    }

    private fun handleLogic(player: Player, id: String, precio: Int) {
        val data = plugin.playerDataManager
        val uuid = player.uniqueId
        val actual = data.getSelectedSurvivor(uuid)
        val tiene = data.tieneSuperviviente(uuid, id)

        if (id.equals(actual, ignoreCase = true)) {
            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.ya-seleccionado"))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
            return
        }

        if (tiene) {
            // Acción: Seleccionar
            data.setSelectedSurvivor(uuid, id)

            // Persistencia en PDC de Paper para integraciones externas
            val key = NamespacedKey(plugin, "selected_survivor")
            player.persistentDataContainer.set(key, PersistentDataType.STRING, id)

            player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.seleccionado",
                Placeholder.parsed("name", id)))
            player.playSound(player.location, clickSoundCache, 1f, 1.2f)

            abrir(player) // Refrescar UI
        } else {
            // Acción: Comprar
            val economy = Mistaken.economy
            if (economy != null && economy.has(player, precio.toDouble())) {
                economy.withdrawPlayer(player, precio.toDouble())
                data.comprarSuperviviente(uuid, id)

                player.sendMessage(plugin.messageConfig.getMessage(player, "tienda.comprado",
                    Placeholder.parsed("name", id)))
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)

                abrir(player) // Refrescar UI
            } else {
                player.sendMessage(plugin.messageConfig.getMessage(player, "errors.no-money"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f)
            }
        }
    }
}
