package liric.mistaken.level.integration

import liric.mistaken.level.LevelAddonPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import java.lang.reflect.Method

class UltimateAdvancementHook(private val plugin: LevelAddonPlugin) {

    private var isEnabled = false

    // Reflection objects
    private var uaaInstance: Any? = null
    private var createTabMethod: Method? = null
    private var registerAdvancementMethod: Method? = null
    private var grantMethod: Method? = null
    
    // Cache for custom advancements created via Mode B
    private val registeredAdvancements = mutableMapOf<String, Any>()

    init {
        if (Bukkit.getPluginManager().isPluginEnabled("UltimateAdvancementAPI")) {
            isEnabled = true
            plugin.logger.info("UltimateAdvancementAPI hooked via reflection.")
            try {
                setupReflection()
                registerConfiguredAdvancements()
            } catch (e: Exception) {
                plugin.logger.warning("Failed to initialize UltimateAdvancementAPI hook: ${e.message}")
            }
        }
    }

    private fun setupReflection() {
        val uaaClass = Class.forName("com.frengor.ultimateadvancementapi.UltimateAdvancementAPI")
        val getInstanceMethod = uaaClass.getMethod("getInstance", org.bukkit.plugin.Plugin::class.java)
        uaaInstance = getInstanceMethod.invoke(null, plugin)

        createTabMethod = uaaClass.getMethod("createAdvancementTab", String::class.java)

        val tabClass = Class.forName("com.frengor.ultimateadvancementapi.advancement.AdvancementTab")
        val baseAdvancementClass = Class.forName("com.frengor.ultimateadvancementapi.advancement.BaseAdvancement")
        val advancementArrayClass = java.lang.reflect.Array.newInstance(baseAdvancementClass, 0).javaClass
        
        // Wait, registering an advancement usually takes varargs or single. Let's find a method with 1 arg.
        registerAdvancementMethod = tabClass.methods.find { it.name == "registerAdvancements" }
        
        grantMethod = Class.forName("com.frengor.ultimateadvancementapi.advancement.Advancement")
            .getMethod("grant", Player::class.java)
    }

    private fun registerConfiguredAdvancements() {
        if (uaaInstance == null) return

        val config = plugin.levelConfig.config
        val advancementsSection = config.getConfigurationSection("advancements") ?: return

        try {
            val tab = createTabMethod?.invoke(uaaInstance, "mistaken_progression") ?: return

            val displayClass = Class.forName("com.frengor.ultimateadvancementapi.advancement.display.AdvancementDisplay")
            val frameTypeClass = Class.forName("com.frengor.ultimateadvancementapi.advancement.display.AdvancementFrameType")
            val taskFrame = frameTypeClass.getField("TASK").get(null)
            
            // public AdvancementDisplay(Material material, String title, AdvancementFrameType frame, boolean showToast, boolean announceToChat, float x, float y, String... description)
            val displayConstructor = displayClass.getConstructor(
                Material::class.java,
                String::class.java,
                frameTypeClass,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Array<String>::class.java
            )

            val baseAdvancementClass = Class.forName("com.frengor.ultimateadvancementapi.advancement.BaseAdvancement")
            // public BaseAdvancement(String key, AdvancementDisplay display, String backgroundTexture)
            val baseAdvConstructor = baseAdvancementClass.getConstructor(
                String::class.java,
                displayClass,
                String::class.java
            )

            // Register Root
            val rootDisplay = displayConstructor.newInstance(
                Material.DIAMOND, "Mistaken Progression", taskFrame, true, true, 0f, 0f, arrayOf("Mistaken levels and rewards")
            )
            val rootAdv = baseAdvConstructor.newInstance("mistaken_progression_root", rootDisplay, "textures/block/stone.png")
            
            // registerAdvancements takes varargs
            val rootArray = java.lang.reflect.Array.newInstance(baseAdvancementClass, 1)
            java.lang.reflect.Array.set(rootArray, 0, rootAdv)
            registerAdvancementMethod?.invoke(tab, rootArray)

            // Register Children
            for (key in advancementsSection.getKeys(false)) {
                val title = advancementsSection.getString("$key.title", key) ?: key
                val description = advancementsSection.getString("$key.description", "") ?: ""
                val iconStr = advancementsSection.getString("$key.icon", "GOLD_INGOT") ?: "GOLD_INGOT"
                val icon = Material.matchMaterial(iconStr.uppercase()) ?: Material.GOLD_INGOT

                val display = displayConstructor.newInstance(
                    icon, title, taskFrame, true, true, 1f, 0f, arrayOf(description)
                )
                
                val adv = baseAdvConstructor.newInstance(key, display, "textures/block/stone.png")
                
                val advArray = java.lang.reflect.Array.newInstance(baseAdvancementClass, 1)
                java.lang.reflect.Array.set(advArray, 0, adv)
                registerAdvancementMethod?.invoke(tab, advArray)
                
                registeredAdvancements[key] = adv
            }

        } catch (e: Exception) {
            plugin.logger.warning("Reflection error while setting up advancements: ${e.message}")
        }
    }

    fun grantAdvancement(player: Player, advancementId: String) {
        if (!isEnabled) return
        
        // Mode B: Try to grant from registered custom advancements
        val registered = registeredAdvancements[advancementId]
        if (registered != null && grantMethod != null) {
            try {
                grantMethod?.invoke(registered, player)
                return
            } catch (e: Exception) {
                plugin.logger.warning("Failed to grant custom advancement $advancementId: ${e.message}")
            }
        }

        // Mode A: Fallback to dispatch command for existing external advancements
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "uadv grant ${player.name} $advancementId")
    }
}
