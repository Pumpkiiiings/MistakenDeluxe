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
 * MenuBase: Motor de menÃºs globalizado.
 *
 * --- DISEÃ‘O ---
 * Los YAMLs de menÃº son ahora GLOBALES (menus/<nombre>.yml), uno solo para
 * todos los idiomas. El tÃ­tulo y los textos de items dinÃ¡micos se resuelven
 * desde el messages.yml del idioma del jugador usando la token %menu_title%:
 *
 *   menus/<nombre>.yml          â†’ layout, slots, decoraciones (sin duplicar por idioma)
 *   langs/<lang>/messages.yml  â†’ menus.<nombre>.titulo  (texto internacionalizado)
 *
 * Esto elimina por completo la carpeta langs/<lang>/menus/ y centraliza
 * toda la traducciÃ³n en un Ãºnico archivo messages.yml por idioma.
 *
 * --- CACHÃ‰ ---
 * Las decoraciones se cachean por IDIOMA (no por jugador) porque el tÃ­tulo
 * varÃ­a segÃºn el idioma pero el layout es idÃ©ntico para todos.
 */
abstract class MenuBase(
    /** Nombre del archivo YAML sin extensiÃ³n, ej: "asesinos_tienda" */
    private val menuName: String
) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    // Clave en messages.yml donde se lee el tÃ­tulo traducido.
    // Por defecto: menus.<menuName>.titulo
    protected open val titleMessageKey: String get() = "menus.$menuName.titulo"

    // Fallback si no se encuentra la clave en messages.yml
    protected open val titleFallback: String get() = "<red>Menu: $menuName"

    // Cache de decoraciones procesadas, agrupadas por idioma del jugador.
    // El tÃ­tulo estÃ¡ incluido porque varÃ­a por idioma.
    private val langCache = ConcurrentHashMap<String, MenuBakedData>()

    // Config global del menÃº (cargada una sola vez desde menus/<nombre>.yml)
    private var globalConfig: FileConfiguration? = null

    // =========================================================================
    // API PÃšBLICA â€” para subclases y acceso externo
    // =========================================================================

    /**
     * Obtiene la configuraciÃ³n global del menÃº (layout, slots, decoraciones).
     * Carga lazy desde disco: menus/<menuName>.yml dentro del dataFolder del plugin.
     */
    fun getGlobalConfig(): FileConfiguration {
        return globalConfig ?: loadGlobalConfig().also { globalConfig = it }
    }

    /**
     * Abre el menÃº al jugador resolviendo su idioma automÃ¡ticamente.
     */
    open fun abrir(player: Player) {
        val baked = getBakedData(player)
        val config = getGlobalConfig()

        val gui = Gui.gui()
            .title(mm.deserialize(baked.resolvedTitle))
            .rows(baked.filas)
            .disableAllInteractions()
            .create()

        // Aplicar decoraciones desde cachÃ©
        baked.decorations.forEach { (slots, item) -> gui.setItem(slots, item) }

        // LÃ³gica de items dinÃ¡micos (implementada por cada subclase)
        setupItems(player, gui, config)

        gui.open(player)
    }

    /**
     * MÃ©todo que las subclases implementan para aÃ±adir sus items dinÃ¡micos.
     *
     * @param player El jugador que abre el menÃº.
     * @param gui    La instancia de GUI ya decorada.
     * @param config El FileConfiguration global del menÃº (menus/<nombre>.yml).
     *               Use [getTranslatedString] para obtener textos localizados.
     */
    abstract fun setupItems(player: Player, gui: Gui, config: FileConfiguration)

    /**
     * Obtiene un texto traducido desde messages.yml del jugador.
     * Reemplaza la antigua necesidad de tener un YAML de menÃº por idioma.
     *
     * @param player El jugador cuyo idioma se usarÃ¡.
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
     * Invalida el cachÃ© del menÃº para que los cambios en YAML y messages.yml
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
     * Obtiene (o genera y cachea) los datos decorativos del menÃº para el idioma del jugador.
     * El tÃ­tulo se resuelve desde messages.yml usando [titleMessageKey].
     */
    private fun getBakedData(player: Player): MenuBakedData {
        val lang = plugin.playerDataManager.getLanguage(player.uniqueId)

        return langCache.getOrPut(lang) {
            val config = getGlobalConfig()

            // Resolver el tÃ­tulo desde messages.yml (no desde el YAML del menÃº)
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
                    val item = ItemBuilder.from(material).name(mm.deserialize(display)).asGuiItem()
                    decorList.add(Pair(slots, item))
                }
            }

            MenuBakedData(rawTitle, filas, decorList)
        }
    }

    /**
     * Carga el YAML global del menÃº usando PumpkingLib ConfigManager
     */
    private fun loadGlobalConfig(): FileConfiguration {
        return pumpking.lib.config.ConfigManager.getMenuConfig(menuName)
    }
}

