package liric.mistaken

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import kotlinx.coroutines.*
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
import kotlin.coroutines.CoroutineContext // AGREGADO

class Mistaken : JavaPlugin() {

    companion object {

        @JvmStatic
        lateinit var instance: Mistaken
            private set

        @JvmStatic
        var economy: Economy? = null
            private set

        @JvmStatic
        fun getHealthAPI(): HealthAPI? = instance.combatManager
    }

    // --- Core Variables ---
    val pluginScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val mm = MiniMessage.miniMessage()

    // 🔥 AGREGADO: El motor que conecta las Corrutinas con el Hilo Principal de Bukkit
    val bukkitDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: java.lang.Runnable) {
            if (!isEnabled) return
            if (Bukkit.isPrimaryThread()) block.run()
            else Bukkit.getScheduler().runTask(this@Mistaken, block)
        }
    }

    lateinit var assassinKey: NamespacedKey
    var craftEngineEnabled: Boolean = false
        private set

    // --- Data Structures ---
    val staffEditMode = mutableSetOf<UUID>()
    val afkPlayers = mutableSetOf<UUID>()
    var lobbyLocation: Location? = null
    var isReady = false

    // --- Managers (Lateinit: Se inicializan en onEnable) ---
    lateinit var configManager: ConfigManager
    lateinit var messageConfig: MessageConfig
    lateinit var statsManager: StatsManager
    lateinit var playerDataManager: PlayerDataManager
    lateinit var databaseManager: DatabaseManager
    lateinit var playerStatsManager: PlayerStatsManager

    // Game Managers
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

    override fun onLoad() {
        instance = this
        // PacketEvents Load (Antes de iniciar el servidor)
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings.checkForUpdates(false).bStats(true)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        val start = System.currentTimeMillis()

        // 0. 🔥 INICIALIZACIÓN CRÍTICA
        instance = this

        // Warm-up de corrutinas para evitar lag spikes iniciales
        pluginScope.launch(Dispatchers.Default) {
            delay(1)
        }

        // 1. PacketEvents & Keys
        PacketEvents.getAPI().init()
        assassinKey = NamespacedKey(this, "selected_assassin")

        // 2. Configuración y Carpetas
        saveDefaultConfig()
        createRequiredFolders()
        loadLobbyLocation()

        configManager = ConfigManager(this).apply { loadAllConfigs() }
        messageConfig = MessageConfig(this)

        // 3. Hooks (Vault, etc)
        if (!setupIntegrations()) return

        // 4. Base de Datos
        if (!setupDatabase()) return

        // 5. Datos Base
        playerStatsManager = PlayerStatsManager(this)
        statsManager = StatsManager(this)
        playerDataManager = PlayerDataManager(this)

        // 6. El Corazón de los Managers
        combatManager = CombatManager(this)
        gameManager = GameManager(this)
        mapManager = MapManager(this)
        arenaManager = ArenaManager(this)
        asesinoManager = AsesinoManager(this)
        supervivienteManager = SupervivienteManager(this)
        discordManager = DiscordManager(this)
        generatorManager = GeneratorManager(this)

        // 🔥 INICIALIZACIÓN DEL MOTOR MUSICAL
        musicManager = MusicManager(this)

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

        // 9. Eventos y Comandos Brigadier
        registerEvents()
        CommandRegistry(this).registerAll()

        // 10. 🏁 TAREAS FINALES Y ACTIVACIÓN DEL LOBBY

        // Marcamos el plugin como listo
        isReady = true

        // --- 🏥 SINCRONIZACIÓN DE JUGADORES (Lobby Ready) ---
        // Aplicamos la salud 20/200 y corazones a todos los que ya estén dentro
        Bukkit.getOnlinePlayers().forEach { player ->
            combatManager.resetHealth(player)
            musicManager.syncPlayer(player) // Para que escuchen la música del lobby de una
        }

        // --- 🌍 REGLAS DEL MUNDO LOBBY ---
        lobbyLocation?.world?.let { world ->
            // Activa reaparición instantánea en el lobby
            world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true)
            // Opcional: Activar música en el mundo del lobby si no lo has hecho en el MusicManager
        }

        // --- 🚀 MOTORES EN MARCHA ---
        iniciarMotorDeParticulas()
        sendLogo()

        val time = System.currentTimeMillis() - start
        componentLogger.info(mm.deserialize("<gradient:green:aqua>Mistaken v${description.version} habilitado en ${time}ms (Full Lobby Sync)</gradient>"))
    }

    override fun onDisable() {
        isReady = false

        pluginScope.cancel("Plugin shutting down")

        if (::ambientManager.isInitialized) runCatching { ambientManager.stopAll() }
        if (::musicManager.isInitialized) musicManager.shutdown()
        if (::generatorManager.isInitialized) runCatching { generatorManager.clearGenerators() }
        if (::scoreboardManager.isInitialized) runCatching { scoreboardManager.removeAll() }
        if (::asesinoManager.isInitialized) runCatching { asesinoManager.shutdown() } // Llama a su propio método de limpieza total
        if (::supervivienteManager.isInitialized) runCatching { supervivienteManager.shutdown() }
        if (::databaseManager.isInitialized) runCatching { databaseManager.close() }

        PacketEvents.getAPI().terminate()

        componentLogger.info(mm.deserialize(
            "<newline><red>║ <bold>MISTAKEN</bold> ha sido desactivado con éxito.</red><newline>"
        ))
    }

    // --- SETUP HELPERS ---

    private fun setupDatabase(): Boolean {
        return try {
            // Cargamos la config de DB
            val dbFile = File(dataFolder, "database.yml")
            if (!dbFile.exists()) saveResource("database.yml", false)
            val dbConfig = YamlConfiguration.loadConfiguration(dbFile)

            // Inicializamos managers
            databaseManager = DatabaseManager(this, dbConfig) // Usamos el constructor que espera FileConfiguration
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
        // Usamos el scheduler asíncrono de Paper para el filtrado y cálculos matemáticos
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            if (gameManager.currentState != GameState.INGAME) return@Runnable

            // Lista para agrupar a quiénes debemos aplicarles efectos físicos en el hilo principal
            val targetsForPhysicalTrail = mutableListOf<Pair<Player, Asesino>>()

            // 1. FILTRADO ASÍNCRONO (Aquí no lagueamos a nadie)
            asesinoManager.asesinosActivos.forEach { (uuid, asesino) ->
                val p = Bukkit.getPlayer(uuid) ?: return@forEach

                // Verificaciones rápidas (Matemática simple)
                if (p.isOnline && (p.velocity.lengthSquared() > 0.001 || p.isSprinting)) {

                    // A. Mostrar Trail de Paquetes (PacketEvents es ASYNC-SAFE)
                    // Esto se queda en este hilo asíncrono. ¡Súper rápido!
                    asesino.mostrarTrail(p)

                    // B. Agregamos a la lista para el proceso síncrono
                    targetsForPhysicalTrail.add(p to asesino)
                }
            }

            // 2. PROCESO SÍNCRONO AGRUPADO (Batching)
            if (targetsForPhysicalTrail.isNotEmpty()) {
                // Saltamos al hilo principal una sola vez para todos los asesinos
                // Usamos el scheduler de Bukkit directo que es más liviano que 'launch' para tareas de alta frecuencia
                server.scheduler.runTask(this, Runnable {
                    for (pair in targetsForPhysicalTrail) {
                        val player = pair.first
                        val asesino = pair.second

                        // Doble check de seguridad
                        if (player.isOnline) {
                            asesino.mostrarTrailFisico(player)
                        }
                    }
                })
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
        // 1. Crear estructura de carpetas
        listOf("lang", "menus").forEach { folderName ->
            File(dataFolder, folderName).takeIf { !it.exists() }?.mkdirs()
        }

        // 2. Archivos en la raíz del plugin
        listOf("database.yml", "music.yml").forEach { fileName ->
            if (!File(dataFolder, fileName).exists()) {
                saveResource(fileName, false)
            }
        }

        // 3. Archivos de Lenguaje (Soporte Multi-Lang)
        listOf("es.yml", "en.yml", "jp.yml", "fr.yml", "zh.yml").forEach { langName ->
            val internalPath = "lang/$langName"
            if (!File(dataFolder, internalPath).exists()) {
                // runCatching es el try-catch "moderno" de Kotlin
                runCatching { saveResource(internalPath, false) }
            }
        }

        // 4. Archivos de Menús (GUIs)
        listOf(
            "tienda_principal.yml",
            "asesinos_tienda.yml",
            "supervivientes_tienda.yml"
        ).forEach { menuName ->
            val internalPath = "menus/$menuName"
            if (!File(dataFolder, internalPath).exists()) {
                runCatching {
                    saveResource(internalPath, false)
                }.onFailure {
                    componentLogger.warn(mm.deserialize("<yellow>⚠️ No se pudo exportar el menú: $menuName</yellow>"))
                }
            }
        }

        componentLogger.info(mm.deserialize("<gray>[System] Estructura de archivos verificada correctamente.</gray>"))
    }

    // --- STATE CHECKS ---
    fun isInEditMode(player: Player) = staffEditMode.contains(player.uniqueId)
    fun isAFK(player: Player) = afkPlayers.contains(player.uniqueId)
    fun isIgnored(player: Player) = isInEditMode(player) || isAFK(player)

    private fun sendLogo() {
        val b1 = "<#005f73>"
        val b2 = "<#004488>"
        val b3 = "<#003366>"
        val b4 = "<#005f73>"
        val b5 = "<#004488>"
        val info = "<#00d4ff>"

        // Paper Logger soporta componentes directos, mucho más limpio
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
