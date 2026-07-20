package liric.mistaken.roles.killers

import liric.mistaken.Mistaken
import liric.mistaken.roles.killers.clases.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import liric.mistaken.api.managers.IKillerManager
import org.bukkit.Material
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

/**
 *[LIRIC-MISTAKEN 2.0]
 * KillerManager: El mero jefe de los malos.
 * FIX: Memory Leaks parchados (Soporte EntityScheduler nativo sin Corrutinas).
 */
class KillerManager(private val plugin: Mistaken) : IKillerManager {

    private val mm = plugin.mm

    val activeKillers = ConcurrentHashMap<UUID, Killer>()
    private val availableClasses = ConcurrentHashMap<String, Killer>()

    override val catalogo: Map<String, Killer> get() = availableClasses

    init {
        listOf(
            Slasher(), Herobrine(), Entity303(), NullAsesino(),
            ColorAndElectricity(), CharlieInferno(), Romeo(), Mariachi(),
            Sowoul ()
        ).forEach { registerClass(it) }
    }

    override fun registerClass(asesino: Killer) {
        availableClasses[asesino.id.lowercase()] = asesino
    }

    fun updateKiller(player: Player, claseId: String) {
        if (claseId.equals("none", ignoreCase = true)) {
            removeKiller(player)
            return
        }
        val clase = getClassById(claseId) ?: return

        // ?? FIX: Ejecutamos el cleanup de forma segura en el hilo del jugador (Entity Scheduler)
        player.scheduler.run(plugin, Consumer { _ ->
            clase.cleanup(player)
            plugin.componentLogger.info(ColorTranslator.translate("[INFO] [Manager] ${player.name} synchronized with ${clase.nombre}"))
        }, null)
    }

    fun registerKiller(player: Player, asesino: Killer) {
        val uuid = player.uniqueId

        // 1. Limpieza total inmediata (Hilo Principal)
        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)
        activeKillers[uuid] = asesino

        // Feedback
        player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "killer.transform",
            Placeholder.component("name", ColorTranslator.translate(asesino.nombre))))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)

        // 2. ?? FIX: EntityScheduler de Paper con runDelayed y Consumer expl�cito
        player.scheduler.runDelayed(
            plugin,
            Consumer { _ ->
                if (!player.isOnline || !activeKillers.containsKey(uuid)) return@Consumer

                asesino.equip(player)

                // ?? Reorganizaci�n din�mica de slots basada en config
                val config = plugin.configManager.getKillerConfig(asesino.id)
                val currentItems = (1..4).associateWith { player.inventory.getItem(it) }

                for (i in 1..4) {
                    val targetSlot = config.getInt("items.skill${i}_slot", i)
                    if (targetSlot != i) {
                        player.inventory.setItem(i, null)
                    }
                }
                for (i in 1..4) {
                    val targetSlot = config.getInt("items.skill${i}_slot", i)
                    val item = currentItems[i]
                    if (item != null && item.type != Material.AIR) {
                        player.inventory.setItem(targetSlot, item)
                    }
                }

                val weaponSlot = config.getInt("items.weapon_slot", 8)
                if (weaponSlot != 8) {
                    val weaponItem = player.inventory.getItem(8)
                    player.inventory.setItem(8, null)
                    player.inventory.setItem(weaponSlot, weaponItem)
                }

                asesino.showTrail(player)
                player.inventory.heldItemSlot = weaponSlot
            },
            null,
            15L // 15 ticks de retraso
        )
    }

    fun equipKiller(player: Player, claseId: String) {
        val clase = getClassById(claseId) ?: getClassById("slasher")
        clase?.let { registerKiller(player, it) }
    }

    fun removeKiller(player: Player) {
        val asesino = activeKillers.remove(player.uniqueId)

        // Limpiamos los datos del Killer
        asesino?.cleanup(player) ?: availableClasses.values.forEach { it.cleanup(player) }

        // Reset total de los fierros del jugador (Directo y r�pido)
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.health = 20.0
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.isGlowing = false
        player.isSwimming = false
    }

    fun removeAllKillers() {
        val iterador = activeKillers.keys.iterator()

        while (iterador.hasNext()) {
            val uuid = iterador.next()
            val player = plugin.server.getPlayer(uuid)

            if (player != null && player.isOnline) {
                removeKiller(player)
            } else {
                activeKillers[uuid]?.let {
                    plugin.componentLogger.warn(ColorTranslator.translate("[WARN] [Manager] Cleaning ghost data of disconnected assassin: $uuid"))
                }
            }
            iterador.remove()
        }

        activeKillers.clear()

        // Le decimos a todos los asesinos que vac�en su memoria RAM interna
        availableClasses.values.forEach { asesino ->
            asesino.clearGlobalData()
        }
    }

    // --- GETTERS ---
    override fun getClassById(id: String?): Killer? = id?.lowercase()?.let { availableClasses[it] }
    fun getKillerOfPlayer(player: Player?): Killer? = player?.let { activeKillers[it.uniqueId] }
    fun getCurrentKiller(): Player? = activeKillers.keys.firstOrNull()?.let { plugin.server.getPlayer(it) }?.takeIf { it.isOnline }
    fun isKiller(player: Player?): Boolean = player?.let { activeKillers.containsKey(it.uniqueId) } ?: false
    fun getAvailableClasses(): Map<String, Killer> = catalogo

    fun shutdown() {
        removeAllKillers()
    }
}


