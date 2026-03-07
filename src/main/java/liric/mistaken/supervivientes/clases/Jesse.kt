package liric.mistaken.supervivientes.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import kotlinx.coroutines.*
import liric.mistaken.Mistaken
import liric.mistaken.supervivientes.Superviviente
import liric.mistaken.utils.CraftEngineUtils
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * [LIRIC-MISTAKEN 2.0]
 * Jesse (MCSM): El Héroe Protector.
 * MECÁNICAS:
 * - Dash de Impacto (Empuje + Daño).
 * - Puñetazo Pesado (Ceguera + Nausea 6s).
 * - Bloqueo Sónico (Rayo Warden, KB masivo).
 */
class Jesse : Superviviente(
    "jesse",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.jesse.nombre", "Jesse", "supervivientes_info")
) {

    private val pathBase = "supervivientes.jesse"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Llave para el puñetazo melee
    val MELEE_PUNCH_KEY = NamespacedKey("mistaken", "jesse_punch")

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 20))) {
                usarHeroDash(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> { /* H2: Puñetazo (Listener Melee) */ }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 35))) {
                usarBloqueoSonic(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        var msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))

        val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_PLAYER_ATTACK_SWEEP")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
        val configMecanica = plugin.configManager.getSupervivientes()

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
            else configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val meta = item.itemMeta
            if (key == "habilidad2") meta.persistentDataContainer.set(MELEE_PUNCH_KEY, PersistentDataType.BYTE, 1.toByte())

            langInfo.getString("$pathBase.habilidades_nombres.$key")?.let {
                meta.displayName(mm.deserialize(it))
            }
            item.itemMeta = meta

            if (isArmor) {
                when(key) {
                    "casco" -> inv.helmet = item
                    "pechera" -> inv.chestplate = item
                    "pantalones" -> inv.leggings = item
                    "botas" -> inv.boots = item
                }
            } else {
                inv.setItem(slot, item)
            }
        }

        deliver("casco", 0, true); deliver("pechera", 0, true)
        deliver("pantalones", 0, true); deliver("botas", 0, true)
        deliver("habilidad1", 0); deliver("habilidad2", 1); deliver("habilidad3", 2)

        player.updateInventory()
    }

    // --- H1: HERO DASH (Daño al impactar) ---
    private fun usarHeroDash(player: Player) {
        val dir = player.location.direction.normalize().multiply(2.2).setY(0.3)
        player.velocity = dir

        val job = scope.launch {
            var count = 0
            val hitted = mutableSetOf<java.util.UUID>()

            while (isActive && count < 10 && player.isOnline) {
                withContext(plugin.bukkitDispatcher) {
                    // Sonido y viento continuo
                    player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f)

                    val pos = Vector3d(player.location.x, player.location.y + 1.0, player.location.z)
                    val particle = WrapperPlayServerParticle(Particle(ParticleTypes.CLOUD), false, pos, Vector3f(0.3f, 0.3f, 0.3f), 0.05f, 5)
                    PacketEvents.getAPI().playerManager.sendPacket(player, particle)

                    // Chocar contra el asesino
                    player.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>().forEach { victim ->
                        if (plugin.gameManager.esAsesino(victim.uniqueId) && !hitted.contains(victim.uniqueId)) {
                            hitted.add(victim.uniqueId)

                            plugin.gameManager.combatManager.takeDamage(victim) // Hace daño

                            val knockback = player.location.direction.multiply(1.5).setY(0.4)
                            victim.velocity = knockback

                            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                            victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))

                            victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                        }
                    }
                }
                delay(50)
                count++
            }
        }
        trackJob(job)
    }

    // --- H2: PUÑETAZO (Lógica en Listener) ---
    fun aplicarGolpePuno(victim: Player) {
        victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 120, 0)) // 6s
        victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 120, 1))    // 6s
        victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f)
    }

    // --- H3: BLOQUEO SÓNICO (Warden Boom) ---
    private fun usarBloqueoSonic(player: Player) {
        val startLoc = player.eyeLocation
        val direction = startLoc.direction.normalize()

        player.playSound(player.location, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1f)

        val job = scope.launch {
            delay(1500) // 1.5s de carga (como el Warden real)

            withContext(plugin.bukkitDispatcher) {
                if (!player.isOnline) return@withContext

                player.world.playSound(player.location, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1f)

                var currentLoc = startLoc.clone()
                var hitTarget = false

                for (i in 0..15) { // Distancia de 15 bloques
                    currentLoc.add(direction)

                    // Rayo visual del Warden usando PacketEvents
                    val pos = Vector3d(currentLoc.x, currentLoc.y, currentLoc.z)
                    val particle = WrapperPlayServerParticle(Particle(ParticleTypes.SONIC_BOOM), false, pos, Vector3f(), 0f, 1)
                    player.world.players.forEach { PacketEvents.getAPI().playerManager.sendPacket(it, particle) }

                    // Impacto
                    if (!hitTarget) {
                        currentLoc.world.getNearbyPlayers(currentLoc, 1.5).forEach { victim ->
                            if (plugin.gameManager.esAsesino(victim.uniqueId)) {
                                val kb = direction.clone().multiply(2.5).setY(0.5)
                                victim.velocity = kb

                                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2)) // 2s
                                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 1))  // 5s

                                hitTarget = true
                            }
                        }
                    }
                }
            }
        }
        trackJob(job)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        scope.coroutineContext.cancelChildren()
    }
}
