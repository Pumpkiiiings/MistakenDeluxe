package liric.mistaken.menu.menus

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import liric.mistaken.menu.MenuBase
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag

/**
 * [LIRIC-MISTAKEN 2.0]
 * ShopSelector: Menú principal de selección de tiendas.
 * Optimización: Interfaz pre-renderizada (Singleton) para consumo nulo de recursos.
 */
class ShopSelector : MenuBase("tienda_principal") {

    private var clickSound: Sound = Sound.UI_BUTTON_CLICK
    private var isInitialized = false

    init {
        // Inicializamos el GUI global inmediatamente al crear la instancia
        initGlobalGui()
    }

    private fun initGlobalGui() {
        if (isInitialized) return

        // 1. Cargar sonido de forma segura
        val soundName = config.getString("ajustes.sonido-click", "UI_BUTTON_CLICK") ?: "UI_BUTTON_CLICK"
        clickSound = try {
            Sound.valueOf(soundName.uppercase())
        } catch (e: IllegalArgumentException) {
            Sound.UI_BUTTON_CLICK
        }

        // 2. Crear la instancia base del GUI (Usando la propiedad de MenuBase)
        this.gui = Gui.gui()
            .title(titulo)
            .rows(filas)
            .disableAllInteractions()
            .create()

        // 3. ITEM: ASESINOS
        val pathA = "items.asesinos"
        val itemAsesinos = ItemBuilder.from(
            Material.getMaterial(config.getString("$pathA.material", "NETHERITE_SWORD")?.uppercase() ?: "NETHERITE_SWORD") ?: Material.NETHERITE_SWORD
        ).apply {
            name(mm.deserialize(config.getString("$pathA.nombre", "<red>Asesinos")!!))
            lore(config.getStringList("$pathA.lore").map { mm.deserialize(it) })
            flags(*ItemFlag.entries.toTypedArray()) // Ocultar todos los atributos (1.21.4 way)
        }.asGuiItem { event ->
            val p = event.whoClicked as? Player ?: return@asGuiItem
            p.playSound(p.location, clickSound, 1f, 1f)

            // Acceso directo a la tienda de asesinos del plugin principal
            plugin.asesinoTienda.abrir(p)
        }

        // 4. ITEM: SUPERVIVIENTES
        val pathS = "items.supervivientes"
        val itemSurvivors = ItemBuilder.from(
            Material.getMaterial(config.getString("$pathS.material", "IRON_CHESTPLATE")?.uppercase() ?: "IRON_CHESTPLATE") ?: Material.IRON_CHESTPLATE
        ).apply {
            name(mm.deserialize(config.getString("$pathS.nombre", "<green>Supervivientes")!!))
            lore(config.getStringList("$pathS.lore").map { mm.deserialize(it) })
            flags(*ItemFlag.entries.toTypedArray())
        }.asGuiItem { event ->
            val p = event.whoClicked as? Player ?: return@asGuiItem
            p.playSound(p.location, clickSound, 1f, 1f)

            // Acceso directo a la tienda de supervivientes del plugin principal
            plugin.supervivienteTienda.abrir(p)
        }

        // 5. Colocar los items en sus slots configurados
        gui?.let {
            it.setItem(config.getInt("$pathA.slot", 11), itemAsesinos)
            it.setItem(config.getInt("$pathS.slot", 15), itemSurvivors)
        }

        isInitialized = true
    }

    /**
     * No necesitamos lógica dinámica por jugador en este menú,
     * ya que es un selector estático.
     */
    override fun setupItems(player: Player) {
        // Nada que procesar aquí.
    }

    /**
     * Sobrescribimos abrir para usar la instancia global pre-cargada.
     */
    override fun abrir(player: Player) {
        gui?.open(player)
    }

    /**
     * Permite recargar el menú si se cambia el archivo YAML.
     */
    fun reload() {
        isInitialized = false
        initGlobalGui()
    }
}
