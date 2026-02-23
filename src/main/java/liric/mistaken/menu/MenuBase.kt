package liric.mistaken.menu

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import liric.mistaken.Mistaken
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * MenuBase: Estructura multilingüe ultra-optimizada.
 * Los menús se cachean por IDIOMA, no por jugador, para ahorrar RAM.
 */
abstract class MenuBase(private val fileName: String) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    // --- 🚀 EL SECRETO DEL RENDIMIENTO ---
    // Guardamos las decoraciones ya procesadas por cada idioma.
    // Mapa: <"es", CacheDeMenu>
    private val langCache = ConcurrentHashMap<String, MenuLanguageData>()

    /**
     * Clase interna para guardar los datos ya procesados de un idioma.
     */
    private data class MenuLanguageData(
        val titulo: String,
        val filas: Int,
        val decorations: List<Pair<List<Int>, GuiItem>>
    )

    /**
     * Obtiene o genera los datos del menú para el idioma del jugador.
     */
    private fun getMenuData(player: Player): MenuLanguageData {
        val lang = player.let { plugin.playerDataManager.getLanguage(it.uniqueId) } ?: "es"

        // Si el idioma ya está en caché, lo devolvemos al instante (0% CPU)
        return langCache.getOrPut(lang) {
            val config = plugin.messageConfig.getSpecificFile(player, fileName)

            val titulo = config.getString("titulo") ?: "<red>Menú: $fileName"
            val filas = config.getInt("filas", 3)
            val decorList = mutableListOf<Pair<List<Int>, GuiItem>>()

            // Procesar decoraciones del YAML
            val decorSection = config.getConfigurationSection("decoraciones")
            decorSection?.getKeys(false)?.forEach { key ->
                val material = Material.matchMaterial(decorSection.getString("$key.material", "AIR")!!) ?: Material.AIR
                val display = decorSection.getString("$key.nombre", " ")!!
                val slots = decorSection.getIntegerList("$key.slots")

                if (material != Material.AIR) {
                    val item = ItemBuilder.from(material).name(mm.deserialize(display)).asGuiItem()
                    decorList.add(Pair(slots, item))
                }
            }

            MenuLanguageData(titulo, filas, decorList)
        }
    }

    /**
     * Abre el menú al jugador detectando su idioma.
     */
    open fun abrir(player: Player) {
        // 1. Obtenemos los datos (Título, Filas, Decoraciones) del caché de su idioma
        val data = getMenuData(player)

        // 2. Creamos la instancia del GUI (Las GUIs deben ser nuevas por jugador para evitar bugs)
        val gui = Gui.gui()
            .title(mm.deserialize(data.titulo))
            .rows(data.filas)
            .disableAllInteractions()
            .create()

        // 3. Aplicamos decoraciones desde el caché (Súper rápido)
        data.decorations.forEach { (slots, item) ->
            gui.setItem(slots, item)
        }

        // 4. Lógica de ítems dinámicos del hijo (Tienda, stats, etc.)
        // Pasamos el archivo config específico para que el hijo también sepa el lang
        val config = plugin.messageConfig.getSpecificFile(player, fileName)
        setupItems(player, gui, config)

        gui.open(player)
    }

    /**
     * @param config El archivo YAML del idioma del jugador (ej: lang/en/tienda.yml)
     */
    abstract fun setupItems(player: Player, gui: Gui, config: FileConfiguration)

    /**
     * Limpia el caché para que los cambios en el YAML se vean sin reiniciar.
     */
    fun reload() {
        langCache.clear()
    }
}
