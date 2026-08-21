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
import liric.mistaken.game.managers.gameplay.HorrorEnvironmentManager
import liric.mistaken.game.managers.gameplay.CombatManager
import liric.mistaken.game.managers.gameplay.FlashlightManager
import liric.mistaken.game.managers.gameplay.GeneratorManager
import liric.mistaken.game.managers.gameplay.SpectatorManager
import liric.mistaken.game.managers.cinematic.CinematicManager
import liric.mistaken.game.managers.visual.ScoreboardManager
import liric.mistaken.listeners.mechanics.*
import liric.mistaken.listeners.interactables.*
import liric.mistaken.listeners.player.*
import liric.mistaken.listeners.world.*
import liric.mistaken.listeners.lobby.*
import liric.mistaken.listeners.killers.KillerGeneralListener
import liric.mistaken.listeners.killers.KillerSkillListener
import liric.mistaken.listeners.survivors.FlashlightListener
import liric.mistaken.listeners.survivors.SurvivorAbilityListener
import liric.mistaken.menu.menus.ShopSelector
import liric.mistaken.roles.survivors.SurvivorManager
import liric.mistaken.menu.menus.SurvivorShop
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
import liric.mistaken.api.MistakenProvider
import liric.mistaken.api.MistakenAPI
import liric.mistaken.data.db.DatabaseFactory
import liric.mistaken.game.managers.visual.ObserverHUDManager
import liric.mistaken.packet.PacketInteractListener
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.ConfigManager
import liric.mistaken.MistakenLib

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
    // iniciarMotorDeParticles() runs on the async scheduler and reads these sets via isIgnored().
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
    lateinit var visualUpdateService: liric.mistaken.game.managers.visual.VisualUpdateService
    lateinit var nameTagManager: liric.mistaken.game.managers.visual.NameTagManager
    lateinit var ambientManager: AmbientManager
    lateinit var horrorEnvironmentManager: HorrorEnvironmentManager
    lateinit var combatManager: CombatManager
    lateinit var flashlightManager: FlashlightManager
    lateinit var webHook: WebHook
    lateinit var cinematicManager: CinematicManager
    lateinit var observerHUDManager: ObserverHUDManager

    lateinit var spectatorManager: SpectatorManager
    lateinit var killerManager: KillerManager
    lateinit var survivorManager: SurvivorManager
    lateinit var killerTienda: KillerShop
    lateinit var survivorTienda: SurvivorShop
    lateinit var shopSelector: ShopSelector
    lateinit var glowingAPI: liric.mistaken.utils.misc.SafeGlowingManager

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

        // Initialize MistakenLib internal framework
        MistakenLib.init(this)

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

        // 🔥 FIX 3: Inicializamos la API ANTES de cargar los managers y killers
        val apiImpl = MistakenAPIImpl(this)
        MistakenProvider.register(apiImpl)

        glowingAPI = liric.mistaken.utils.misc.SafeGlowingManager(this)
        combatManager = CombatManager(this)
        antiBlockListener = AntiBlockListener(this)
        voteManager = VoteManager()
        ambientManager = AmbientManager(this)
        horrorEnvironmentManager = HorrorEnvironmentManager(this)
        generatorManager = GeneratorManager(this)

        flashlightManager = FlashlightManager(this)

        sessionManager = SessionManager(this)
        isolationManager = IsolationManager(this)
        visibilityManager = VisibilityManager(this)

        PacketEvents.getAPI().eventManager.registerListener(PacketVisibilityListener(visibilityManager))
        PacketEvents.getAPI().eventManager.registerListener(PacketInteractListener())

        mapManager = MapManager(this)
        scoreboardManager = ScoreboardManager(this)
        nameTagManager = liric.mistaken.game.managers.visual.NameTagManager(this)
        ambientManager = AmbientManager(this)
        arenaManager = ArenaManager(this)
        killerManager = KillerManager(this)
        survivorManager = SurvivorManager(this)
        webHook = WebHook(this)
        musicManager = MusicManager(this)
        spectatorManager = SpectatorManager(this)
        cinematicManager = CinematicManager(this)

        server.pluginManager.registerEvents(spectatorManager, this)

        killerTienda = KillerShop()
        survivorTienda = SurvivorShop()
        shopSelector = ShopSelector()
        scoreboardManager = ScoreboardManager(this)
        observerHUDManager = ObserverHUDManager(this)
        
        visualUpdateService = liric.mistaken.game.managers.visual.VisualUpdateService(this)
        visualUpdateService.start()

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
            iniciarMotorDeParticles()
        }

        sendLogo()

        val time = System.currentTimeMillis() - start
        componentLogger.info(ColorTranslator.translate("[SUCCESS] Mistaken v${pluginMeta.version} enabled in ${time}ms ($serverMode)"))
    }

    override fun onDisable() {
        isReady = false

        // Antes de cerrar sesiones y de terminar PacketEvents: hay que devolver los bloques
        // reales a los clientes o se quedan con la zona iluminada hasta el relog.
        if (::flashlightManager.isInitialized) runCatching { flashlightManager.disableAll() }
        if (::sessionManager.isInitialized) sessionManager.activeSessions.values.forEach { it.shutdown() }
        if (::ambientManager.isInitialized) runCatching { ambientManager.stopAll() }
        if (::horrorEnvironmentManager.isInitialized) runCatching { horrorEnvironmentManager.shutdown() }
        if (::musicManager.isInitialized) musicManager.shutdown()
        if (::generatorManager.isInitialized) runCatching { generatorManager.clearGenerators() }
        if (::scoreboardManager.isInitialized) runCatching { scoreboardManager.removeAll() }
        if (::nameTagManager.isInitialized) runCatching { nameTagManager.removeAll() }
        MistakenLib.shutdown()
        if (::killerManager.isInitialized) runCatching { killerManager.shutdown() }
        if (::survivorManager.isInitialized) runCatching { survivorManager.shutdown() }
        if (::observerHUDManager.isInitialized) runCatching { observerHUDManager.shutdown() }
        if (::visualUpdateService.isInitialized) runCatching { visualUpdateService.stop() }
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
        pm.registerEvents(SurvivorAbilityListener(this), this)
        pm.registerEvents(FlashlightListener(this), this)
        pm.registerEvents(GeneratorListener(this), this)
        pm.registerEvents(HackTerminalListener(this), this)
        pm.registerEvents(KeypadListener(this), this)
        pm.registerEvents(PrivateGameInteractListener(this), this)

        if (pm.isPluginEnabled("Parties")) {
            pm.registerEvents(liric.mistaken.utils.hooks.AlessioPartiesHook(this), this)
            componentLogger.info(ColorTranslator.translate("[INFO] Hooked into Parties (AlessioDP)!"))
        }
        if (pm.isPluginEnabled("LodestoneParties")) {
            pm.registerEvents(liric.mistaken.utils.hooks.LodestonePartiesHook(this), this)
            componentLogger.info(ColorTranslator.translate("[INFO] Hooked into LodestoneParties!"))
        }

        if (pm.isPluginEnabled("ObserverPaper")) {
            try {
                // Verificamos si existe la clase del evento (si tienen un JAR actualizado)
                Class.forName("com.observer.paper.api.events.ObserverPlayerIdleEvent")
                pm.registerEvents(liric.mistaken.utils.hooks.ObserverEventListener, this)
                componentLogger.info(ColorTranslator.translate("[INFO] Hooked into ObserverPaper!"))
            } catch (e: ClassNotFoundException) {
                componentLogger.warn(ColorTranslator.translate("[WARN] ObserverPaper found, but it is an older version. Update Observer to enable animation events."))
            } catch (e: Throwable) {
                componentLogger.warn(ColorTranslator.translate("[WARN] Could not register ObserverPaper events: ${e.message}"))
            }
        }
    }

    private fun iniciarMotorDeParticles() {
        // FIX #13: asyncScheduler runs on an IO thread where Bukkit API calls like
        // server.getPlayer(), player.isOnline, player.velocity, player.isSprinting
        // are NOT thread-safe and can cause IllegalStateException / data corruption.
        // globalRegionScheduler runs on the main thread — safe for all Bukkit API.
        // Period of 2 ticks (100 ms) matches the original 100 ms interval.
        server.globalRegionScheduler.runAtFixedRate(this, { _ ->
            if (!isReady) return@runAtFixedRate

            sessionManager.activeSessions.values.forEach { session ->
                if (session.currentState != GameState.INGAME) return@forEach

                session.killersUUIDs.forEach { uuid ->
                    val p = server.getPlayer(uuid) ?: return@forEach
                    val killer = killerManager.getKillerOfPlayer(p) ?: return@forEach

                    if (p.isOnline && (p.velocity.lengthSquared() > 0.001 || p.isSprinting)) {
                        // Both trail calls are now safe on the main thread — no need for the
                        // inner globalRegionScheduler.run() dispatch that was required before.
                        killer.showTrail(p)
                        killer.showPhysicalTrail(p)
                    }
                }
            }
        }, 1L, 2L) // 1 tick inicial, 2 ticks = 100 ms, on main thread

        // ECS Character Tick (1 tick rate for smooth movement and animations)
        server.globalRegionScheduler.runAtFixedRate(this, { _ ->
            if (!isReady) return@runAtFixedRate
            killerManager.getAvailableClasses().values.forEach { killer ->
                if (killer is liric.mistaken.roles.killers.BaseKiller) {
                    killer.tickAll()
                }
            }
            // Add Survivor ticking here if they also use BaseSurvivor with ECS in the future
        }, 1L, 1L)
    }

    private fun loadLobbyLocation() {
        val section = config.getConfigurationSection("settings.lobby")
        if (section == null) {
            lobbyLocation = server.worlds[0].spawnLocation
            return
        }

        val worldName = section.getString("world", "world") ?: "world"
        val world = server.getWorld(worldName)
        
        if (world == null) {
            componentLogger.warn(liric.mistaken.utils.color.ColorTranslator.translate("[WARN] El mundo del lobby ('$worldName') no existe. Usando el spawn del mundo por defecto."))
            lobbyLocation = server.worlds[0].spawnLocation
            return
        }

        lobbyLocation = Location(
            world,
            section.getDouble("x", world.spawnLocation.x),
            section.getDouble("y", world.spawnLocation.y),
            section.getDouble("z", world.spawnLocation.z),
            section.getDouble("yaw", world.spawnLocation.yaw.toDouble()).toFloat(),
            section.getDouble("pitch", world.spawnLocation.pitch.toDouble()).toFloat()
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

        val dbType = try { databaseManager.javaClass.simpleName.replace("DatabaseManager", "").replace("Manager", "") } catch (e: Exception) { "Unknown" }

        componentLogger.info(ColorTranslator.translate("""
            <newline>
             $b1<bold>███╗   ███╗██╗███████╗████████╗ █████╗ ██╗  ██╗███████╗███╗   ██╗</bold>$b1
             $b1<bold>████╗ ████║██║██╔════╝╚══██╔══╝██╔══██╗██║ ██╔╝██╔════╝████╗  ██║</bold>$b1
             $b2<bold>██╔████╔██║██║███████╗   ██║   ███████║█████╔╝ █████╗  ██╔██╗ ██║</bold>$b2
             $b3<bold>██║╚██╔╝██║██║╚════██║   ██║   ██╔══██║██╔═██╗ ██╔══╝  ██║╚██╗██║</bold>$b3
             $b4<bold>██║ ╚═╝ ██║██║███████║   ██║   ██║  ██║██║  ██╗███████╗██║ ╚████║</bold>$b4
             $b5<bold>╚═╝     ╚═╝╚═╝╚══════╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═══╝</bold>$b5
            <newline>
               <gray>Thank you for using Mistaken!</gray>
               <yellow>Source Code:</yellow> <click:open_url:'https://github.com/Pumpkiiiings/MistakenDeluxe'><hover:show_text:'<gray>Click to open repository</gray>'><yellow><underlined>https://github.com/Pumpkiiiings/MistakenDeluxe</underlined></yellow></hover></click>
            <newline>
               <white>Author:</white> $info Pumpkingz$info
               <white>Network Mode:</white> <green>● $serverMode</green>
               <white>Database:</white> <green>● $dbType</green>
            <newline>
        """.trimIndent()))
    }
}

class MistakenAPIImpl(private val _plugin: Mistaken) : MistakenAPI {
    override val plugin: org.bukkit.plugin.Plugin
        get() = _plugin
    override val killerManager: liric.mistaken.api.managers.IKillerManager
        get() = _plugin.killerManager
    override val sessionManager: liric.mistaken.api.managers.ISessionManager
        get() = _plugin.sessionManager
    override val configManager: liric.mistaken.api.managers.IConfigManager
        get() = _plugin.configManager
    override val playerDataManager: liric.mistaken.api.managers.IPlayerDataManager
        get() = _plugin.playerDataManager
    override val messages: liric.mistaken.api.managers.IMessageService
        get() = liric.mistaken.config.engine.core.MessageService
    override val mm: net.kyori.adventure.text.minimessage.MiniMessage
        get() = _plugin.mm
    override val logger: java.util.logging.Logger
        get() = _plugin.logger
    override val arenaManager: liric.mistaken.api.managers.IArenaManager
        get() = _plugin.arenaManager
    override val survivorManager: liric.mistaken.api.managers.ISurvivorManager
        get() = _plugin.survivorManager
    override val statsManager: liric.mistaken.api.managers.IStatsManager
        get() = _plugin.statsManager

    override fun isIgnored(player: org.bukkit.entity.Player): Boolean {
        return _plugin.isIgnored(player)
    }
}

