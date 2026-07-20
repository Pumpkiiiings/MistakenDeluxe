package liric.mistaken.level

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.ServicePriority
import liric.mistaken.api.level.LevelProvider
import liric.mistaken.api.level.ExperienceProvider
import liric.mistaken.level.api.LevelProviderImpl
import liric.mistaken.level.api.ExperienceProviderImpl
import liric.mistaken.level.manager.LevelManager
import liric.mistaken.level.database.LevelRepository
import liric.mistaken.level.listener.ExperienceListener
import liric.mistaken.level.listener.AdvancementHookListener
import liric.mistaken.level.command.LevelCommand
import liric.mistaken.level.command.LevelAdminCommand
import liric.mistaken.level.config.LevelConfig
import liric.mistaken.level.api.MistakenLevelExpansion
import liric.mistaken.level.integration.UltimateAdvancementHook
import liric.mistaken.level.rewards.RewardRegistry
import liric.mistaken.level.rewards.types.*
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import pumpking.lib.database.HikariDatabaseManager
import pumpking.lib.database.DatabaseType
import org.bukkit.Bukkit
import java.io.File
import liric.mistaken.level.config.MenuConfig
import liric.mistaken.level.config.XpSourcesConfig

class LevelAddonPlugin : JavaPlugin() {

    lateinit var levelProvider: LevelProviderImpl
        private set

    lateinit var experienceProvider: ExperienceProviderImpl
        private set

    lateinit var manager: LevelManager
        private set

    lateinit var repository: LevelRepository
        private set

    lateinit var levelConfig: LevelConfig
        private set

    lateinit var xpSourcesConfig: XpSourcesConfig
        private set

    lateinit var menuConfig: MenuConfig
        private set

    private lateinit var databaseProvider: HikariDatabaseManager

    override fun onEnable() {
        logger.info("[MistakenDeluxe-Level-Addon] Starting up...")

        // Init config
        saveDefaultConfig()
        saveResource("levels.yml", false)
        // No rewards.yml needed as it was merged, but keeping it if it's there
        saveResource("messages.yml", false)
        try { saveResource("xp_sources.yml", false) } catch (e: Exception) {}

        levelConfig = LevelConfig(this)
        levelConfig.load()

        xpSourcesConfig = XpSourcesConfig(this)
        xpSourcesConfig.load()

        menuConfig = MenuConfig(this)
        menuConfig.load()

        // Init Database
        // For simplicity we will assume SQLite if config is SQLITE
        val dbTypeStr = config.getString("database.type", "SQLITE")
        val dbType = try { DatabaseType.valueOf(dbTypeStr!!) } catch (e: Exception) { DatabaseType.SQLITE }
        
        databaseProvider = HikariDatabaseManager(
            dbType,
            config.getString("database.host", "localhost")!!,
            config.getInt("database.port", 3306),
            config.getString("database.database", "minecraft")!!,
            config.getString("database.username", "root")!!,
            config.getString("database.password", "")!!,
            File(dataFolder, "levels.db")
        )
        repository = LevelRepository(databaseProvider)
        repository.init()

        // Init Hooks
        val advancementHook = UltimateAdvancementHook(this)

        // Init Managers
        manager = LevelManager(this)

        // Register Rewards
        RewardRegistry.register("message", MessageReward())
        RewardRegistry.register("actionbar", ActionBarReward())
        RewardRegistry.register("title", TitleReward())
        RewardRegistry.register("command", CommandReward())
        RewardRegistry.register("killer", KillerReward())
        RewardRegistry.register("survivor", SurvivorReward())
        RewardRegistry.register("crystals", CurrencyReward())
        RewardRegistry.register("permission", PermissionReward())
        RewardRegistry.register("advancement", AdvancementReward(advancementHook))

        // Init providers
        levelProvider = LevelProviderImpl(this)
        experienceProvider = ExperienceProviderImpl(this)

        // Register services to Bukkit ServicesManager
        server.servicesManager.register(
            LevelProvider::class.java,
            levelProvider,
            this,
            ServicePriority.Normal
        )

        server.servicesManager.register(
            ExperienceProvider::class.java,
            experienceProvider,
            this,
            ServicePriority.Normal
        )

        // Register Commands
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event: ReloadableRegistrarEvent<Commands> ->
            val registrar = event.registrar()
            registrar.register("level", "View your level", listOf("levels", "xp"), LevelCommand(this))
            registrar.register("leveladmin", "Admin commands for levels", LevelAdminCommand(this))
        }

        // Register Listeners
        server.pluginManager.registerEvents(ExperienceListener(this), this)
        server.pluginManager.registerEvents(AdvancementHookListener(this), this)

        // Register PAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            MistakenLevelExpansion(this).register()
            logger.info("[MistakenDeluxe-Level-Addon] Registered PlaceholderAPI Expansion.")
        }

        logger.info("[MistakenDeluxe-Level-Addon] Successfully registered Level & Experience API Services.")
    }

    override fun onDisable() {
        logger.info("[MistakenDeluxe-Level-Addon] Shutting down...")
        
        // Save data
        if (this::manager.isInitialized) {
            manager.saveAllSync()
        }

        // Unregister services automatically handled by Bukkit, but good to be explicit for our own cleanup
        server.servicesManager.unregisterAll(this)

        if (this::databaseProvider.isInitialized) {
            databaseProvider.close()
        }
    }
}
