package liric.mistaken.roles.survivors

import liric.mistaken.Mistaken
import liric.mistaken.roles.survivors.classes.*
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
        // Registro de Classes (Singletons)
        // Aqu� agregas las dem�s classes cuando las tengas listas (Jesse, Petra, etc.)
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

    override fun registerClass(survivor: Survivor) {
        val config = plugin.configManager.getSurvivorConfig(survivor.id)
        if (config.getBoolean("enabled", true)) {
            availableClasses[survivor.id.lowercase()] = survivor
        }
    }

    /**
     * ?? REGISTRO OPTIMIZADO (Paper 1.21.4+):
     * Usamos 'player.scheduler'. Si el player se desconecta antes de los 5 ticks,
     * la tarea se cancela sola autom�ticamente.
     */
    fun registrarSurvivor(player: Player, clase: Survivor) {
        val uuid = player.uniqueId

        // 1. Asignaci�n inmediata en RAM
        activeRoles[uuid] = clase

        // 2. Tarea diferida anclada a la entidad (Safe)
        // Se ejecuta 5 ticks (250ms) despu�s para asegurar que el inventario est� listo
        player.scheduler.runDelayed(plugin, { task ->
            
            if (activeRoles[uuid] == clase) {
                clase.equip(player)

                // Apply vida m�xima espec�fica del survivor
                val config = plugin.configManager.getSurvivorConfig(clase.id)
                val maxHealth = config.getDouble("stats.health", 20.0)
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = maxHealth
                player.health = maxHealth

                player.updateInventory()

                plugin.componentLogger.info(ColorTranslator.translate(
                    "<gray>[Survivor]</gray> <white>${player.name}</white> <green>equipado como ${clase.nombre}</green>"
                ))

                // Feedback al player
                player.sendMessage(PumpkingServiceManager.messages.getComponent(player, "game.class-selected",
                    Placeholder.component("class", ColorTranslator.translate(clase.nombre))))
            }
        }, null, 5L)
    }

    /**
     * Remueve al survivor.
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
                // 1. Limpieza l�gica de la clase
                clase.cleanup(player)

                // 2. Limpieza f�sica
                player.inventory.clear()
                player.inventory.armorContents = arrayOfNulls(4)
                
                // Restaurar vida m�xima por defecto (20.0 = 10 corazones)
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 20.0

                // Limpieza de pociones eficiente
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }

                player.isSwimming = false
                player.walkSpeed = 0.2f
            }, null)
        } else {
            // Si est� offline, solo limpiamos la l�gica interna de la clase (si aplica)
            clase.cleanup(null)
        }
    }

    override fun cleanAll() {
        super.cleanAll()
        plugin.componentLogger.info(pumpking.lib.color.ColorTranslator.translate("<blue>[INFO]</blue> <gray>Survivor cleanup completed.</gray>"))
    }

    // --- GETTERS ---
    fun esSurvivorActivo(player: Player?): Boolean = player?.let { activeRoles.containsKey(it.uniqueId) } ?: false
    fun getSurvivorClass(player: Player?): Survivor? = player?.let { activeRoles[it.uniqueId] }
    fun getAvailableClasses(): Map<String, Survivor> = availableClasses
}

