package liric.mistaken.roles.killers

import liric.mistaken.Mistaken
import liric.mistaken.roles.killers.clases.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import java.util.UUID
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import liric.mistaken.api.managers.IKillerManager
import org.bukkit.Material
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

import liric.mistaken.roles.shared.AbstractRoleManager


class KillerManager(plugin: Mistaken) : AbstractRoleManager<Killer>(plugin), IKillerManager {

    init {
        listOf(
            Slasher(), Herobrine(), Entity303(), NullAsesino(),
            ColorAndElectricity(), CharlieInferno(), CharlieJazz(), Romeo(), Mariachi(),
            Sowoul(), TinkyWinky(), StillLife(), WardenKiller()
        ).forEach { registerClass(it) }
    }

    override fun registerClass(asesino: Killer) {
        val config = plugin.configManager.getKillerConfig(asesino.id)
        if (config.getBoolean("enabled", true)) {
            availableClasses[asesino.id.lowercase()] = asesino
        }
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
        activeRoles[uuid] = asesino

        // Feedback
        player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "killer.transform",
            Placeholder.component("name", ColorTranslator.translate(asesino.nombre))))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)

        // 2. ?? FIX: EntityScheduler de Paper con runDelayed y Consumer expl�cito
        player.scheduler.runDelayed(
            plugin,
            Consumer { _ ->
                if (!player.isOnline || !activeRoles.containsKey(uuid)) return@Consumer

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
        removeRoleLogic(player.uniqueId, player)
    }

    override fun removeRoleLogic(uuid: UUID, player: Player?) {
        val asesino = activeRoles.remove(uuid) ?: return

        // Limpiamos los datos del Killer
        asesino.cleanup(player)

        if (player != null && player.isOnline) {
            
            player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
            player.health = 20.0
            player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
            player.isGlowing = false
            player.isSwimming = false
        }
    }

    fun removeAllKillers() {
        cleanAll()
        // Le decimos a todos los asesinos que vacíen su memoria RAM interna
        availableClasses.values.forEach { asesino ->
            asesino.clearGlobalData()
        }
    }

    // --- GETTERS ---
    fun getKillerOfPlayer(player: Player?): Killer? = player?.let { activeRoles[it.uniqueId] }
    fun isKiller(player: Player?): Boolean = player?.let { activeRoles.containsKey(it.uniqueId) } ?: false
    fun getAvailableClasses(): Map<String, Killer> = availableClasses

    override fun shutdown() {
        removeAllKillers()
    }
}

