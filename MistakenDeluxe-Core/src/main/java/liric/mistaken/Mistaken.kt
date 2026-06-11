package liric.mistaken

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import fr.skytasul.glowingentities.GlowingEntities
import liric.mistaken.api.HealthAPI
import liric.mistaken.data.stats.StatsManager
import liric.mistaken.game.managers.audio.MusicManager
import liric.mistaken.roles.asesinos.AsesinoManager
import liric.mistaken.menu.menus.AsesinoTienda
import liric.mistaken.commands.CommandRegistry
import liric.mistaken.data.PlayerDataManager
import liric.mistaken.roles.asesinos.Asesino
import liric.mistaken.data.DatabaseManager
import liric.mistaken.utils.hooks.WebHook
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.managers.*
import liric.mistaken.game.managers.engine.ArenaManager
import liric.mistaken.game.managers.engine.IsolationManager
import liric.mistaken.game.managers.engine.MapManager
import liric.mistaken.game.managers.engine.SessionManager
import liric.mistaken.game.managers.engine.VoteManager
import liric.mistaken.game.managers.gameplay.AmbientManager
import liric.mistaken.game.managers.gameplay.CombatManager
import liric.mistaken.game.managers.gameplay.GeneratorManager
import liric.mistaken.game.managers.gameplay.SpectatorManager
import liric.mistaken.game.managers.visual.CinematicManager
import liric.mistaken.game.managers.visual.ScoreboardManager
import liric.mistaken.listeners.*
import liric.mistaken.listeners.asesinos.AsesinoGeneralListener
import liric.mistaken.listeners.asesinos.AsesinoHabilidadListener
import liric.mistaken.listeners.supervivientes.SupervivienteHabilidadListener
import liric.mistaken.menu.menus.ShopSelector
import liric.mistaken.roles.supervivientes.SupervivienteManager
import liric.mistaken.menu.menus.SupervivienteTienda
import liric.mistaken.utils.hooks.Placeholders
import net.kyori.adventure.text.minimessage.MiniMessage
import net.milkbowl.vault.economy.Economy
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicePriority
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.bukkit.plugin.java.JavaPlugin

@Suppress("UnstableApiUsage")
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
    lateinit var assassinKey: NamespacedKey
    var craftEngineEnabled: Boolean = false
        private set
    var serverMode: String = "GAME_SERVER"
        private set

    val staffEditMode = mutableSetOf<UUID>()
    val afkPlayers = mutableSetOf<UUID>()
    var lobbyLocation: Location? = null
    var isReady = false
    val ignoredTestPlayers = mutableSetOf<UUID>()

    val configManager get() = pumpking.lib.config.ConfigManager
    lateinit var statsManager: StatsManager
    lateinit var playerDataManager: PlayerDataManager
    lateinit var databaseManager: DatabaseManager

    lateinit var sessionManager: SessionManager
    lateinit var isolationManager: IsolationManager

    lateinit var antiBlockListener: AntiBlockListener
    lateinit var voteManager: VoteManager
    lateinit var arenaManager: ArenaManager
    lateinit var musicManager: MusicManager
    lateinit var generatorManager: GeneratorManager
    lateinit var mapManager: MapManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var ambientManager: AmbientManager
    lateinit var combatManager: CombatManager
    lateinit var webHook: WebHook
    lateinit var cinematicManager: CinematicManager

    lateinit var spectatorManager: SpectatorManager
    lateinit var asesinoManager: AsesinoManager
    lateinit var supervivienteManager: SupervivienteManager
    lateinit var asesinoTienda: AsesinoTienda
    lateinit var supervivienteTienda: SupervivienteTienda
    lateinit var shopSelector: ShopSelector
    lateinit var glowingAPI: GlowingEntities

    override fun onLoad() {
        instance = this
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings.checkForUpdates(false).bStats(true)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        val start = System.currentTimeMillis()
        instance = this

        PacketEvents.getAPI().init()
        assassinKey = NamespacedKey(this, "selected_assassin")
        server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")

        saveDefaultConfig()
        createRequiredFolders()

        // Initialize PumpkingLib internal framework
        pumpking.lib.core.PumpkingLib.init(this)

        // ðŸ”¥ FIX 1: Registramos los comandos PRIMERO.
        // Si la base de datos o el lobby fallan, al menos tendrÃ¡s comandos para arreglarlo.
        CommandRegistry(this).registerAll()

        serverMode = config.getString("server-mode", "GAME_SERVER")?.uppercase() ?: "GAME_SERVER"
        componentLogger.info(mm.deserialize("<yellow>[Red] Modo del servidor configurado como: <bold>$serverMode</bold></yellow>"))

        loadLobbyLocation()
        if (serverMode == "MULTIARENA" || serverMode == "NETWORK_LOBBY") {
            if (lobbyLocation != null) {
                lobbyLocation?.world?.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
            } else {
                componentLogger.warn(mm.deserialize("<red>[!] Falta establecer el lobby (/setlobby).</red>"))
            }
        } else if (serverMode == "GAME_SERVER" && lobbyLocation == null) {
            componentLogger.warn(mm.deserialize("<red>[!] GAME_SERVER requiere /setlobby para crear el Pre-Lobby de cristal.</red>"))
        }

        // ðŸ”¥ FIX 2: Si falla la conexiÃ³n de DB o Vault, no apagamos el plugin entero.
        // Solo lanzamos el warning, para que puedas usar comandos de admin.
        setupIntegrations()
        setupDatabase()

        statsManager = StatsManager(this)
        playerDataManager = PlayerDataManager(this)

        // ðŸ”¥ FIX 3: Inicializamos la API ANTES de cargar los managers y asesinos
        val apiImpl = liric.mistaken.api.MistakenAPIImpl(this)
        liric.mistaken.api.MistakenProvider.register(apiImpl)

        glowingAPI = GlowingEntities(this)
        combatManager = CombatManager(this)
        antiBlockListener = AntiBlockListener(this)
        voteManager = VoteManager()
        ambientManager = AmbientManager(this)
        generatorManager = GeneratorManager(this)

        sessionManager = SessionManager(this)
        isolationManager = IsolationManager(this)

        mapManager = MapManager(this)
        arenaManager = ArenaManager(this)
        asesinoManager = AsesinoManager(this)
        supervivienteManager = SupervivienteManager(this)
        webHook = WebHook(this)
        musicManager = MusicManager(this)
        spectatorManager = SpectatorManager(this)
        cinematicManager = CinematicManager(this)

        server.pluginManager.registerEvents(spectatorManager, this)

        asesinoTienda = AsesinoTienda()
        supervivienteTienda = SupervivienteTienda()
        shopSelector = ShopSelector()
        scoreboardManager = ScoreboardManager(this)

        server.servicesManager.register(HealthAPI::class.java, combatManager, this, ServicePriority.Normal)

        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            Placeholders(this).register()
        }

        registerEvents()

        isReady = true

        if (serverMode != "NETWORK_LOBBY") {
            server.onlinePlayers.forEach { player ->
                combatManager.resetHealth(player)
                musicManager.syncPlayer(player)
            }
            iniciarMotorDeParticulas()
        }

        sendLogo()

        val time = System.currentTimeMillis() - start
        componentLogger.info(mm.deserialize("<gradient:green:aqua>Mistaken v${pluginMeta.version} habilitado en ${time}ms ($serverMode)</gradient>"))
    }

    override fun onDisable() {
        isReady = false

        if (::sessionManager.isInitialized) sessionManager.activeSessions.values.forEach { it.shutdown() }
        if (::ambientManager.isInitialized) runCatching { ambientManager.stopAll() }
        if (::musicManager.isInitialized) musicManager.shutdown()
        if (::generatorManager.isInitialized) runCatching { generatorManager.clearGenerators() }
        if (::scoreboardManager.isInitialized) runCatching { scoreboardManager.removeAll() }
        pumpking.lib.core.PumpkingLib.shutdown()
        if (::asesinoManager.isInitialized) runCatching { asesinoManager.shutdown() }
        if (::supervivienteManager.isInitialized) runCatching { supervivienteManager.shutdown() }
        if (::glowingAPI.isInitialized) runCatching { glowingAPI.disable() }
        if (::databaseManager.isInitialized) runCatching { databaseManager.close() }

        PacketEvents.getAPI().terminate()

        componentLogger.info(mm.deserialize("<newline><red>â•‘ <bold>MISTAKEN</bold> ha sido desactivado con Ã©xito.</red><newline>"))
    }

    private fun setupDatabase(): Boolean {
        return try {
            val dbFile = File(dataFolder, "database.yml")
            if (!dbFile.exists()) saveResource("database.yml", false)
            
            databaseManager = liric.mistaken.data.db.DatabaseFactory.create(this)
            databaseManager.setup()
            true
        } catch (e: Exception) {
            componentLogger.error(mm.deserialize("<red>No se pudo conectar a la base de datos. Los datos no se guardarÃ¡n.</red>"))
            false
        }
    }

    private fun setupIntegrations(): Boolean {
        val rsp: RegisteredServiceProvider<Economy>? = server.servicesManager.getRegistration(Economy::class.java)

        if (rsp == null) {
            componentLogger.error(mm.deserialize("<red><b>[!]</b> Vault no encontrÃ³ ningÃºn plugin de economÃ­a compatible.</red>"))
            return false
        }
        economy = rsp.provider

        craftEngineEnabled = server.pluginManager.isPluginEnabled("CraftEngine")
        if (craftEngineEnabled) componentLogger.info(mm.deserialize("<aqua>âœ” CraftEngine detectado y vinculado.</aqua>"))

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
        pm.registerEvents(GeneratorListener(this), this)
    }

    private fun iniciarMotorDeParticulas() {
        server.asyncScheduler.runAtFixedRate(this, { _ ->
            if (!isReady) return@runAtFixedRate

            sessionManager.activeSessions.values.forEach { session ->
                if (session.currentState != GameState.INGAME) return@forEach

                val targetsForPhysicalTrail = mutableListOf<Pair<Player, Asesino>>()

                session.asesinosUUIDs.forEach { uuid ->
                    val p = server.getPlayer(uuid) ?: return@forEach
                    val asesino = asesinoManager.getAsesinoDelJugador(p) ?: return@forEach

                    if (p.isOnline && (p.velocity.lengthSquared() > 0.001 || p.isSprinting)) {
                        asesino.mostrarTrail(p)
                        targetsForPhysicalTrail.add(p to asesino)
                    }
                }

                if (targetsForPhysicalTrail.isNotEmpty()) {
                    server.globalRegionScheduler.run(this) { _ ->
                        for (pair in targetsForPhysicalTrail) {
                            if (pair.first.isOnline) pair.second.mostrarTrailFisico(pair.first)
                        }
                    }
                }
            }
        }, 0L, 100L, TimeUnit.MILLISECONDS)
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

        // Carpeta global de layouts de menÃºs (un YAML por menÃº, sin duplicar por idioma)
        val menusFolder = File(dataFolder, "menus")
        if (!menusFolder.exists()) menusFolder.mkdirs()

        val baseFiles = listOf("database.yml", "music.yml")
        baseFiles.forEach { fileName ->
            val file = File(dataFolder, fileName)
            if (!file.exists()) {
                runCatching { saveResource(fileName, false) }
            }
        }
    }


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
             $b1<bold>â–ˆâ–ˆâ–ˆâ•—   â–ˆâ–ˆâ–ˆâ•—â–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•— â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•— â–ˆâ–ˆâ•—  â–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ•—   â–ˆâ–ˆâ•—</bold>$b1
             $b1<bold>â–ˆâ–ˆâ–ˆâ–ˆâ•— â–ˆâ–ˆâ–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•”â•â•â•â•â•â•šâ•â•â–ˆâ–ˆâ•”â•â•â•â–ˆâ–ˆâ•”â•â•â–ˆâ–ˆâ•—â–ˆâ–ˆâ•‘ â–ˆâ–ˆâ•”â•â–ˆâ–ˆâ•”â•â•â•â•â•â–ˆâ–ˆâ–ˆâ–ˆâ•—  â–ˆâ–ˆâ•‘</bold>$b1
             $b2<bold>â–ˆâ–ˆâ•”â–ˆâ–ˆâ–ˆâ–ˆâ•”â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—   â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•‘â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•”â• â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—  â–ˆâ–ˆâ•”â–ˆâ–ˆâ•— â–ˆâ–ˆâ•‘</bold>$b2
             $b3<bold>â–ˆâ–ˆâ•‘â•šâ–ˆâ–ˆâ•”â•â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘â•šâ•â•â•â•â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•”â•â•â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•”â•â–ˆâ–ˆâ•— â–ˆâ–ˆâ•”â•â•â•  â–ˆâ–ˆâ•‘â•šâ–ˆâ–ˆâ•—â–ˆâ–ˆâ•‘</bold>$b3
             $b4<bold>â–ˆâ–ˆâ•‘ â•šâ•â• â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘  â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘  â–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â–ˆâ–ˆâ•‘ â•šâ–ˆâ–ˆâ–ˆâ–ˆâ•‘</bold>$b4
             $b5<bold>â•šâ•â•     â•šâ•â•â•šâ•â•â•šâ•â•â•â•â•â•â•   â•šâ•â•   â•šâ•â•  â•šâ•â•â•šâ•â•  â•šâ•â•â•šâ•â•â•â•â•â•â•â•šâ•â•  â•šâ•â•â•â•</bold>$b5
             $b4<bold>               ___  ____ _    _  _ _  _ ____ </bold>$b4
             $b5<bold>               |  \ |___ |    |  |  \/  |___ </bold>$b5
             $b5<bold>               |__/ |___ |___ |__| _/\_ |___ </bold>$b5
            <newline>
               <white>Autor:</white> $info Pumpkingz$info
               <white>Modo Red:</white> <green>â— $serverMode</green>
            <newline>
        """.trimIndent()))
    }
}

