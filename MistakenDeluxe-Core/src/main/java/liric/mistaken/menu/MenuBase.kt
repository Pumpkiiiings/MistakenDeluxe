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
import net.kyori.adventure.text.Component
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.ConfigManager
import liric.mistaken.config.engine.core.MessageService


abstract class MenuBase(
    /** Nombre del archivo YAML sin extensi�n, ej: "killers_shop" */
    private val menuName: String
) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    /**
     * Parsea un texto con MiniMessage desactivando la cursiva (italic) por defecto,
     * ya que Minecraft la aplica forzosamente al lore de los items.
     */
    protected fun parseSafe(text: String): Component {
        return ColorTranslator.translate("<!italic>$text")
    }

    
    
    protected open val titleMessageKey: String get() = "menus.$menuName.titulo"

    
    protected open val titleFallback: String get() = "<red>Menu: $menuName"

    
    
    private val langCache = ConcurrentHashMap<String, MenuBakedData>()

    
    private var globalConfig: FileConfiguration? = null

    
    
    

    /**
     * Obtiene la configuraci�n global del men� (layout, slots, decoraciones).
     * Carga lazy desde disco: menus/<menuName>.yml dentro del dataFolder del plugin.
     */
    fun getGlobalConfig(): FileConfiguration {
        return globalConfig ?: loadGlobalConfig().also { globalConfig = it }
    }

    /**
     * Abre el men� al player resolviendo su idioma autom�ticamente.
     */
    open fun abrir(player: Player) {
        val baked = getBakedData(player)
        val config = getGlobalConfig()

        val gui = Gui.gui()
            .title(ColorTranslator.translate(baked.resolvedTitle))
            .rows(baked.filas)
            .disableAllInteractions()
            .create()

        
        baked.decorations.forEach { (slots, item) -> gui.setItem(slots, item) }

        
        setupItems(player, gui, config)

        gui.open(player)
    }

    /**
     * M�todo que las subclasses implementan para a�adir sus items din�micos.
     *
     * @param player El player que abre el men�.
     * @param gui    La instancia de GUI ya decorada.
     * @param config El FileConfiguration global del men� (menus/<nombre>.yml).
     *               Use [getTranslatedString] para obtener textos localizados.
     */
    abstract fun setupItems(player: Player, gui: Gui, config: FileConfiguration)

    /**
     * Obtiene un texto traducido desde messages.yml del player.
     * Reemplaza la antigua necesidad de tener un YAML de men� por idioma.
     *
     * @param player El player cuyo idioma se usar�.
     * @param path   La ruta en messages.yml, ej: "menus.tienda_principal.items.killers.nombre"
     * @param def    Valor por defecto si no se encuentra la clave.
     */
    fun getTranslatedString(player: Player, path: String, def: String = "<red>Missing: $path"): String {
        return MessageService.getRawString(player, path, def, "messages")
    }

    /**
     * Obtiene una lista de strings traducidos desde messages.yml del player.
     */
    fun getTranslatedList(player: Player, path: String): List<String> {
        return MessageService.getRawStringList(player, path, "messages")
    }


    /**
     * Invalida el cach� del men� para que los cambios en YAML y messages.yml
     * se reflejen sin reiniciar el servidor (utilizado en /mistaken reload).
     */
    fun reload() {
        langCache.clear()
        globalConfig = null
    }

    
    
    

    private data class MenuBakedData(
        val resolvedTitle: String,
        val filas: Int,
        val decorations: List<Pair<List<Int>, GuiItem>>
    )

    /**
     * Obtiene (o genera y cachea) los datos decorativos del men� para el idioma del player.
     * El t�tulo se resuelve desde messages.yml usando [titleMessageKey].
     */
    private fun getBakedData(player: Player): MenuBakedData {
        val lang = plugin.playerDataManager.getLanguage(player.uniqueId)

        return langCache.getOrPut(lang) {
            val config = getGlobalConfig()

            
            val rawTitle = MessageService.getRawString(player, titleMessageKey, titleFallback, "messages")

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
     * Carga el YAML global del men� usando MistakenLib ConfigManager
     */
    private fun loadGlobalConfig(): FileConfiguration {
        return ConfigManager.getMenuConfig(menuName)
    }
}
