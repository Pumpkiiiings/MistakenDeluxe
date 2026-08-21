package liric.mistaken.roles.killers

import liric.mistaken.Mistaken
import liric.mistaken.roles.killers.triggers.TriggerRegistry

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import liric.mistaken.utils.color.ColorTranslator
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.utils.hooks.CraftEngine

abstract class CoreKiller(id: String, nombre: String) : Killer(id, nombre) {
    protected val plugin: Mistaken
        get() = Mistaken.instance

    val triggerRegistry = TriggerRegistry(this.id)

    open override fun cleanup(player: Player?) {
        player?.let {
            triggerRegistry.clearCooldowns(it.uniqueId)
        }
        if (player == null) {
            dispose()
        }
    }

    /**
     * Nuevo sistema de Triggers. Los Killers nativos pueden hacer override de esto.
     */
    open fun onTrigger(player: Player, triggerId: String) {}

    /**
     * Interceptaci�n de chat para Killers nativos. 
     * Retorna un string para reescribir el broadcast, o null para no tocarlo.
     */
    open fun onInterceptChat(player: Player, message: String): String? { return null }

    /**
     * Se llama cuando el killer mata a un player (lo pone en espectador).
     */
    open fun onKill(killer: Player, victim: Player) {}

    open override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val langInfo = MessageService.getSpecificFile(player, "killers_info")
        val configMecanica = plugin.configManager.getKillerConfig(this.id)
        
        // Cargar triggers desde YAML (si existe la secci�n)
        triggerRegistry.loadFromConfig(configMecanica)

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val itemId = configMecanica.getString("armor.$key") ?: configMecanica.getString("items.$key")
            if (itemId == null || itemId == "none") return

            val item = CraftEngine.getCustomItem(itemId) ?: run {
                val mat = Material.matchMaterial(itemId.replace(".*:".toRegex(), "").uppercase())
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "weapon") "asesinos.${this.id}.skill_names.weapon"
            else "asesinos.${this.id}.skill_names.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(ColorTranslator.translate(it)) }
            }

            if (isArmor) {
                when(key) {
                    "helmet" -> inv.helmet = item
                    "chestplate" -> inv.chestplate = item
                    "leggings" -> inv.leggings = item
                    "boots" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        deliver("helmet", 0, true); deliver("chestplate", 0, true)
        deliver("leggings", 0, true); deliver("boots", 0, true)
        deliver("skill1", 1); deliver("skill2", 2)
        deliver("skill3", 3); deliver("skill4", 4)
        deliver("weapon", 8)
    }

    /**
     * Utilidad para obtener el item de arma base si el script lo necesita (ej: lanzarlo)
     */
    protected fun getWeaponItem(): ItemStack {
        val configMecanica = plugin.configManager.getKillerConfig(this.id)
        val itemId = configMecanica.getString("items.weapon") ?: return ItemStack(Material.IRON_SWORD)
        return CraftEngine.getCustomItem(itemId) ?: run {
            val mat = Material.matchMaterial(itemId.replace(".*:".toRegex(), "").uppercase())
            if (mat != null) ItemStack(mat) else ItemStack(Material.IRON_SWORD)
        }
    }

    // ----------------------------------------------------
    // RESOURCE TRACKING
    // ----------------------------------------------------
    private var isDisposed = false
    private val taskTracker = liric.mistaken.roles.killers.tracking.KillerTaskTracker()
    private val eventTracker = liric.mistaken.roles.killers.tracking.KillerEventTracker()
    private val resourceTracker = liric.mistaken.roles.killers.tracking.KillerResourceTracker()

    fun trackResource(cleanupAction: Runnable) {
        if (isDisposed) return
        resourceTracker.track(cleanupAction)
    }

    // ----------------------------------------------------
    // EVENT TRACKING (DSL)
    // ----------------------------------------------------
    fun <T : org.bukkit.event.Event> onEvent(
        eventClass: Class<T>,
        priority: org.bukkit.event.EventPriority = org.bukkit.event.EventPriority.NORMAL,
        ignoreCancelled: Boolean = true,
        action: java.util.function.Consumer<T>
    ) {
        if (isDisposed) return
        val listener = object : org.bukkit.event.Listener {}
        plugin.server.pluginManager.registerEvent(eventClass, listener, priority, { _, event ->
            if (eventClass.isInstance(event)) {
                action.accept(eventClass.cast(event))
            }
        }, plugin, ignoreCancelled)
        eventTracker.track(listener)
    }

    // ----------------------------------------------------
    // SCHEDULER TRACKING (DSL)
    // ----------------------------------------------------
    fun runTimer(
        entity: org.bukkit.entity.Entity, 
        delayTicks: Long, 
        periodTicks: Long, 
        task: java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>
    ) {
        if (isDisposed) return
        val t = entity.scheduler.runAtFixedRate(plugin, task, null, delayTicks, periodTicks)
        taskTracker.track(t)
    }

    fun runDelayed(
        entity: org.bukkit.entity.Entity, 
        delayTicks: Long, 
        task: java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>
    ) {
        if (isDisposed) return
        val t = entity.scheduler.runDelayed(plugin, task, null, delayTicks)
        taskTracker.track(t)
    }

    fun runGlobalTimer(
        delayTicks: Long, 
        periodTicks: Long, 
        task: java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>
    ) {
        if (isDisposed) return
        val t = org.bukkit.Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task, delayTicks, periodTicks)
        taskTracker.track(t)
    }
    
    fun runGlobalDelayed(
        delayTicks: Long, 
        task: java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>
    ) {
        if (isDisposed) return
        val t = org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task, delayTicks)
        taskTracker.track(t)
    }

    fun runRegionTimer(
        location: org.bukkit.Location, 
        delayTicks: Long, 
        periodTicks: Long, 
        task: java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>
    ) {
        if (isDisposed) return
        val t = org.bukkit.Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, task, delayTicks, periodTicks)
        taskTracker.track(t)
    }
    
    fun runRegionDelayed(
        location: org.bukkit.Location, 
        delayTicks: Long, 
        task: java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>
    ) {
        if (isDisposed) return
        val t = org.bukkit.Bukkit.getRegionScheduler().runDelayed(plugin, location, task, delayTicks)
        taskTracker.track(t)
    }

    // ----------------------------------------------------
    // LIFECYCLE
    // ----------------------------------------------------
    final override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        super.dispose()
        
        onDispose()
        
        taskTracker.dispose()
        eventTracker.dispose()
        resourceTracker.dispose()
    }
    
    protected open fun onDispose() {}
}
