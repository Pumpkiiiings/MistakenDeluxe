package liric.mistaken.roles.killers

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import liric.mistaken.Mistaken
import liric.mistaken.roles.common.triggers.TriggerRegistry
import liric.mistaken.roles.shared.RoleItemUtils
import liric.mistaken.utils.hooks.ObserverHook
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import liric.mistaken.config.engine.core.MessageService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

abstract class CoreKiller(id: String, name: String) : Killer(id, name) {
    protected val plugin: Mistaken
        get() = Mistaken.instance

    val triggerRegistry = TriggerRegistry(this.id)
    private val musicTasks = ConcurrentHashMap<UUID, ScheduledTask>()

    open override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            triggerRegistry.clearCooldowns(it.uniqueId)
            defaultMusic?.let { musicId -> stopLoopingMusic(it.uniqueId, musicId) }
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

        triggerRegistry.loadFromConfig(configMecanica)

        val langBasePath = "killers.${this.id}"
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "helmet", 0, isArmor = true)
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "chestplate", 0, isArmor = true)
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "leggings", 0, isArmor = true)
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "boots", 0, isArmor = true)
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "skill1", 1)
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "skill2", 2)
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "skill3", 3)
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "skill4", 4)
        RoleItemUtils.deliverConfigItem(inv, configMecanica, langInfo, langBasePath, "weapon", 8)

        inv.heldItemSlot = 8
        player.updateInventory()
        onEquipped(player)
    }

    /** Hook invoked after default config items are equipped and the weapon slot is focused. */
    protected open fun onEquipped(player: Player) {}

    /**
     * Utilidad para obtener el item de arma base si el script lo necesita (ej: lanzarlo)
     */
    protected fun getWeaponItem(): ItemStack {
        val configMecanica = plugin.configManager.getKillerConfig(this.id)
        val itemId = configMecanica.getString("items.weapon")
        return RoleItemUtils.resolveItem(itemId, Material.IRON_SWORD) ?: ItemStack(Material.IRON_SWORD)
    }

    /** Starts or restarts a looping positional theme sound tied to [player] while active as killer. */
    protected fun startLoopingMusic(
        player: Player,
        soundId: String,
        volume: Float = 2.0f,
        maxDistSq: Double? = null,
        periodTicks: Long = 1480L
    ) {
        val uuid = player.uniqueId
        stopLoopingMusic(uuid, soundId)

        val task = player.scheduler.runAtFixedRate(plugin, Consumer { t ->
            if (!player.isOnline || !plugin.killerManager.isKiller(player)) {
                stopLoopingMusic(uuid, soundId)
                t.cancel()
                return@Consumer
            }
            val recipients = if (maxDistSq != null) {
                player.world.players.filter { it.location.distanceSquared(player.location) < maxDistSq }
            } else {
                Bukkit.getOnlinePlayers()
            }
            recipients.forEach { p ->
                ObserverHook.stopSound(p, soundId)
                ObserverHook.playEntitySound(p, soundId, player, volume, 1.0f)
            }
        }, null, 1L, periodTicks)

        if (task != null) {
            musicTasks[uuid] = task
        }
    }

    /** Cancels any active theme loop for [uuid] and stops [soundId] for online players. */
    protected fun stopLoopingMusic(uuid: UUID, soundId: String) {
        musicTasks.remove(uuid)?.cancel()
        Bukkit.getOnlinePlayers().forEach { ObserverHook.stopSound(it, soundId) }
    }

    
    
    
    private var isDisposed = false
    private val taskTracker = liric.mistaken.roles.killers.tracking.KillerTaskTracker()
    private val eventTracker = liric.mistaken.roles.killers.tracking.KillerEventTracker()
    private val resourceTracker = liric.mistaken.roles.killers.tracking.KillerResourceTracker()

    fun trackResource(cleanupAction: Runnable) {
        if (isDisposed) return
        resourceTracker.track(cleanupAction)
    }

    
    
    
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

    
    
    
    final override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        super.dispose()
        
        musicTasks.values.forEach { it.cancel() }
        musicTasks.clear()
        onDispose()
        
        taskTracker.dispose()
        eventTracker.dispose()
        resourceTracker.dispose()
    }
    
    protected open fun onDispose() {}
}
