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
import liric.mistaken.data.db.HikariDatabaseManager
import liric.mistaken.data.db.DatabaseType
import org.bukkit.Bukkit
import java.io.File
import liric.mistaken.level.config.MenuConfig
import liric.mistaken.level.config.XpSourcesConfig
import liric.mistaken.level.menu.ProgressionMenu
import org.bukkit.configuration.file.YamlConfiguration

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

    lateinit var messagesConfig: YamlConfiguration
        private set

    private lateinit var databaseProvider: HikariDatabaseManager

    override fun onEnable() {
        componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Starting up...</gray>"))

        
        saveDefaultConfig()
        saveResource("levels.yml", false)
        
        saveResource("messages.yml", false)
        try { saveResource("xp_sources.yml", false) } catch (e: Exception) {}

        levelConfig = LevelConfig(this)
        levelConfig.load()

        xpSourcesConfig = XpSourcesConfig(this)
        xpSourcesConfig.load()

        menuConfig = MenuConfig(this)
        menuConfig.load()

        val file = File(dataFolder, "messages.yml")
        if (!file.exists()) {
            saveResource("messages.yml", false)
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file)

        
        
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

        
        val advancementHook = UltimateAdvancementHook(this)

        
        manager = LevelManager(this)

        
        RewardRegistry.register("message", MessageReward())
        RewardRegistry.register("actionbar", ActionBarReward())
        RewardRegistry.register("title", TitleReward())
        RewardRegistry.register("command", CommandReward())
        RewardRegistry.register("killer", KillerReward())
        RewardRegistry.register("survivor", SurvivorReward())
        RewardRegistry.register("crystals", CurrencyReward())
        RewardRegistry.register("permission", PermissionReward())
        RewardRegistry.register("advancement", AdvancementReward(advancementHook))

        
        levelProvider = LevelProviderImpl(this)
        experienceProvider = ExperienceProviderImpl(this)

        
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

        
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event: ReloadableRegistrarEvent<Commands> ->
            val registrar = event.registrar()
            registrar.register("level", "View your level", listOf("levels", "xp"), LevelCommand(this))
            registrar.register("leveladmin", "Admin commands for levels", LevelAdminCommand(this))
        }

        
        server.pluginManager.registerEvents(ExperienceListener(this), this)
        server.pluginManager.registerEvents(AdvancementHookListener(this), this)

        
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            MistakenLevelExpansion(this).register()
            componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Registered PlaceholderAPI Expansion.</gray>"))
        }

        componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<green>[SUCCESS]</green> <gray>Successfully registered Level & Experience API Services.</gray>"))
    }

    override fun onDisable() {
        componentLogger.info(liric.mistaken.utils.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Shutting down...</gray>"))
        
        
        if (this::manager.isInitialized) {
            manager.saveAllSync()
        }

        
        server.servicesManager.unregisterAll(this)

        if (this::databaseProvider.isInitialized) {
            databaseProvider.close()
        }
    }
}
