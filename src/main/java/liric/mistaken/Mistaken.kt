package liric.mistaken

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import fr.skytasul.glowingentities.GlowingEntities
import liric.mistaken.api.HealthAPI
import liric.mistaken.database.StatsManager
import liric.mistaken.game.managers.MusicManager
import liric.mistaken.asesinos.AsesinoManager
import liric.mistaken.asesinos.AsesinoTienda
import liric.mistaken.commands.CommandRegistry
import liric.mistaken.config.ConfigManager
import liric.mistaken.config.MessageConfig
import liric.mistaken.data.PlayerDataManager
import liric.mistaken.asesinos.Asesino
import liric.mistaken.database.DatabaseManager
import liric.mistaken.database.PlayerStatsManager
import liric.mistaken.discord.DiscordManager
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.GameManager // El nuevo GameManager
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
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class Mistaken : JavaPlugin() {

    companion object {
        @JvmStatic
        lateinit var instance: Mistaken
            private set

        @JvmStatic
        var economy: Economy? = null
            internal set

        @JvmStatic
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
    var isReady = false
    val ignoredTestPlayers = mutableSetOf<UUID>()

    // --- Managers ---
    lateinit var configManager: ConfigManager
    lateinit var messageConfig: MessageConfig
    lateinit var statsManager: StatsManager
    lateinit var playerDataManager: PlayerDataManager
    lateinit var databaseManager: DatabaseManager
    lateinit var playerStatsManager: PlayerStatsManager

    // Game Managers
    lateinit var antiBlockListener: AntiBlockListener
    lateinit var voteManager: VoteManager // Añadido para el nuevo GameManager
    lateinit var gameManager: GameManager
    lateinit var arenaManager: ArenaManager
    lateinit var musicManager: MusicManager
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
    lateinit var glowingAPI: GlowingEntities

    override fun onLoad() {
        instance = this
        // PacketEvents Load (Antes de iniciar el servidor)
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings.checkForUpdates(false).bStats(true)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        val start = System.currentTimeMillis()
        instance = this

        // 1. PacketEvents & Keys
        PacketEvents.getAPI().init()
        assassinKey = NamespacedKey(this, "selected_assassin")

        // 2. Configuración y Carpetas
        saveDefaultConfig()
        createRequiredFolders()
        loadLobbyLocation()

        messageConfig = MessageConfig(this)
        configManager = ConfigManager(this).apply { loadAllConfigs() }

        // 3. Hooks (Vault, etc)
        if (!setupIntegrations()) return

        // 4. Base de Datos
        if (!setupDatabase()) return

        // 5. Datos Base
        playerStatsManager = PlayerStatsManager(this)
        statsManager = StatsManager(this)
        playerDataManager = PlayerDataManager(this)

        // 6. El Corazón de los Managers
        glowingAPI = GlowingEntities(this)
        combatManager = CombatManager(this)
        antiBlockListener = AntiBlockListener(this)
        voteManager = VoteManager() // Inicializado antes del GameManager

        // ¡Aquí entra en acción el nuevo GameManager!
        gameManager = GameManager(this)

        mapManager = MapManager(this)
        arenaManager = ArenaManager(this)
        asesinoManager = AsesinoManager(this)
        supervivienteManager = SupervivienteManager(this)
        discordManager = DiscordManager(this)
        generatorManager = GeneratorManager(this)

        musicManager = MusicManager(this)
        liric.mistaken.api.MistakenAPI.init(this)

        // UI
        asesinoTienda = AsesinoTienda()
        supervivienteTienda = SupervivienteTienda()
        shopSelector = ShopSelector()

        // Bucles de fondo
        ambientManager = AmbientManager(this)
        scoreboardManager = ScoreboardManager(this)

        // 7. Servicios API
        server.servicesManager.register(HealthAPI::class.java, combatManager, this, ServicePriority.Normal)

        // 8. Placeholders
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            MistakenExpansion(this).register()
        }

        // 9. Eventos y Comandos
        registerEvents()
        CommandRegistry(this).registerAll()

        // 10. 🏁 TAREAS FINALES Y ACTIVACIÓN DEL LOBBY
        isReady = true

        server.onlinePlayers.forEach { player ->
            combatManager.resetHealth(player)
            musicManager.syncPlayer(player)
        }

        lobbyLocation?.world?.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)

        iniciarMotorDeParticulas()
        sendLogo()

        val time = System.currentTimeMillis() - start
        componentLogger.info(mm.deserialize("<gradient:green:aqua>Mistaken v${pluginMeta.version} habilitado en ${time}ms (Full Lobby Sync)</gradient>"))
    }

    override fun onDisable() {
        isReady = false

        if (::ambientManager.isInitialized) runCatching { ambientManager.stopAll() }
        if (::musicManager.isInitialized) musicManager.shutdown()
        if (::generatorManager.isInitialized) runCatching { generatorManager.clearGenerators() }
        if (::scoreboardManager.isInitialized) runCatching { scoreboardManager.removeAll() }
        if (::asesinoManager.isInitialized) runCatching { asesinoManager.shutdown() }
        if (::supervivienteManager.isInitialized) runCatching { supervivienteManager.shutdown() }
        if (::glowingAPI.isInitialized) runCatching { glowingAPI.disable() }
        if (::databaseManager.isInitialized) runCatching { databaseManager.close() }

        PacketEvents.getAPI().terminate()

        componentLogger.info(mm.deserialize("<newline><red>║ <bold>MISTAKEN</bold> ha sido desactivado con éxito.</red><newline>"))
    }

    // --- SETUP HELPERS ---

    private fun setupDatabase(): Boolean {
        return try {
            val dbFile = File(dataFolder, "database.yml")
            if (!dbFile.exists()) saveResource("database.yml", false)
            val dbConfig = YamlConfiguration.loadConfiguration(dbFile)

            databaseManager = DatabaseManager(this, dbConfig)
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
        val rsp: RegisteredServiceProvider<Economy>? = server.servicesManager.getRegistration(Economy::class.java)

        if (rsp == null) {
            componentLogger.error(mm.deserialize("<red><b>[!]</b> Vault no encontró ningún plugin de economía compatible.</red>"))
            server.pluginManager.disablePlugin(this)
            return false
        }

        economy = rsp.provider

        if (economy == null) {
            componentLogger.error(mm.deserialize("<red><b>[!]</b> El proveedor de economía es nulo. Revisa Vault.</red>"))
            server.pluginManager.disablePlugin(this)
            return false
        }

        componentLogger.info(mm.deserialize("<green>✔ Integración con Vault establecida correctamente.</green>"))

        craftEngineEnabled = server.pluginManager.isPluginEnabled("CraftEngine")
        if (craftEngineEnabled) {
            componentLogger.info(mm.deserialize("<aqua>✔ CraftEngine detectado y vinculado.</aqua>"))
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
        pm.registerEvents(antiBlockListener, this)
        pm.registerEvents(SupervivienteHabilidadListener(this), this)
    }

    // 🔥 MOTOR DE PARTÍCULAS ACTUALIZADO PARA PAPER SCHEDULER
    private fun iniciarMotorDeParticulas() {
        // Usamos el AsyncScheduler nativo de Paper (Apto para Folia)
        // 100 milisegundos = 2 Ticks
        server.asyncScheduler.runAtFixedRate(this, { _ ->
            if (gameManager.currentState != GameState.INGAME) return@runAtFixedRate

            val targetsForPhysicalTrail = mutableListOf<Pair<Player, Asesino>>()

            asesinoManager.asesinosActivos.forEach { (uuid, asesino) ->
                val p = server.getPlayer(uuid) ?: return@forEach

                if (p.isOnline && (p.velocity.lengthSquared() > 0.001 || p.isSprinting)) {
                    // El envío de paquetes es seguro desde un hilo asíncrono
                    asesino.mostrarTrail(p)
                    targetsForPhysicalTrail.add(p to asesino)
                }
            }

            if (targetsForPhysicalTrail.isNotEmpty()) {
                // Volvemos al GlobalRegionScheduler solo si necesitamos aplicar efectos físicos al mundo
                server.globalRegionScheduler.run(this) { _ ->
                    for (pair in targetsForPhysicalTrail) {
                        val player = pair.first
                        val asesino = pair.second

                        if (player.isOnline) {
                            asesino.mostrarTrailFisico(player)
                        }
                    }
                }
            }
        }, 0L, 100L, TimeUnit.MILLISECONDS)
    }

    // --- UTILS & LOCATION ---

    private fun loadLobbyLocation() {
        val section = config.getConfigurationSection("settings.lobby") ?: return
        val worldName = section.getString("world", "world") ?: "world"
        val world = server.getWorld(worldName) ?: return

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
        if (!dataFolder.exists()) dataFolder.mkdirs()

        val langFolder = File(dataFolder, "langs")
        if (!langFolder.exists()) langFolder.mkdirs()

        val baseFiles = listOf("database.yml", "music.yml")
        baseFiles.forEach { fileName ->
            val file = File(dataFolder, fileName)
            if (!file.exists()) {
                runCatching {
                    saveResource(fileName, false)
                }.onFailure {
                    componentLogger.warn(mm.deserialize("<yellow>⚠️ No se pudo exportar el archivo base: $fileName</yellow>"))
                }
            }
        }
        componentLogger.info(mm.deserialize("<gray>[System] Estructura base verificada.</gray>"))
    }

    // --- STATE CHECKS ---
    fun isInEditMode(player: Player) = staffEditMode.contains(player.uniqueId)
    fun isAFK(player: Player) = afkPlayers.contains(player.uniqueId)
    fun isIgnored(player: Player): Boolean {
        val uuid = player.uniqueId
        return uuid in staffEditMode || uuid in afkPlayers || uuid in ignoredTestPlayers
    }

    private fun sendLogo() {
        val b1 = "<#005f73>"
        val b2 = "<#004488>"
        val b3 = "<#003366>"
        val b4 = "<#005f73>"
        val b5 = "<#004488>"
        val info = "<#00d4ff>"

        componentLogger.info(mm.deserialize("""
            <newline>
             $b1<bold> </bold>$b1
             $b1<bold> ███▄ ▄███▓ ██▓  ██████ ▄▄▄█████▓ ▄▄▄       ██ ▄█▀▓█████  ███▄    █ </bold>$b1
             $b1<bold>▓██▒▀█▀ ██▒▓██▒▒██    ▒ ▓  ██▒ ▓▒▒████▄     ██▄█▒ ▓█   ▀  ██ ▀█   █ </bold>$b1
             $b2<bold>▓██    ▓██░▒██▒░ ▓██▄   ▒ ▓██░ ▒░▒██  ▀█▄  ▓███▄░ ▒███   ▓██  ▀█ ██▒</bold>$b2
             $b3<bold>▒██    ▒██ ░██░  ▒   ██▒░ ▓██▓ ░ ░██▄▄▄▄██ ▓██ █▄ ▒▓█  ▄ ▓██▒  ▐▌██▒</bold>$b3
             $b4<bold>▒██▒   ░██▒░██░▒██████▒▒  ▒██▒ ░  ▓█   ▓██▒▒██▒ █▄░▒████▒▒██░   ▓██░</bold>$b4
             $b5<bold>░ ▒░   ░  ░░▓  ▒ ▒▓▒ ▒ ░  ▒ ░░    ▒▒   ▓▒█░▒ ▒▒ ▓▒░░ ▒░ ░░ ▒░   ▒ ▒ </bold>$b5
             $b4<bold>░  ░      ░ ▒ ░░ ░▒  ░ ░    ░      ▒   ▒▒ ░░ ░▒ ▒░ ░ ░  ░░ ░░   ░ ▒░</bold>$b4
             $b5<bold>░      ░    ▒ ░░  ░  ░    ░        ░   ▒   ░ ░░ ░    ░      ░   ░ ░  </bold>$b5
             $b5<bold>       ░    ░        ░                 ░  ░░  ░      ░  ░         ░ </bold>$b5
            <newline>
               <white>Autor:</white> $info Pumpkingz$info
               <white>Estado:</white> <green>● Operativo</green>
               <white>Addons Detectados:</white> $info MistakenGenerators, PumpkinEffects, CraftEngine $info
            <newline>
        """.trimIndent()))
    }
}
