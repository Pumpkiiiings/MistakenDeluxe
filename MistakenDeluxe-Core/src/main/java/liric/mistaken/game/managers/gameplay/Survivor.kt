package liric.mistaken.game.managers.gameplay

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import kotlinx.coroutines.Job
import liric.mistaken.Mistaken
import liric.mistaken.api.util.Sounds
import liric.mistaken.config.engine.core.MessageService
import liric.mistaken.scripting.effects.triggers.TriggerRegistry
import liric.mistaken.utils.color.ColorTranslator
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

abstract class Survivor(override val id: String, override val name: String) : liric.mistaken.api.roles.ISurvivor {

    protected val plugin = Mistaken.instance
    protected val mm = plugin.mm

    val triggerRegistry = TriggerRegistry(this.id)

    private val cooldowns = ConcurrentHashMap<String, Long>()

    protected val activeJobs = ConcurrentHashMap.newKeySet<Job>()
    protected val playerJobs = ConcurrentHashMap<UUID, MutableSet<Job>>()
    protected val activeTasks = ConcurrentHashMap<UUID, MutableSet<ScheduledTask>>()

    open fun trackearHeridos(player: Player) {}

    fun checkCooldown(player: Player, slot: Int, seconds: Int): Boolean {
        if (seconds <= 0) return false

        val key = "${player.uniqueId}_$slot"
        val now = System.currentTimeMillis()
        val expireTime = cooldowns.getOrDefault(key, 0L)

        if (now < expireTime) {
            val remaining = (expireTime - now) / 1000.0
            player.sendActionBar(ColorTranslator.translate("<red>Cooldown: <white>${"%.1f".format(remaining)}s</white>"))
            return true
        }

        cooldowns[key] = now + (seconds * 1000L)
        return false
    }

    protected fun trackJob(job: Job, ownerUuid: UUID? = null) {
        if (ownerUuid != null) {
            val set = playerJobs.computeIfAbsent(ownerUuid) { ConcurrentHashMap.newKeySet() }
            set.add(job)
            job.invokeOnCompletion { set.remove(job) }
        } else {
            activeJobs.add(job)
            job.invokeOnCompletion { activeJobs.remove(job) }
        }
    }

    protected fun trackTask(ownerUuid: UUID, task: ScheduledTask?) {
        if (task == null) return
        activeTasks.computeIfAbsent(ownerUuid) { ConcurrentHashMap.newKeySet() }.add(task)
    }

    protected fun equipFromConfig(
        player: Player,
        includeArmor: Boolean = true,
        armorFallbacks: Map<String, Material> = emptyMap(),
        itemFallbacks: Map<String, Material> = emptyMap(),
        itemCustomizer: ((String, ItemStack) -> Unit)? = null
    ) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val langInfo = MessageService.getSpecificFile(player, "survivors_info")
        val config = plugin.configManager.getSurvivorConfig(this.id)
        val langBasePath = "survivors.${this.id}"

        if (includeArmor) {
            listOf("helmet", "chestplate", "leggings", "boots").forEach { key ->
                RoleItemUtils.deliverConfigItem(
                    inv, config, langInfo, langBasePath, key, 0,
                    isArmor = true,
                    fallback = armorFallbacks[key]
                )
            }
        }

        listOf("skill1" to 0, "skill2" to 1, "skill3" to 2).forEach { (key, slot) ->
            RoleItemUtils.deliverConfigItem(
                inv, config, langInfo, langBasePath, key, slot,
                isArmor = false,
                fallback = itemFallbacks[key],
                customizer = itemCustomizer?.let { fn -> { item -> fn(key, item) } }
            )
        }

        player.updateInventory()
    }

    protected fun sendAbilityMessage(
        player: Player,
        lang: FileConfiguration,
        mech: FileConfiguration,
        key: String,
        defaultSound: String? = null
    ) {
        val rawMsg = lang.getString("survivors.${this.id}.habilidades_mensajes.$key")
        if (!rawMsg.isNullOrEmpty()) {
            val clean = rawMsg.replace("<prefix>", "", true).replace("%prefix%", "", true).trim()
            if (clean.isNotEmpty()) player.sendMessage(ColorTranslator.translate(clean))
        }
        if (defaultSound != null) {
            val soundName = mech.getString("survivors.${this.id}.items.${key}_sound", defaultSound)
            Sounds.orNull(soundName)?.let { player.playSound(player.location, it, 1f, 1f) }
        }
    }

    open override fun cleanup(player: Player?) {
        if (player == null) {
            activeJobs.toList().forEach { it.cancel() }
            activeJobs.clear()
            playerJobs.values.toList().forEach { set -> set.toList().forEach { it.cancel() } }
            playerJobs.clear()
            activeTasks.values.toList().forEach { set -> set.toList().forEach { it.cancel() } }
            activeTasks.clear()
            return
        }

        triggerRegistry.clearCooldowns(player.uniqueId)
        playerJobs.remove(player.uniqueId)?.toList()?.forEach { it.cancel() }
        activeTasks.remove(player.uniqueId)?.toList()?.forEach { it.cancel() }

        val prefix = "${player.uniqueId}_"
        cooldowns.keys.removeIf { it.startsWith(prefix) }

        if (player.isOnline) {
            player.inventory.clear()
            player.inventory.armorContents = arrayOfNulls(4)
            player.activePotionEffects.toList().forEach { effect ->
                player.removePotionEffect(effect.type)
            }
            player.updateInventory()
        }
    }

    open fun onTrigger(player: Player, triggerId: String) {}

    abstract override fun equip(player: Player)
    abstract override fun useSkill(player: Player, slot: Int)
}
