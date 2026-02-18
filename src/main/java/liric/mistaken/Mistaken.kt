package liric.mistaken

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import liric.mistaken.api.HealthAPI
import liric.mistaken.database.StatsManager
import liric.mistaken.asesinos.AsesinoManager
import liric.mistaken.asesinos.AsesinoTienda
import liric.mistaken.commands.CommandRegistry
import liric.mistaken.config.ConfigManager
import liric.mistaken.config.MessageConfig
import liric.mistaken.data.PlayerDataManager
import liric.mistaken.database.DatabaseManager
import liric.mistaken.database.PlayerStatsManager
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
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*

class Mistaken : JavaPlugin() {

    // --- Singleton & Global Services ---
    companion object {
        lateinit var instance: Mistaken
            private set

        var economy: Economy? = null
            private set

        // Método estático de utilidad para API
        fun getHealthAPI(): HealthAPI? = instance.combatManager
    }

    // --- Core Variables ---
    val mm = MiniMessage.miniMessage()
    lateinit var assassinKey: NamespacedKey
    var craftEngineEnabled: Boolean = false
        private set

    // --- Data Structures ---
    val staffEditMode = mutableSetOf<UUID>()
    val afkPlayers = mutableSetOf<UUID>()
    var lobbyLocation: Location? = null

    // --- Managers (Lateinit: Se inicializan en onEnable) ---
    lateinit var configManager: ConfigManager
    lateinit var messageConfig: MessageConfig
    lateinit var statsManager: StatsManager
    lateinit var playerDataManager: PlayerDataManager
    lateinit var dbManager: DatabaseManager
    lateinit var playerStatsManager: PlayerStatsManager

    // Game Managers
    lateinit var gameManager: GameManager
    lateinit var arenaManager: ArenaManager
    lateinit var generatorManager: GeneratorManager
    lateinit var mapManager: MapManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var ambientManager: AmbientManager
    lateinit var combatManager: CombatManager
    lateinit var discordManager: DiscordManager

    // Roles & Shops
    lateinit var asesinoManager: AsesinoManager
    lateinit var supervivienteManager: SupervivienteManager
    lateinit var asesinoTienda: AsesinoTienda
    lateinit var supervivienteTienda: SupervivienteTienda
    lateinit var shopSelector: ShopSelector

    override fun onLoad() {
        instance = this
        // PacketEvents Load (Antes de iniciar el servidor)
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings.checkForUpdates(false).bStats(true)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        val start = System.currentTimeMillis()

        // 1. Init PacketEvents & Keys
        PacketEvents.getAPI().init()
        assassinKey = NamespacedKey(this, "selected_assassin")

        // 2. Archivos y Configuración
        saveDefaultConfig()
        createRequiredFolders()
        loadLobbyLocation()

        configManager = ConfigManager(this).apply { loadAllConfigs() }
        messageConfig = MessageConfig(this)
        statsManager = StatsManager(this)
        playerDataManager = PlayerDataManager(this)

        // 3. Integraciones Externas (Vault, CraftEngine)
        if (!setupIntegrations()) return

        // 4. Base de Datos (Crítico: Si falla, apagamos)
        if (!setupDatabase()) return

        // 5. Inicialización de Managers (El orden importa)
        discordManager = DiscordManager(this)
        generatorManager = GeneratorManager(this)
        arenaManager = ArenaManager(this)
        mapManager = MapManager(this)

        asesinoManager = AsesinoManager(this)
        supervivienteManager = SupervivienteManager(this)

        asesinoTienda = AsesinoTienda()
        supervivienteTienda = SupervivienteTienda()
        shopSelector = ShopSelector()

        ambientManager = AmbientManager(this)
        combatManager = CombatManager(this)

        // 6. Registro de Servicios (API Pública)
        server.servicesManager.register(HealthAPI::class.java, combatManager, this, ServicePriority.Normal)

        // 7. Core Game Logic
        gameManager = GameManager(this)
        scoreboardManager = ScoreboardManager(this)

        // 8. Placeholders
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            MistakenExpansion(this).register()
            componentLogger.info(mm.deserialize("<green>✔ PAPI Hook registrado."))
        }

        // 9. Eventos y Comandos (NUEVO SISTEMA)
        registerEvents()

        // ¡AQUÍ ESTÁ LA MAGIA! Inyección de comandos Brigadier
        CommandRegistry(this).registerAll()

        // 10. Tareas
        iniciarMotorDeParticulas()
        sendLogo()

        val time = System.currentTimeMillis() - start
        componentLogger.info(mm.deserialize("<gradient:green:aqua>Mistaken habilitado en ${time}ms.</gradient>"))
    }

    override fun onDisable() {
        // Null-safe checks usando Kotlin 'isInitialized'
        if (::dbManager.isInitialized) dbManager.close()
        if (::scoreboardManager.isInitialized) scoreboardManager.removeAll()
        if (::generatorManager.isInitialized) generatorManager.clearGenerators()
        if (::ambientManager.isInitialized) ambientManager.stopAll()
        if (::asesinoManager.isInitialized) asesinoManager.removerTodosLosAsesinos()

        PacketEvents.getAPI().terminate()

        componentLogger.info(mm.deserialize("<red>Mistaken desactivado correctamente."))
    }

    // --- SETUP HELPERS ---

    private fun setupDatabase(): Boolean {
        return try {
            // Cargamos la config de DB
            val dbFile = File(dataFolder, "database.yml")
            if (!dbFile.exists()) saveResource("database.yml", false)
            val dbConfig = YamlConfiguration.loadConfiguration(dbFile)

            // Inicializamos managers
            dbManager = DatabaseManager(this, dbConfig) // Usamos el constructor que espera FileConfiguration
            playerStatsManager = PlayerStatsManager(this)

            componentLogger.info(mm.deserialize("<green>[DB] Conexión HikariCP establecida.</green>"))
            true
        } catch (e: Exception) {
            componentLogger.error(mm.deserialize("<red>FATAL: Error al conectar Base de Datos.</red>"))
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
            false
        }
    }

    private fun setupIntegrations(): Boolean {
        // Economy (Vault)
        val rsp = server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            componentLogger.error(mm.deserialize("<red>Vault no encontrado o sin plugin de economía.</red>"))
            server.pluginManager.disablePlugin(this)
            return false
        }
        economy = rsp.provider

        // CraftEngine
        if (server.pluginManager.isPluginEnabled("CraftEngine")) {
            craftEngineEnabled = true
            componentLogger.info(mm.deserialize("<aqua>CraftEngine detectado.</aqua>"))
        }
        return true
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

    private fun iniciarMotorDeParticulas() {
        // Optimizamos el Scheduler usando el método nativo de Bukkit/Paper
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            if (gameManager.currentState != GameState.INGAME) return@Runnable

            // Iteramos sobre el mapa de asesinos
            asesinoManager.asesinosActivos.forEach { (uuid, asesino) ->
                val p = Bukkit.getPlayer(uuid)

                // Verificaciones rápidas antes de procesar
                if (p != null && p.isOnline && (p.velocity.lengthSquared() > 0.001 || p.isSprinting)) {

                    // 1. Cálculo Asíncrono (Heavy math)
                    asesino.mostrarTrail(p)

                    // 2. Renderizado Síncrono (Solo si es necesario tocar la API de Bukkit que no sea thread-safe)
                    server.scheduler.runTask(this, Runnable {
                        asesino.mostrarTrailFisico(p)
                    })
                }
            }
        }, 0L, 2L)
    }

    // --- UTILS & LOCATION ---

    private fun loadLobbyLocation() {
        val section = config.getConfigurationSection("settings.lobby") ?: return
        val worldName = section.getString("world", "world") ?: "world"
        val world = Bukkit.getWorld(worldName) ?: return

        lobbyLocation = Location(
            world,
            section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
            section.getDouble("yaw").toFloat(), section.getDouble("pitch").toFloat()
        )
    }

    fun setLobbyLocationConfig(loc: Location) {
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
        folders.forEach { File(dataFolder, it).mkdirs() }

        val menus = listOf(
            "tienda_principal.yml",
            "asesinos_tienda.yml",
            "supervivientes_tienda.yml"
        )

        menus.forEach { nombre ->
            val file = File(dataFolder, "menus/$nombre")
            if (!file.exists()) {
                try {
                    saveResource("menus/$nombre", false)
                } catch (e: Exception) {
                    logger.warning("No se pudo exportar $nombre")
                }
            }
        }
    }

    // --- STATE CHECKS ---
    fun isInEditMode(player: Player) = staffEditMode.contains(player.uniqueId)
    fun isAFK(player: Player) = afkPlayers.contains(player.uniqueId)
    fun isIgnored(player: Player) = isInEditMode(player) || isAFK(player)

    private fun sendLogo() {
        val b1 = "<#005f73>"
        val b2 = "<#004488>"
        val b3 = "<#003366>"
        val info = "<#00d4ff>"

        // Paper Logger soporta componentes directos, mucho más limpio
        componentLogger.info(mm.deserialize("""
            <newline>
            $b1<bold>   __  __ _     _        _              </bold>$b1
            $b1<bold>  /  |/  (_)__ / /____ _/ /_____ ___    </bold>$b1
            $b2<bold> / /|_/ / (_-</ __/ _ `/  '_/ -_) _ \   </bold>$b2
            $b3<bold>/_/  /_/_/___/\__/\_,_/_/\_\\__/_//_/   </bold>$b3
            <newline>
              <gray>Autor:</gray> $info Pumpkingz$info
              <gray>Estado:</gray> <green>● Operativo (1.21.4)</green>
              <gray>Motor:</gray> $info Brigadier & PacketEvents$info
            <newline>
        """.trimIndent()))
    }
}
