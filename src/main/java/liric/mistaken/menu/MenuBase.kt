package liric.mistaken.menu

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import liric.mistaken.Mistaken
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * [LIRIC-MISTAKEN 2.0]
 * MenuBase: Estructura fundamental para interfaces optimizadas.
 * Implementa pre-renderizado de decoraciones para ahorrar ciclos de CPU.
 */
abstract class MenuBase(fileName: String) {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    protected var gui: Gui? = null
    protected val config = plugin.configManager.getMenuConfig(fileName)

    protected val titulo: Component = plugin.mm.deserialize(
        config.getString("titulo") ?: "<red>Menú sin Título"
    )
    protected val filas: Int = config.getInt("filas", 3)

    // Optimización: Guardamos los ítems decorativos ya construidos
    private val decorationCache = mutableListOf<Pair<List<Int>, GuiItem>>()

    init {
        // Pre-cargamos las decoraciones al instanciar el menú (una sola vez)
        preLoadDecorations()
    }

    /**
     * Parsea y construye los ítems decorativos del YAML una sola vez.
     */
    private fun preLoadDecorations() {
        val decorSection = config.getConfigurationSection("decoraciones") ?: return

        for (key in decorSection.getKeys(false)) {
            val materialName = decorSection.getString("$key.material", "AIR")!!
            val display = decorSection.getString("$key.nombre", " ")!!
            val slots = decorSection.getIntegerList("$key.slots")

            val material = Material.getMaterial(materialName.uppercase()) ?: run {
                plugin.logger.warning("¡Aguas! Material inváldo en menú: $materialName")
                Material.AIR
            }

            if (material == Material.AIR) continue

            // Construimos el ítem una sola vez y lo guardamos en RAM
            val decorationItem = ItemBuilder.from(material)
                .name(mm.deserialize(display))
                .asGuiItem()

            decorationCache.add(Pair(slots, decorationItem))
        }
    }

    /**
     * Crea la instancia del GUI y aplica las decoraciones desde el caché.
     */
    protected open fun createGuiInstance() {
        this.gui = Gui.gui()
            .title(titulo)
            .rows(filas)
            .disableAllInteractions()
            .create()

        // Aplicar decoraciones desde el caché (operación ultra-rápida)
        decorationCache.forEach { (slots, item) ->
            gui?.setItem(slots, item)
        }
    }

    /**
     * Abre el menú al jugador procesando los ítems dinámicos.
     */
    open fun abrir(player: Player) {
        // Si el GUI es nulo (o queremos que sea por jugador), lo instanciamos
        if (this.gui == null) {
            createGuiInstance()
        }

        // Configurar ítems específicos (como cabezas de jugadores o stats)
        setupItems(player)

        gui?.open(player)
    }

    /**
     * Método abstracto para que los hijos añadan su lógica (items de tienda, etc).
     */
    abstract fun setupItems(player: Player)

    /**
     * Permite forzar la reconstrucción del GUI (por ejemplo, en un reload).
     */
    fun reloadGui() {
        this.gui = null
        decorationCache.clear()
        preLoadDecorations()
    }
}
