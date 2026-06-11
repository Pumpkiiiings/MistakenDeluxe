package liric.mistaken.menu

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import liric.mistaken.Mistaken
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.1]
 * MenuBase: Motor de menús globalizado.
 *
 * --- DISEÑO ---
 * Los YAMLs de menú son ahora GLOBALES (menus/<nombre>.yml), uno solo para
 * todos los idiomas. El título y los textos de items dinámicos se resuelven
 * desde el messages.yml del idioma del jugador usando la token %menu_title%:
 *
 *   menus/<nombre>.yml          → layout, slots, decoraciones (sin duplicar por idioma)
 *   langs/<lang>/messages.yml  → menus.<nombre>.titulo  (texto internacionalizado)
 *
 * Esto elimina por completo la carpeta langs/<lang>/menus/ y centraliza
 * toda la traducción en un único archivo messages.yml por idioma.
 *
 * --- CACHÉ ---
 * Las decoraciones se cachean por IDIOMA (no por jugador) porque el título
 * varía según el idioma pero el layout es idéntico para todos.
 */
abstract class MenuBase(
    /** Nombre del archivo YAML sin extensión, ej: "asesinos_tienda" */
    private val menuName: String
) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    /**
     * Parsea un texto con MiniMessage desactivando la cursiva (italic) por defecto,
     * ya que Minecraft la aplica forzosamente al lore de los items.
     */
    protected fun parseSafe(text: String): net.kyori.adventure.text.Component {
        return mm.deserialize("<!italic>$text")
    }

    // Clave en messages.yml donde se lee el título traducido.
    // Por defecto: menus.<menuName>.titulo
    protected open val titleMessageKey: String get() = "menus.$menuName.titulo"

    // Fallback si no se encuentra la clave en messages.yml
    protected open val titleFallback: String get() = "<red>Menu: $menuName"

    // Cache de decoraciones procesadas, agrupadas por idioma del jugador.
    // El título está incluido porque varía por idioma.
    private val langCache = ConcurrentHashMap<String, MenuBakedData>()

    // Config global del menú (cargada una sola vez desde menus/<nombre>.yml)
    private var globalConfig: FileConfiguration? = null

    // =========================================================================
    // API PÚBLICA — para subclases y acceso externo
    // =========================================================================

    /**
     * Obtiene la configuración global del menú (layout, slots, decoraciones).
     * Carga lazy desde disco: menus/<menuName>.yml dentro del dataFolder del plugin.
     */
    fun getGlobalConfig(): FileConfiguration {
        return globalConfig ?: loadGlobalConfig().also { globalConfig = it }
    }

    /**
     * Abre el menú al jugador resolviendo su idioma automáticamente.
     */
    open fun abrir(player: Player) {
        val baked = getBakedData(player)
        val config = getGlobalConfig()

        val gui = Gui.gui()
            .title(mm.deserialize(baked.resolvedTitle))
            .rows(baked.filas)
            .disableAllInteractions()
            .create()

        // Aplicar decoraciones desde caché
        baked.decorations.forEach { (slots, item) -> gui.setItem(slots, item) }

        // Lógica de items dinámicos (implementada por cada subclase)
        setupItems(player, gui, config)

        gui.open(player)
    }

    /**
     * Método que las subclases implementan para añadir sus items dinámicos.
     *
     * @param player El jugador que abre el menú.
     * @param gui    La instancia de GUI ya decorada.
     * @param config El FileConfiguration global del menú (menus/<nombre>.yml).
     *               Use [getTranslatedString] para obtener textos localizados.
     */
    abstract fun setupItems(player: Player, gui: Gui, config: FileConfiguration)

    /**
     * Obtiene un texto traducido desde messages.yml del jugador.
     * Reemplaza la antigua necesidad de tener un YAML de menú por idioma.
     *
     * @param player El jugador cuyo idioma se usará.
     * @param path   La ruta en messages.yml, ej: "menus.tienda_principal.items.asesinos.nombre"
     * @param def    Valor por defecto si no se encuentra la clave.
     */
    fun getTranslatedString(player: Player, path: String, def: String = "<red>Missing: $path"): String {
        return pumpking.lib.service.PumpkingServiceManager.messages.getRawString(player, path, def, "messages")
    }

    /**
     * Obtiene una lista de strings traducidos desde messages.yml del jugador.
     */
    fun getTranslatedList(player: Player, path: String): List<String> {
        return pumpking.lib.service.PumpkingServiceManager.messages.getRawStringList(player, path, "messages")
    }


    /**
     * Invalida el caché del menú para que los cambios en YAML y messages.yml
     * se reflejen sin reiniciar el servidor (utilizado en /mistaken reload).
     */
    fun reload() {
        langCache.clear()
        globalConfig = null
    }

    // =========================================================================
    // INTERNOS
    // =========================================================================

    private data class MenuBakedData(
        val resolvedTitle: String,
        val filas: Int,
        val decorations: List<Pair<List<Int>, GuiItem>>
    )

    /**
     * Obtiene (o genera y cachea) los datos decorativos del menú para el idioma del jugador.
     * El título se resuelve desde messages.yml usando [titleMessageKey].
     */
    private fun getBakedData(player: Player): MenuBakedData {
        val lang = plugin.playerDataManager.getLanguage(player.uniqueId)

        return langCache.getOrPut(lang) {
            val config = getGlobalConfig()

            // Resolver el título desde messages.yml (no desde el YAML del menú)
            val rawTitle = pumpking.lib.service.PumpkingServiceManager.messages.getRawString(player, titleMessageKey, titleFallback, "messages")

            val filas = config.getInt("filas", 3)
            val decorList = mutableListOf<Pair<List<Int>, GuiItem>>()

            val decorSection = config.getConfigurationSection("decoraciones")
            decorSection?.getKeys(false)?.forEach { key ->
                val matStr = decorSection.getString("$key.material", "AIR") ?: "AIR"
                val material = Material.matchMaterial(matStr.uppercase()) ?: Material.AIR
                val display = decorSection.getString("$key.nombre", " ") ?: " "
                val slots = decorSection.getIntegerList("$key.slots")

                if (material != Material.AIR && slots.isNotEmpty()) {
                    val item = ItemBuilder.from(material).name(parseSafe(display)).asGuiItem()
                    decorList.add(Pair(slots, item))
                }
            }

            MenuBakedData(rawTitle, filas, decorList)
        }
    }

    /**
     * Carga el YAML global del menú usando PumpkingLib ConfigManager
     */
    private fun loadGlobalConfig(): FileConfiguration {
        return pumpking.lib.config.ConfigManager.getMenuConfig(menuName)
    }
}

