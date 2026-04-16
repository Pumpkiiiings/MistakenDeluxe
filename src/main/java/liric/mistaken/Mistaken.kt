package liric.mistaken

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import fr.skytasul.glowingentities.GlowingEntities
import liric.mistaken.api.HealthAPI
import liric.mistaken.roles.asesinos.AsesinoManager
import liric.mistaken.menu.menus.AsesinoTienda
import liric.mistaken.config.ConfigManager
import liric.mistaken.config.MessageConfig
import liric.mistaken.data.PlayerDataManager
import liric.mistaken.data.DatabaseManager
import liric.mistaken.data.stats.PlayerStatsManager
import liric.mistaken.data.stats.StatsManager
import liric.mistaken.utils.hooks.DiscordHook
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.managers.*
import liric.mistaken.game.managers.audio.AmbientManager
import liric.mistaken.game.managers.audio.MusicManager
import liric.mistaken.game.managers.engine.ArenaManager
import liric.mistaken.game.managers.engine.IsolationManager
import liric.mistaken.game.managers.engine.MapManager
import liric.mistaken.game.managers.engine.SessionManager
import liric.mistaken.game.managers.gameplay.CombatManager
import liric.mistaken.game.managers.gameplay.GeneratorManager
import liric.mistaken.game.managers.gameplay.SpectatorManager
import liric.mistaken.game.managers.gameplay.VoteManager
import liric.mistaken.game.managers.visual.CinematicManager
import liric.mistaken.game.managers.visual.ScoreboardManager
import liric.mistaken.listeners.*
import liric.mistaken.listeners.asesinos.AsesinoGeneralListener
import liric.mistaken.listeners.asesinos.AsesinoHabilidadListener
import liric.mistaken.listeners.supervivientes.SupervivienteHabilidadListener
import liric.mistaken.menu.menus.ShopSelector
import liric.mistaken.roles.supervivientes.SupervivienteManager
import liric.mistaken.menu.menus.SupervivienteTienda
import liric.mistaken.utils.hooks.MistakenExpansion
import net.kyori.adventure.text.minimessage.MiniMessage
import net.milkbowl.vault.economy.Economy
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * [LIRIC-MISTAKEN 2.0]
 * Optimizado para Paper 1.21.4+ y Folia.
 */
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

    val mm = MiniMessage.miniMessage()
    val assassinKey by lazy { NamespacedKey(this, "selected_assassin") }

    var craftEngineEnabled: Boolean = false
        private set
    var serverMode: String = "GAME_SERVER"
        private set
    val isGameServer: Boolean get() = serverMode != "NETWORK_LOBBY"

    // Estados de memoria ligera
    val staffEditMode = mutableSetOf<UUID>()
    val afkPlayers = mutableSetOf<UUID>()
    val ignoredTestPlayers = mutableSetOf<UUID>()
    var lobbyLocation: Location? = null
    var isReady = false

    // ==========================================
    // MANAGERS GLOBALES (Siempre cargan)
    // ==========================================
    lateinit var configManager: ConfigManager
    lateinit var messageConfig: MessageConfig
    lateinit var databaseManager: DatabaseManager
    lateinit var statsManager: StatsManager
    lateinit var playerStatsManager: PlayerStatsManager
    lateinit var playerDataManager: PlayerDataManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var discordHook: DiscordHook
    lateinit var musicManager: MusicManager

    // ==========================================
    // MANAGERS DE JUEGO (Cargan SÓLO si es Game/Arena) -> NULABLES
    // ==========================================
    var sessionManager: SessionManager? = null
    var isolationManager: IsolationManager? = null
    var voteManager: VoteManager? = null
    var arenaManager: ArenaManager? = null
    var generatorManager: GeneratorManager? = null
    var mapManager: MapManager? = null
    var ambientManager: AmbientManager? = null
    var combatManager: CombatManager? = null
    var cinematicManager: CinematicManager? = null
    var spectatorManager: SpectatorManager? = null
    var asesinoManager: AsesinoManager? = null
    var supervivienteManager: SupervivienteManager? = null
    var asesinoTienda: AsesinoTienda? = null
    var supervivienteTienda: SupervivienteTienda? = null
    var shopSelector: ShopSelector? = null
    var glowingAPI: GlowingEntities? = null
    var antiBlockListener: AntiBlockListener? = null

    override fun onLoad() {
        instance = this
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings.checkForUpdates(false).bStats(true)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        val start = System.currentTimeMillis()
        PacketEvents.getAPI().init()
        server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")

        saveDefaultConfig()
        createRequiredFolders()

        // 1. Carga Core
        configManager = ConfigManager(this).apply { loadAllConfigs() }
        messageConfig = MessageConfig(this)
        serverMode = config.getString("server-mode", "GAME_SERVER")?.uppercase() ?: "GAME_SERVER"

        componentLogger.info(mm.deserialize("<yellow>[Red] Modo del servidor: <bold>$serverMode</bold></yellow>"))
        loadLobbyLocation()

        setupIntegrations()
        if (!setupDatabase()) {
            server.pluginManager.disablePlugin(this)
            return // Si no hay BD, apagamos para evitar corrupción
        }

        // Managers Core (DB, Stats, Scoreboard, Discord)
        playerStatsManager = PlayerStatsManager(this)
        statsManager = StatsManager(this)
        playerDataManager = PlayerDataManager(this)
        scoreboardManager = ScoreboardManager(this)
        discordHook = DiscordHook(this)
        musicManager = MusicManager(this)

        // Eventos Globales (Conexión, Desconexión)
        server.pluginManager.registerEvents(PlayerListener(this), this)
        server.pluginManager.registerEvents(PlayerQuitListener(this), this)

        // 2. Carga Selectiva (Solo si no es LOBBY)
        if (isGameServer) {
            iniciarMotorDeJuego()
        } else {
            componentLogger.info(mm.deserialize("<gray>Modo LOBBY detectado: Sistemas de partida desactivados para ahorrar memoria.</gray>"))
        }

        liric.mistaken.api.MistakenAPI.init(this)
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            MistakenExpansion(this).register()
        }

        isReady = true
        handleHotReload()
        sendLogo(System.currentTimeMillis() - start)
    }

    private fun iniciarMotorDeJuego() {
        // Iniciar Managers Pesados
        glowingAPI = GlowingEntities(this)
        sessionManager = SessionManager(this)
        isolationManager = IsolationManager(this)
        voteManager = VoteManager()
        arenaManager = ArenaManager(this)
        mapManager = MapManager(this)
        ambientManager = AmbientManager(this)
        generatorManager = GeneratorManager(this)
        combatManager = CombatManager(this)
        spectatorManager = SpectatorManager(this)
        asesinoManager = AsesinoManager(this)
        supervivienteManager = SupervivienteManager(this)
        cinematicManager = CinematicManager(this)

        asesinoTienda = AsesinoTienda()
        supervivienteTienda = SupervivienteTienda()
        shopSelector = ShopSelector()
        antiBlockListener = AntiBlockListener(this)

        server.servicesManager.register(HealthAPI::class.java, combatManager!!, this, ServicePriority.Normal)

        // Registrar Eventos de Partida
        val pm = server.pluginManager
        pm.registerEvents(spectatorManager!!, this)
        pm.registerEvents(GameListener(this), this)
        pm.registerEvents(StaminaListener(this), this)
        pm.registerEvents(AsesinoHabilidadListener(this), this)
        pm.registerEvents(AsesinoGeneralListener(this), this)
        pm.registerEvents(antiBlockListener!!, this)
        pm.registerEvents(SupervivienteHabilidadListener(this), this)

        iniciarMotorDeParticulas()
    }

    override fun onDisable() {
        isReady = false

        // Apagado Seguro usando Safe Calls `?.`
        sessionManager?.activeSessions?.values?.forEach { it.shutdown() }
        runCatching { ambientManager?.stopAll() }
        musicManager.shutdown()
        runCatching { generatorManager?.clearGenerators() }
        runCatching { scoreboardManager.removeAll() }
        runCatching { asesinoManager?.shutdown() }
        runCatching { supervivienteManager?.shutdown() }
        runCatching { glowingAPI?.disable() }
        runCatching { databaseManager.close() }

        PacketEvents.getAPI().terminate()
        componentLogger.info(mm.deserialize("<newline><red>║ <bold>MISTAKEN</bold> desactivado.</red><newline>"))
    }

    private fun setupDatabase(): Boolean {
        return try {
            val dbFile = File(dataFolder, "database.yml")
            if (!dbFile.exists()) saveResource("database.yml", false)
            val dbConfig = YamlConfiguration.loadConfiguration(dbFile)

            databaseManager = DatabaseManager(this, dbConfig)
            componentLogger.info(mm.deserialize("<green>[DB] Conexión HikariCP establecida.</green>"))
            true
        } catch (e: Exception) {
            componentLogger.error(mm.deserialize("<red>FATAL: Error BD.</red>"))
            false
        }
    }

    private fun setupIntegrations(): Boolean {
        val rsp = server.servicesManager.getRegistration(Economy::class.java)
        economy = rsp?.provider
        if (economy == null) componentLogger.error(mm.deserialize("<red>[!] Vault / Economía no detectados.</red>"))

        craftEngineEnabled = server.pluginManager.isPluginEnabled("CraftEngine")
        if (craftEngineEnabled) componentLogger.info(mm.deserialize("<aqua>✔ CraftEngine vinculado.</aqua>"))
        return true
    }

    /**
     * Optimizado para Folia/Paper AsyncSchedulers.
     * Se delega el chequeo físico al EntityScheduler del jugador.
     */
    private fun iniciarMotorDeParticulas() {
        server.asyncScheduler.runAtFixedRate(this, { _ ->
            if (!isReady || sessionManager == null) return@runAtFixedRate

            sessionManager?.activeSessions?.values?.forEach { session ->
                if (session.currentState != GameState.INGAME) return@forEach

                session.asesinosUUIDs.forEach { uuid ->
                    val p = server.getPlayer(uuid) ?: return@forEach

                    // Folia Support: Acceder al jugador dentro de su propio Scheduler Tick
                    p.scheduler.run(this, { _ ->
                        if (p.isOnline && (p.velocity.lengthSquared() > 0.001 || p.isSprinting)) {
                            val asesino = asesinoManager?.getAsesinoDelJugador(p) ?: return@run
                            asesino.mostrarTrail(p)
                            asesino.mostrarTrailFisico(p)
                        }
                    }, null)
                }
            }
        }, 0L, 100L, TimeUnit.MILLISECONDS)
    }

    private fun handleHotReload() {
        server.onlinePlayers.forEach { player ->
            musicManager.syncPlayer(player)
            if (isGameServer) {
                combatManager?.resetHealth(player)
                val autoSession = sessionManager?.activeSessions?.values?.firstOrNull()
                    ?: sessionManager?.createSession("Esperando...")

                if (autoSession != null) {
                    sessionManager?.joinSession(player, autoSession.sessionId)
                }
            }
        }
    }

    // Funciones auxiliares simplificadas...
    fun isInEditMode(player: Player) = staffEditMode.contains(player.uniqueId)
    fun isAFK(player: Player) = afkPlayers.contains(player.uniqueId)
    fun isIgnored(player: Player): Boolean {
        val uuid = player.uniqueId
        return uuid in staffEditMode || uuid in afkPlayers || uuid in ignoredTestPlayers
    }

    private fun loadLobbyLocation() {
        val section = config.getConfigurationSection("settings.lobby") ?: return
        val worldName = section.getString("world", "world") ?: "world"
        val world = server.getWorld(worldName) ?: return

        lobbyLocation = Location(
            world,
            section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
            section.getDouble("yaw").toFloat(), section.getDouble("pitch").toFloat()
        )
        if (!isGameServer && lobbyLocation != null) {
            lobbyLocation?.world?.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
        } else if (!isGameServer) {
            componentLogger.warn(mm.deserialize("<red>[!] Falta establecer el lobby (/setlobby).</red>"))
        }
    }

    fun setLobbyLocationConfig(loc: Location) {
        this.lobbyLocation = loc
        config.createSection("settings.lobby").apply {
            set("world", loc.world.name)
            set("x", loc.x); set("y", loc.y); set("z", loc.z)
            set("yaw", loc.yaw); set("pitch", loc.pitch)
        }
        saveConfig()
    }

    private fun createRequiredFolders() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        File(dataFolder, "langs").mkdirs()
        listOf("database.yml", "music.yml").forEach {
            if (!File(dataFolder, it).exists()) runCatching { saveResource(it, false) }
        }
    }

    private fun sendLogo(timeMs: Long) {
        val msg = """
            <newline>
             <#005f73><bold> ███▄ ▄███▓ ██▓  ██████ ▄▄▄█████▓ ▄▄▄       ██ ▄█▀▓█████  ███▄    █ </bold>
             <#004488><bold>▓██▒▀█▀ ██▒▓██▒▒██    ▒ ▓  ██▒ ▓▒▒████▄     ██▄█▒ ▓█   ▀  ██ ▀█   █ </bold>
             <#003366><bold>▓██    ▓██░▒██▒░ ▓██▄   ▒ ▓██░ ▒░▒██  ▀█▄  ▓███▄░ ▒███   ▓██  ▀█ ██▒</bold>
             <#005f73><bold>▒██▒   ░██▒░██░▒██████▒▒  ▒██▒ ░  ▓█   ▓██▒▒██▒ █▄░▒████▒▒██░   ▓██░</bold>
             <#004488><bold>░ ▒░   ░  ░░▓  ▒ ▒▓▒ ▒ ░  ▒ ░░    ▒▒   ▓▒█░▒ ▒▒ ▓▒░░ ▒░ ░░ ▒░   ▒ ▒ </bold>
            <newline>
               <white>Autor:</white> <#00d4ff>Pumpkingz</#00d4ff> | <white>Modo Red:</white> <green>● $serverMode</green> | <white>Carga:</white> <yellow>${timeMs}ms</yellow>
            <newline>
        """.trimIndent()
        componentLogger.info(mm.deserialize(msg))
    }
}
