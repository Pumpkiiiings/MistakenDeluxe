package liric.mistaken

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import kotlinx.coroutines.*
import liric.mistaken.api.HealthAPI
import liric.mistaken.asesinos.AsesinoManager
import liric.mistaken.asesinos.AsesinoTienda
import liric.mistaken.commands.*
import liric.mistaken.database.DatabaseManager
import liric.mistaken.config.ConfigManager
import liric.mistaken.config.MessageConfig
import liric.mistaken.data.PlayerDataManager
import liric.mistaken.database.*
import liric.mistaken.discord.DiscordManager
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.managers.*
import liric.mistaken.listeners.*
import liric.mistaken.listeners.asesinos.AsesinoGeneralListener
import liric.mistaken.listeners.asesinos.AsesinoHabilidadListener
import liric.mistaken.listeners.supervivientes.SupervivienteHabilidadListener
import liric.mistaken.menu.menus.ShopSelector
import liric.mistaken.supervivientes.SupervivienteManager
import liric.mistaken.supervivientes.SupervivienteTienda
import liric.mistaken.utils.MistakenExpansion
import net.kyori.adventure.text.minimessage.MiniMessage
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

class Mistaken : JavaPlugin() {

    // Managers con inicialización tardía para ahorrar memoria en el booteo
    lateinit var configManager: ConfigManager
    lateinit var messageConfig: MessageConfig
    lateinit var playerDataManager: PlayerDataManager
    lateinit var arenaManager: ArenaManager
    lateinit var gameManager: GameManager
    lateinit var generatorManager: GeneratorManager
    lateinit var mapManager: MapManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var asesinoManager: AsesinoManager
    lateinit var supervivienteManager: SupervivienteManager
    lateinit var supervivienteTienda: SupervivienteTienda
    lateinit var ambientManager: AmbientManager
    lateinit var combatManager: CombatManager
    lateinit var databaseManager: DatabaseManager
    lateinit var discordManager: DiscordManager
    lateinit var statsManager: StatsManager
    lateinit var playerStatsManager: PlayerStatsManager
    lateinit var shopSelector: ShopSelector
    lateinit var asesinoTienda: AsesinoTienda

    lateinit var assassinKey: NamespacedKey
    private var dbConfig: FileConfiguration? = null
    var lobbyLocation: Location? = null
    var isCraftEngineEnabled = false = false

    // Estado de jugadores
    val staffEditMode = mutableSetOf<UUID>()
    val afkPlayers = mutableSetOf<UUID>()

    // Coroutine Scope para tareas asíncronas optimizadas
    private val pluginScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        lateinit var instance: Mistaken
            private set
        var economy: Economy? = null
            private set
        val mm = MiniMessage.miniMessage()

        @JvmStatic
        fun getHealthAPI(): HealthAPI = instance.combatManager
    }

    override fun onLoad() {
        instance = this
        // PacketEvents 2.0+ (Spigot Builder)
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings.checkForUpdates(false).debug(false)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        PacketEvents.getAPI().init()
        assassinKey = NamespacedKey(this, "selected_assassin")

        saveDefaultConfig()
        createRequiredFolders()
        loadDatabaseConfig()
        loadLobbyLocation()

        // Inicialización de lógica base
        configManager = ConfigManager(this).also { it.loadAllConfigs() }
        messageConfig = MessageConfig(this)
        playerDataManager = PlayerDataManager(this)

        if (!setupIntegrations()) return
        setupDatabase()

        // Inicialización de Managers de Juego
        generatorManager = GeneratorManager(this)
        arenaManager = ArenaManager(this)
        mapManager = MapManager()
        discordManager = DiscordManager(this)
        asesinoManager = AsesinoManager(this)
        supervivienteManager = SupervivienteManager(this)
        asesinoTienda = AsesinoTienda()
        supervivienteTienda = SupervivienteTienda()
        shopSelector = ShopSelector()
        ambientManager = AmbientManager(this)
        combatManager = CombatManager(this)

        // Registrar API de Salud en los servicios de Bukkit
        server.servicesManager.register(HealthAPI::class.java, combatManager, this, ServicePriority.Normal)

        gameManager = GameManager(this)
        scoreboardManager = ScoreboardManager(this)

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            MistakenExpansion(this).register()
        }

        registerEvents()
        registerCommands()
        iniciarMotorDeParticulasAsync()
        sendLogo()
    }

    private fun setupDatabase() {
        try {
            databaseManager = DatabaseManager(this, dbConfig!!)
            statsManager = StatsManager(this)
            playerStatsManager = PlayerStatsManager(this)
            server.consoleSender.sendMessage(mm.deserialize("<green>[Mistaken] Database y Stats vinculados correctamente.</green>"))
        } catch (e: Exception) {
            server.consoleSender.sendMessage(mm.deserialize("<red>[Mistaken] FATAL ERROR: Fallo en DB. Plugin deshabilitado.</red>"))
            server.pluginManager.disablePlugin(this)
        }
    }

    private fun setupIntegrations(): Boolean {
        val vault = server.pluginManager.getPlugin("Vault")
        if (vault == null || !setupEconomy()) {
            server.consoleSender.sendMessage(mm.deserialize("<red>[Mistaken] Falta Vault o Plugin de Economía.</red>"));
            server.pluginManager.disablePlugin(this)
            return false
        }
        isCraftEngineEnabled = server.pluginManager.isPluginEnabled("CraftEngine")
        return true
    }

    private fun setupEconomy(): Boolean {
        val rsp = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return economy != null
    }

    private fun registerEvents() {
        val pm = server.pluginManager
        pm.registerEvents(PlayerListener(this), this)
        pm.registerEvents(PlayerQuitListener(this), this)
        pm.registerEvents(GameListener(this), this)
        pm.registerEvents(StaminaListener(this), this)
        pm.registerEvents(AsesinoHabilidadListener(this), this)
        pm.registerEvents(AsesinoGeneralListener(this), this)
        pm.registerEvents(AntiBlockListener(this), this)
        pm.registerEvents(SupervivienteHabilidadListener(this), this)
    }

    /**
     * Registro de comandos usando la nueva API Lifecycle de Paper (1.21.4+)
     * Esto usa Brigadier nativo, mucho más rápido que el plugin.yml
     */
    private fun registerCommands() {
        val manager = this.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()

            // Aquí puedes instanciar tus comandos usando Brigadier o BasicCommand
            // Por brevedad, asumo que tus clases Command ya están adaptadas o usan BasicCommand
            registrar.register("arena", "Comando de arenas", ArenaCommand(this))
            registrar.register("vote", "Votar por mapa", VoteCommand(this))
            registrar.register("mistaken", "Comando principal", MistakenCommand(this))
            registrar.register("setlobby", "Establecer lobby", SetLobbyCommand(this))
        }
    }

    /**
     * Motor de partículas usando Coroutines.
     * No bloquea el hilo principal y tiene un consumo casi nulo.
     */
    private fun iniciarMotorDeParticulasAsync() {
        pluginScope.launch {
            while (isActive) {
                if (gameManager.getCurrentState() == GameState.INGAME) {
                    asesinoManager.asesinosActivos.forEach { (uuid, asesino) ->
                        val player = Bukkit.getPlayer(uuid)
                        if (player != null && player.isOnline) {
                            // Solo procesar si se mueve o corre
                            if (player.velocity.lengthSquared() > 0.001 || player.isSprinting) {
                                // Partículas en hilo Async (Dispatchers.Default)
                                asesino.mostrarTrail(player)

                                // Efectos físicos deben ir al Main Thread
                                withContext(Dispatchers.Main) {
                                    asesino.mostrarTrailFisico(player)
                                }
                            }
                        }
                    }
                }
                delay(100) // Equivalente a 2 ticks
            }
        }
    }

    override fun onDisable() {
        pluginScope.cancel() // Detener todas las tareas asíncronas
        if (::databaseManager.isInitialized) databaseManager.close()
        if (::scoreboardManager.isInitialized) scoreboardManager.removeAll()
        if (::generatorManager.isInitialized) generatorManager.clearGenerators()
        if (::ambientManager.isInitialized) ambientManager.stopAll()
        if (::asesinoManager.isInitialized) asesinoManager.removerTodosLosAsesinos()

        PacketEvents.getAPI().terminate()
    }

    // Helpers de estado
    fun isInEditMode(player: Player) = staffEditMode.contains(player.uniqueId)
    fun isAFK(player: Player) = afkPlayers.contains(player.uniqueId)
    fun isIgnored(player: Player) = isInEditMode(player) || isAFK(player)

    // Configuración de Lobby
    fun loadLobbyLocation() {
        val section = config.getConfigurationSection("settings.lobby") ?: return
        val world = Bukkit.getWorld(section.getString("world") ?: "world") ?: return
        lobbyLocation = Location(
            world,
            section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
            section.getDouble("yaw").toFloat(), section.getDouble("pitch").toFloat()
        )
    }

    fun setLobbyLocation(loc: Location) {
        this.lobbyLocation = loc
        val section = config.createSection("settings.lobby")
        section.set("world", loc.world.name)
        section.set("x", loc.x)
        section.set("y", loc.y)
        section.set("z", loc.z)
        section.set("yaw", loc.yaw)
        section.set("pitch", loc.pitch)
        saveConfig()
    }

    private fun createRequiredFolders() {
        val folders = listOf("lang", "menus")
        folders.forEach { name ->
            val folder = File(dataFolder, name)
            if (!folder.exists()) folder.mkdirs()
        }

        val menus = listOf("tienda_principal.yml", "asesinos_tienda.yml", "supervivientes_tienda.yml")
        menus.forEach { name ->
            val file = File(dataFolder, "menus/$name")
            if (!file.exists()) saveResource("menus/$name", false)
        }
    }

    private fun loadDatabaseConfig() {
        val dbFile = File(dataFolder, "database.yml")
        if (!dbFile.exists()) saveResource("database.yml", false)
        dbConfig = YamlConfiguration.loadConfiguration(dbFile)
    }

    private fun sendLogo() {
        val blue1 = "#005f73"
        val info = "#00d4ff"

        val logo = """
            
               <blue1><bold>   __  __ _     _        _              </bold></blue1>
              <blue1><bold>  /  |/  (_)__ / /____ _/ /_____ ___    </bold></blue1>
            <#004488><bold> / /|_/ / (_-</ __/ _ `/  '_/ -_) _ \   </bold></#004488>
            <#003366><bold>/_/  /_/_/___/\\__/\\_,_/_/\\_\\\\__/_//_/   </bold></#003366>
            
              <gray>Autor:</gray> <info>Liric Development</info>
              <gray>Estado:</gray> <green>● Optimizado (Kotlin)</green>
              <gray>Versión:</gray> <white>1.21.4 (Paper Native)</white>
        """.trimIndent()
            .replace("blue1", blue1)
            .replace("info", info)

        server.consoleSender.sendMessage(mm.deserialize(logo))
    }
}
