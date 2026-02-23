package liric.mistaken.supervivientes.clases

import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.listeners.supervivientes.SupervivienteHabilidadListener
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.utils.CraftEngineUtils
import liric.mistaken.utils.mainThread
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0]
 * Repartidor: Soporte táctico y control de área.
 *
 * MEJORAS:
 * - Sistema Multi-Idioma (Nombres y mensajes por jugador).
 * - Carga de materiales desde la raíz con Fallback Vanilla.
 * - HashSet para derrames (0% lag de Metadata).
 */
class Repartidor : Superviviente(
    "repartidor",
    Mistaken.instance.messageConfig.getSpecificFile(null, "supervivientes").getString("supervivientes.repartidor.nombre", "Repartidor")!!
) {

    private val pathBase = "supervivientes.repartidor.items"
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pedidoKey = NamespacedKey("mistaken", "pedido")

    init {
        preLoadKit()
    }

    /**
     * 🔥 PRE-LOAD LÓGICO:
     * Carga los materiales base del archivo supervivientes.yml de la RAIZ.
     */
    private fun preLoadKit() {
        val config = plugin.configManager.getSupervivientesConfig(null)
        val skillKeys = listOf("habilidad1", "habilidad2", "habilidad3")

        skillKeys.forEach { key ->
            val id = config.getString("supervivientes.repartidor.items.$key")
            if (id != null && id != "none" && id.isNotEmpty()) {
                val customItem = CraftEngineUtils.getCustomItem(id)
                val item = if (customItem != null) {
                    customItem
                } else {
                    val mat = Material.matchMaterial(id) ?: Material.PAPER
                    ItemStack(mat)
                }
                itemCache[key] = item
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes")

        // Mapeo: Tecla 1 (Slot 0) a Tecla 3 (Slot 2)
        when (slot) {
            0 -> if (!checkCooldown(player, 0, langConfig.getInt("supervivientes.repartidor.items.habilidad1_cooldown", 30))) {
                usarBebidaEnergetica(player)
                sendAbilityMessage(player, langConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, langConfig.getInt("supervivientes.repartidor.items.habilidad2_cooldown", 25))) {
                lanzarPedido(player)
                sendAbilityMessage(player, langConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, langConfig.getInt("supervivientes.repartidor.items.habilidad3_cooldown", 15))) {
                usarDerrame(player)
                sendAbilityMessage(player, langConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, key: String) {
        val msg = lang.getString("supervivientes.repartidor.items.${key}_mensaje")
        if (!msg.isNullOrEmpty()) {
            player.sendMessage(mm.deserialize(msg))
        }
        val soundName = lang.getString("supervivientes.repartidor.items.${key}_sonido", "ENTITY_GENERIC_EAT")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    // --- 🛠️ EQUIPAMIENTO (CON TRADUCCIÓN AL VUELO) ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()

        if (itemCache.isEmpty()) preLoadKit()

        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes")

        fun giveLocalizedSkill(slot: Int, key: String) {
            val item = itemCache[key]?.clone() ?: return
            val name = langConfig.getString("supervivientes.repartidor.items.${key}_nombre")

            if (name != null) {
                item.editMeta { it.displayName(mm.deserialize(name)) }
            }
            inv.setItem(slot, item)
        }

        // Entregar habilidades en slots 0, 1, 2
        giveLocalizedSkill(0, "habilidad1")
        giveLocalizedSkill(1, "habilidad2")
        giveLocalizedSkill(2, "habilidad3")

        player.sendMessage(plugin.messageConfig.getMessage(player, "supervivientes.repartidor.equip-msg"))
        player.updateInventory()
    }

    // --- ⚡ LÓGICA DE HABILIDADES ---

    private fun usarBebidaEnergetica(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 120, 1))
        val job = scope.launch {
            delay(6000) // 6 segundos de efecto
            withContext(plugin.mainThread) {
                if (player.isOnline) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 80, 1))
                    player.playSound(player.location, Sound.ENTITY_PLAYER_BURP, 0.8f, 0.8f)
                }
            }
        }
        trackJob(job)
    }

    private fun lanzarPedido(player: Player) {
        player.launchProjectile(Snowball::class.java).apply {
            persistentDataContainer.set(pedidoKey, PersistentDataType.BYTE, 1.toByte())
        }
        player.playSound(player.location, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f)
    }

    private fun usarDerrame(player: Player) {
        val blockLoc = player.location.block.location

        // Marcamos el bloque en el HashSet global (SupervivienteHabilidadListener)
        SupervivienteHabilidadListener.marcarBloque(blockLoc)

        player.world.spawnParticle(Particle.ITEM_SLIME, player.location.add(0.0, 0.1, 0.0), 40, 0.5, 0.0, 0.5, 0.1)
        player.playSound(player.location, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.5f)

        val job = scope.launch {
            delay(10000) // 10 segundos
            withContext(plugin.mainThread) {
                SupervivienteHabilidadListener.desmarcarBloque(blockLoc)
                blockLoc.world.spawnParticle(Particle.DRIPPING_WATER, blockLoc.clone().add(0.5, 0.1, 0.5), 15, 0.2, 0.1, 0.2)
            }
        }
        trackJob(job)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        scope.coroutineContext.cancelChildren()
    }
}
