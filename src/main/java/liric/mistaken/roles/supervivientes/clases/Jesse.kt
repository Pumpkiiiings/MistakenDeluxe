package liric.mistaken.roles.supervivientes.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import liric.mistaken.Mistaken
import liric.mistaken.roles.supervivientes.Superviviente
import liric.mistaken.utils.hooks.CraftEngineHook
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.function.Consumer

/**
 *[LIRIC-MISTAKEN 2.0]
 * Jesse (MCSM): El Héroe Protector.
 * FIX: Corrutinas eliminadas, Schedulers nativos aplicados.
 */
class Jesse : Superviviente(
    "jesse",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.jesse.nombre", "Jesse", "supervivientes_info")
) {

    private val pathBase = "supervivientes.jesse"
    val MELEE_PUNCH_KEY = NamespacedKey("mistaken", "jesse_punch")

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 20))) {
                usarHeroDash(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> { /* Lógica de puñetazo está en el Listener global */ }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 35))) {
                usarBloqueoSonic(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: FileConfiguration, mech: FileConfiguration, key: String) {
        player.scheduler.run(plugin, { _ ->
            val msg = lang.getString("$pathBase.habilidades_mensajes.$key")
            if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))

            val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_PLAYER_ATTACK_SWEEP")
            runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
        }, null)
    }

    override fun equipar(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val inv = player.inventory
            inv.clear()
            inv.armorContents = arrayOfNulls(4)

            val langInfo = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")
            val configMecanica = plugin.configManager.getSupervivientes()

            fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
                val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key")
                else configMecanica.getString("$pathBase.items.$key")

                if (id == null || id == "none") return

                val item = CraftEngineHook.getCustomItem(id) ?: run {
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
        }, null)
    }

    private fun usarHeroDash(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val dir = player.location.direction.normalize().multiply(2.2).setY(0.3)
            player.velocity = dir

            var count = 0
            val hitted = mutableSetOf<UUID>()

            player.scheduler.runAtFixedRate(plugin, Consumer { task ->
                if (count >= 10 || !player.isOnline) {
                    task.cancel()
                    return@Consumer
                }

                player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f)

                val pos = Vector3d(player.location.x, player.location.y + 1.0, player.location.z)
                val particle = WrapperPlayServerParticle(Particle(ParticleTypes.CLOUD), false, pos, Vector3f(0.3f, 0.3f, 0.3f), 0.05f, 5)
                PacketEvents.getAPI().playerManager.sendPacket(player, particle)

                player.world.getNearbyPlayers(player.location, 1.5).forEach { victim ->
                    val session = plugin.sessionManager?.getSession(victim)
                    if (session?.esAsesino(victim.uniqueId) == true && !hitted.contains(victim.uniqueId)) {
                        victim.scheduler.run(plugin, { _ ->
                            hitted.add(victim.uniqueId)

                            plugin.combatManager?.takeDamage(victim)

                            val knockback = player.location.direction.multiply(1.5).setY(0.4)
                            victim.velocity = knockback

                            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 1))
                            victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0))

                            victim.world.playSound(victim.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                        }, null)
                    }
                }
                count++
            }, null, 1L, 1L)
        }, null)
    }

    fun aplicarGolpePuno(victim: Player) {
        victim.scheduler.run(plugin, { _ ->
            victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 120, 0))
            victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 120, 1))
            victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f)
        }, null)
    }

    private fun usarBloqueoSonic(player: Player) {
        player.scheduler.run(plugin, { _ ->
            val startLoc = player.eyeLocation
            val direction = startLoc.direction.normalize()

            player.playSound(player.location, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1f)

            player.scheduler.runDelayed(plugin, { _ ->
                if (!player.isOnline) return@runDelayed

                player.world.playSound(player.location, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1f)

                val currentLoc = startLoc.clone()
                var hitTarget = false

                for (i in 0..15) {
                    currentLoc.add(direction)

                    val pos = Vector3d(currentLoc.x, currentLoc.y, currentLoc.z)
                    val particle = WrapperPlayServerParticle(Particle(ParticleTypes.SONIC_BOOM), false, pos, Vector3f(), 0f, 1)
                    player.world.players.forEach { PacketEvents.getAPI().playerManager.sendPacket(it, particle) }

                    if (!hitTarget) {
                        currentLoc.world.getNearbyPlayers(currentLoc, 1.5).forEach { victim ->
                            val session = plugin.sessionManager?.getSession(victim)
                            if (session?.esAsesino(victim.uniqueId) == true) {
                                victim.scheduler.run(plugin, { _ ->
                                    val kb = direction.clone().multiply(2.5).setY(0.5)
                                    victim.velocity = kb

                                    victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2))
                                    victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 100, 1))
                                }, null)
                                hitTarget = true
                            }
                        }
                    }
                }
            }, null, 30L)
        }, null)
    }
}
