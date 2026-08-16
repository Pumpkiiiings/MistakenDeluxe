package liric.mistaken.roles.survivors

import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.clases.*
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

import liric.mistaken.roles.shared.AbstractRoleManager


class SurvivorManager(plugin: Mistaken) : AbstractRoleManager<Survivor>(plugin) {

    init {
        // Registro de Clases (Singletons)
        // Aquí agregas las demás clases cuando las tengas listas (Jesse, Petra, etc.)
        listOf(
            Civilian(),
            DeliveryMan(),
            Minty(),
            RaincoatKid(),
            Jesse(),
            Villager(),
            Notch(),
            Troll()
        ).forEach { registerClass(it) }
    }

    override fun registerClass(superviviente: Survivor) {
        val config = plugin.configManager.getSurvivorConfig(superviviente.id)
        if (config.getBoolean("enabled", true)) {
            availableClasses[superviviente.id.lowercase()] = superviviente
        }
    }

    /**
     * ?? REGISTRO OPTIMIZADO (Paper 1.21.4+):
     * Usamos 'player.scheduler'. Si el jugador se desconecta antes de los 5 ticks,
     * la tarea se cancela sola automáticamente.
     */
    fun registrarSurvivor(player: Player, clase: Survivor) {
        val uuid = player.uniqueId

        // 1. Asignación inmediata en RAM
        activeRoles[uuid] = clase

        // 2. Tarea diferida anclada a la entidad (Safe)
        // Se ejecuta 5 ticks (250ms) después para asegurar que el inventario esté listo
        player.scheduler.runDelayed(plugin, { task ->
            
            if (activeRoles[uuid] == clase) {
                clase.equip(player)

                player.updateInventory()

                plugin.componentLogger.info(ColorTranslator.translate(
                    "<gray>[Survivor]</gray> <white>${player.name}</white> <green>equipado como ${clase.nombre}</green>"
                ))

                // Feedback al jugador
                player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "game.class-selected",
                    Placeholder.component("class", ColorTranslator.translate(clase.nombre))))
            }
        }, null, 5L)
    }

    /**
     * Remueve al superviviente.
     */
    fun removerSurvivor(player: Player) {
        removeRoleLogic(player.uniqueId, player)
    }

    fun removerSurvivor(uuid: UUID) {
        val player = Bukkit.getPlayer(uuid)
        removeRoleLogic(uuid, player)
    }

    override fun removeRoleLogic(uuid: UUID, player: Player?) {
        val clase = activeRoles.remove(uuid) ?: return

        if (player != null && player.isOnline) {
            // ?? FOLIA FIX: Modificar inventario/efectos DEBE hacerse en el hilo de la entidad
            player.scheduler.run(plugin, { _ ->
                // 1. Limpieza lógica de la clase
                clase.cleanup(player)

                // 2. Limpieza física
                player.inventory.clear()
                player.inventory.armorContents = arrayOfNulls(4)

                // Limpieza de pociones eficiente
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }

                player.isSwimming = false
                player.walkSpeed = 0.2f
            }, null)
        } else {
            // Si está offline, solo limpiamos la lógica interna de la clase (si aplica)
            clase.cleanup(null)
        }
    }

    override fun cleanAll() {
        super.cleanAll()
        plugin.componentLogger.info(ColorTranslator.translate("[INFO] [Manager] Survivor cleanup completed."))
    }

    // --- GETTERS ---
    fun esSurvivorActivo(player: Player?): Boolean = player?.let { activeRoles.containsKey(it.uniqueId) } ?: false
    fun getSurvivorClass(player: Player?): Survivor? = player?.let { activeRoles[it.uniqueId] }
    fun getAvailableClasses(): Map<String, Survivor> = availableClasses
}

