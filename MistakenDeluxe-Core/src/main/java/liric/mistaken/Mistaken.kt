package liric.mistaken

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import fr.skytasul.glowingentities.GlowingEntities
import liric.mistaken.api.HealthAPI
import liric.mistaken.data.stats.StatsManager
import liric.mistaken.game.managers.audio.MusicManager
import liric.mistaken.roles.killers.KillerManager
import liric.mistaken.menu.menus.KillerShop
import liric.mistaken.commands.CommandRegistry
import liric.mistaken.data.PlayerDataManager
import liric.mistaken.roles.killers.Killer
import liric.mistaken.data.DatabaseManager
import liric.mistaken.utils.hooks.WebHook
import liric.mistaken.game.enums.GameState
import liric.mistaken.game.managers.*
import liric.mistaken.game.managers.engine.ArenaManager
import liric.mistaken.game.managers.engine.IsolationManager
import liric.mistaken.game.managers.engine.MapManager
import liric.mistaken.game.managers.engine.SessionManager
import liric.mistaken.game.managers.engine.VoteManager
import liric.mistaken.game.managers.engine.visibility.VisibilityManager
import liric.mistaken.game.managers.engine.visibility.PacketVisibilityListener
import liric.mistaken.game.managers.gameplay.AmbientManager
import liric.mistaken.game.managers.gameplay.CombatManager
import liric.mistaken.game.managers.gameplay.GeneratorManager
import liric.mistaken.game.managers.gameplay.SpectatorManager
import liric.mistaken.game.managers.cinematic.CinematicManager
import liric.mistaken.game.managers.visual.ScoreboardManager
import liric.mistaken.listeners.*
import liric.mistaken.listeners.killers.KillerGeneralListener
import liric.mistaken.listeners.killers.KillerSkillListener
import liric.mistaken.listeners.survivors.SurvivorHabilidadListener
import liric.mistaken.menu.menus.ShopSelector
import liric.mistaken.roles.survivors.SurvivorManager
import liric.mistaken.menu.menus.SurvivorTienda
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
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.plugin.java.JavaPlugin
import dev.triumphteam.gui.TriumphGui
import liric.mistaken.api.MistakenAPIImpl
import liric.mistaken.api.MistakenProvider
import liric.mistaken.data.db.DatabaseFactory
import liric.mistaken.game.managers.visual.ObserverHUDManager
import liric.mistaken.packet.PacketInteractListener
import pumpking.lib.color.ColorTranslator
import pumpking.lib.config.ConfigManager
import pumpking.lib.core.PumpkingLib

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

    // FIX #12: mutableSetOf<UUID>() returns a LinkedHashSet which is NOT thread-safe.
    // iniciarMotorDeParticulas() runs on the async scheduler and reads these sets via isIgnored().
    // ConcurrentHashMap.newKeySet() provides a thread-safe, lock-free Set backed by CHM.
    val staffEditMode: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val afkPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    var lobbyLocation: Location? = null
    var isReady = false
    val ignoredTestPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    val configManager get() = ConfigManager
    lateinit var statsManager: StatsManager
    lateinit var playerDataManager: PlayerDataManager
    lateinit var databaseManager: DatabaseManager

    lateinit var sessionManager: SessionManager
    lateinit var isolationManager: IsolationManager
    lateinit var visibilityManager: VisibilityManager

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
    lateinit var observerHUDManager: ObserverHUDManager

    lateinit var spectatorManager: SpectatorManager
    lateinit var asesinoManager: KillerManager
    lateinit var supervivienteManager: SurvivorManager
    lateinit var asesinoTienda: KillerShop
    lateinit var supervivienteTienda: SurvivorTienda
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
        
        // Fix for Triumph-GUI classloader crash in Paper
        TriumphGui.init(this)

        PacketEvents.getAPI().init()
        assassinKey = NamespacedKey(this, "selected_assassin")
        // FIX #20: registerOutgoingPluginChannel throws IllegalArgumentException if the
        // channel is already registered (e.g. on hot-reload). Guard with isOutgoingChannelRegistered.
        if (!server.messenger.isOutgoingChannelRegistered(this, "BungeeCord")) {
            server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")
        }

        saveDefaultConfig()
        createRequiredFolders()

        // Initialize PumpkingLib internal framework
        PumpkingLib.init(this)

        // 🔥 FIX 1: Registramos los comandos PRIMERO.
        // Si la base de datos o el lobby fallan, al menos tendrás comandos para arreglarlo.
        CommandRegistry(this).registerAll()

        serverMode = config.getString("server-mode", "GAME_SERVER")?.uppercase() ?: "GAME_SERVER"
        componentLogger.info(ColorTranslator.translate("[INFO] Server mode set to: $serverMode"))

        loadLobbyLocation()
        if (serverMode == "MULTIARENA" || serverMode == "NETWORK_LOBBY") {
            if (lobbyLocation != null) {
                lobbyLocation?.world?.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
            } else {
                componentLogger.warn(ColorTranslator.translate("[WARN] Lobby is not set (/setlobby)."))
            }
        } else if (serverMode == "GAME_SERVER" && lobbyLocation == null) {
            componentLogger.warn(ColorTranslator.translate("[WARN] GAME_SERVER requires /setlobby to create the glass Pre-Lobby."))
        }

        // 🔥 FIX 2: Si falla la conexión de DB o Vault, no apagamos el plugin entero.
        // Solo lanzamos el warning, para que puedas usar comandos de admin.
        setupIntegrations()
        setupDatabase()

        statsManager = StatsManager(this)
        playerDataManager = PlayerDataManager(this)

        // 🔥 FIX 3: Inicializamos la API ANTES de cargar los managers y asesinos
        val apiImpl = MistakenAPIImpl(this)
        MistakenProvider.register(apiImpl)

        glowingAPI = GlowingEntities(this)
        combatManager = CombatManager(this)
        antiBlockListener = AntiBlockListener(this)
        voteManager = VoteManager()
        ambientManager = AmbientManager(this)
        generatorManager = GeneratorManager(this)

        sessionManager = SessionManager(this)
        isolationManager = IsolationManager(this)
        visibilityManager = VisibilityManager(this)

        PacketEvents.getAPI().eventManager.registerListener(PacketVisibilityListener(visibilityManager))
        PacketEvents.getAPI().eventManager.registerListener(PacketInteractListener())

        mapManager = MapManager(this)
        arenaManager = ArenaManager(this)
        asesinoManager = KillerManager(this)
        supervivienteManager = SurvivorManager(this)
        webHook = WebHook(this)
        musicManager = MusicManager(this)
        spectatorManager = SpectatorManager(this)
        cinematicManager = CinematicManager(this)

        server.pluginManager.registerEvents(spectatorManager, this)

        asesinoTienda = KillerShop()
        supervivienteTienda = SurvivorTienda()
        shopSelector = ShopSelector()
        scoreboardManager = ScoreboardManager(this)
        observerHUDManager = ObserverHUDManager(this)

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
        componentLogger.info(ColorTranslator.translate("[SUCCESS] Mistaken v${pluginMeta.version} enabled in ${time}ms ($serverMode)"))
    }

    override fun onDisable() {
        isReady = false

        if (::sessionManager.isInitialized) sessionManager.activeSessions.values.forEach { it.shutdown() }
        if (::ambientManager.isInitialized) runCatching { ambientManager.stopAll() }
        if (::musicManager.isInitialized) musicManager.shutdown()
        if (::generatorManager.isInitialized) runCatching { generatorManager.clearGenerators() }
        if (::scoreboardManager.isInitialized) runCatching { scoreboardManager.removeAll() }
        PumpkingLib.shutdown()
        if (::asesinoManager.isInitialized) runCatching { asesinoManager.shutdown() }
        if (::supervivienteManager.isInitialized) runCatching { supervivienteManager.shutdown() }
        if (::observerHUDManager.isInitialized) runCatching { observerHUDManager.shutdown() }
        if (::glowingAPI.isInitialized) runCatching { glowingAPI.disable() }
        if (::databaseManager.isInitialized) runCatching { databaseManager.close() }
        // FIX #3: webHook.shutdown() was missing from onDisable — the CoroutineScope and
        // HttpClient were never released, leaking IO threads and TLS sockets.
        if (::webHook.isInitialized) runCatching { webHook.shutdown() }

        PacketEvents.getAPI().terminate()

        componentLogger.info(ColorTranslator.translate("[INFO] MISTAKEN has been successfully disabled."))
    }

    private fun setupDatabase(): Boolean {
        return try {
            val dbFile = File(dataFolder, "database.yml")
            if (!dbFile.exists()) saveResource("database.yml", false)

            databaseManager = DatabaseFactory.create(this)
            databaseManager.setup()
            true
        } catch (e: Exception) {
            componentLogger.error(ColorTranslator.translate("[ERROR] Could not connect to the database. Data will not be saved."))
            false
        }
    }

    private fun setupIntegrations(): Boolean {
        val rsp: RegisteredServiceProvider<Economy>? = server.servicesManager.getRegistration(Economy::class.java)

        if (rsp == null) {
            componentLogger.error(ColorTranslator.translate("[ERROR] Vault found no compatible economy plugin."))
            return false
        }
        economy = rsp.provider

        craftEngineEnabled = server.pluginManager.isPluginEnabled("CraftEngine")
        if (craftEngineEnabled) componentLogger.info(ColorTranslator.translate("[SUCCESS] CraftEngine detected and hooked."))

        return true
    }

    private fun registerEvents() {
        val pm = server.pluginManager
        pm.registerEvents(PlayerListener(this), this)
        pm.registerEvents(PlayerQuitListener(this), this)
        pm.registerEvents(GameListener(this), this)
        pm.registerEvents(StaminaListener(this), this)
        pm.registerEvents(KillerSkillListener(this), this)
        pm.registerEvents(KillerGeneralListener(this), this)
        pm.registerEvents(antiBlockListener, this)
        pm.registerEvents(SurvivorHabilidadListener(this), this)
        pm.registerEvents(GeneratorListener(this), this)
    }

    private fun iniciarMotorDeParticulas() {
        // FIX #13: asyncScheduler runs on an IO thread where Bukkit API calls like
        // server.getPlayer(), player.isOnline, player.velocity, player.isSprinting
        // are NOT thread-safe and can cause IllegalStateException / data corruption.
        // globalRegionScheduler runs on the main thread — safe for all Bukkit API.
        // Period of 2 ticks (100 ms) matches the original 100 ms interval.
        server.globalRegionScheduler.runAtFixedRate(this, { _ ->
            if (!isReady) return@runAtFixedRate

            sessionManager.activeSessions.values.forEach { session ->
                if (session.currentState != GameState.INGAME) return@forEach

                session.asesinosUUIDs.forEach { uuid ->
                    val p = server.getPlayer(uuid) ?: return@forEach
                    val asesino = asesinoManager.getKillerOfPlayer(p) ?: return@forEach

                    if (p.isOnline && (p.velocity.lengthSquared() > 0.001 || p.isSprinting)) {
                        // Both trail calls are now safe on the main thread — no need for the
                        // inner globalRegionScheduler.run() dispatch that was required before.
                        asesino.showTrail(p)
                        asesino.showPhysicalTrail(p)
                    }
                }
            }
        }, 1L, 2L) // 1 tick inicial, 2 ticks = 100 ms, on main thread
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

        // Carpeta global de layouts de menús (un YAML por menú, sin duplicar por idioma)
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

        componentLogger.info(ColorTranslator.translate("""
            <newline>
             $b1<bold>███╗   ███╗██╗███████╗████████╗ █████╗ ██╗  ██╗███████╗███╗   ██╗</bold>$b1
             $b1<bold>████╗ ████║██║██╔════╝╚══██╔══╝██╔══██╗██║ ██╔╝██╔════╝████╗  ██║</bold>$b1
             $b2<bold>██╔████╔██║██║███████╗   ██║   ███████║█████╔╝ █████╗  ██╔██╗ ██║</bold>$b2
             $b3<bold>██║╚██╔╝██║██║╚════██║   ██║   ██╔══██║██╔═██╗ ██╔══╝  ██║╚██╗██║</bold>$b3
             $b4<bold>██║ ╚═╝ ██║██║███████║   ██║   ██║  ██║██║  ██╗███████╗██║ ╚████║</bold>$b4
             $b5<bold>╚═╝     ╚═╝╚═╝╚══════╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═══╝</bold>$b5
             $b4<bold>               ___  ____ _    _  _ _  _ ____ </bold>$b4
             $b5<bold>               |  \ |___ |    |  |  \/  |___ </bold>$b5
             $b5<bold>               |__/ |___ |___ |__| _/\_ |___ </bold>$b5
            <newline>
               <white>Autor:</white> $info Pumpkingz$info
               <white>Modo Red:</white> <green>● $serverMode</green>
            <newline>
        """.trimIndent()))
    }
}

