package liric.mistaken.roles.supervivientes.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.player.GameMode
import com.github.retrooper.packetevents.protocol.player.TextureProperty
import com.github.retrooper.packetevents.protocol.player.UserProfile
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.*
import liric.mistaken.Mistaken
import liric.mistaken.roles.supervivientes.Superviviente
import liric.mistaken.utils.hooks.CraftEngineHook
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer

/**
 *[LIRIC-MISTAKEN 2.0]
 * Troll: El maestro del engaño.
 * FIX: Adaptado a Folia y PlayerInfo v2.
 */
class Troll : Superviviente(
    "troll",
    Mistaken.instance.messageConfig.getRawString(null, "supervivientes.troll.nombre", "<green><b>EL TROLL</b></green>", "supervivientes_info")
) {

    private val pathBase = "supervivientes.troll"
    private val activeClones = ConcurrentHashMap<Int, UUID>()

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = plugin.messageConfig.getSpecificFile(player, "supervivientes_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("$pathBase.items.habilidad1_cooldown", 30))) {
                invocarClonInteligente(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("$pathBase.items.habilidad2_cooldown", 20))) {
                colocarCascaraPlatano(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("$pathBase.items.habilidad3_cooldown", 30))) {
                colocarCajaSorpresa(player)
                sendAbilityMessage(player, langConfig, mechConfig, "habilidad3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: FileConfiguration, mech: FileConfiguration, key: String) {
        player.scheduler.run(plugin, { _ ->
            val msg = lang.getString("$pathBase.habilidades_mensajes.$key")
            if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))
            val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_BAT_TAKEOFF")
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
                val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key") else configMecanica.getString("$pathBase.items.$key")
                if (id == null || id == "none") return

                val item = CraftEngineHook.getCustomItem(id) ?: run {
                    val mat = Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase())
                    if (mat != null) ItemStack(mat) else null
                } ?: return

                langInfo.getString("$pathBase.habilidades_nombres.$key")?.let {
                    item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
                }

                if (isArmor) {
                    when(key) {
                        "casco" -> inv.helmet = item
                        "pechera" -> inv.chestplate = item
                        "pantalones" -> inv.leggings = item
                        "botas" -> inv.boots = item
                    }
                } else inv.setItem(slot, item)
            }

            deliver("casco", 0, true); deliver("pechera", 0, true)
            deliver("pantalones", 0, true); deliver("botas", 0, true)
            deliver("habilidad1", 0); deliver("habilidad2", 1); deliver("habilidad3", 2)

            player.updateInventory()
        }, null)
    }

    private fun invocarClonInteligente(player: Player) {
        val loc = player.location.clone()
        val fakeId = ThreadLocalRandom.current().nextInt(200000, 300000)
        val fakeUUID = UUID.randomUUID()
        val pm = PacketEvents.getAPI().playerManager

        val profile = UserProfile(fakeUUID, player.name)
        player.playerProfile.properties.forEach { prop ->
            profile.textureProperties.add(TextureProperty(prop.name, prop.value, prop.signature))
        }

        val infoData = WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            profile, true, 1, GameMode.SURVIVAL, Component.text(player.name), null
        )
        val infoPacket = WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, infoData)
        val spawnPacket = WrapperPlayServerSpawnEntity(
            fakeId, Optional.of(fakeUUID), EntityTypes.PLAYER,
            Vector3d(loc.x, loc.y, loc.z), loc.pitch, loc.yaw, loc.yaw, 0, Optional.empty()
        )
        val metaPacket = WrapperPlayServerEntityMetadata(fakeId, listOf(EntityData(17, EntityDataTypes.BYTE, 0x7F.toByte())))

        val session = plugin.sessionManager?.getSession(player)
        val viewers = session?.getPlayers() ?: plugin.server.onlinePlayers

        viewers.forEach { viewer ->
            pm.sendPacket(viewer, infoPacket)
            pm.sendPacket(viewer, spawnPacket)
            pm.sendPacket(viewer, metaPacket)
        }

        // Remover del tab inmediatamente para que no se vea doble
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            val removeInfo = WrapperPlayServerPlayerInfoRemove(profile.uuid)
            viewers.forEach { pm.sendPacket(it, removeInfo) }
        }, 5L)

        // Movimiento falso
        var ticks = 0
        val currentLoc = loc.clone()
        val direction = loc.direction.setY(0.0).normalize()
        var velocityY = 0.0

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 140 || !player.isOnline) {
                val destroyPacket = WrapperPlayServerDestroyEntities(fakeId)
                viewers.forEach { pm.sendPacket(it, destroyPacket) }

                plugin.server.regionScheduler.run(plugin, currentLoc, { _ ->
                    currentLoc.world.spawnParticle(Particle.CLOUD, currentLoc.clone().add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.05)
                })
                task.cancel()
                return@Consumer
            }

            val lookAhead = currentLoc.clone().add(direction.clone().multiply(1.0))
            if (lookAhead.block.type.isSolid) {
                if (!lookAhead.clone().add(0.0, 1.0, 0.0).block.type.isSolid) {
                    velocityY = 0.5
                } else {
                    direction.rotateAroundY(Math.toRadians((if (Math.random() > 0.5) 90.0 else -90.0)))
                }
            }

            velocityY -= 0.08
            currentLoc.add(direction.x * 0.4, velocityY, direction.z * 0.4)

            if (currentLoc.block.type.isSolid) {
                currentLoc.y = currentLoc.block.y + 1.0
                velocityY = 0.0
            }

            val tpPacket = WrapperPlayServerEntityTeleport(fakeId, Vector3d(currentLoc.x, currentLoc.y, currentLoc.z), currentLoc.yaw, currentLoc.pitch, true)
            val headPacket = WrapperPlayServerEntityHeadLook(fakeId, currentLoc.yaw)

            viewers.forEach { viewer ->
                pm.sendPacket(viewer, tpPacket)
                pm.sendPacket(viewer, headPacket)
            }

            if (ticks % 6 == 0) {
                plugin.server.regionScheduler.run(plugin, currentLoc, { _ ->
                    currentLoc.world.playSound(currentLoc, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.3f, 1f)
                })
            }
            ticks++
        }, 1L, 1L)
    }

    private fun colocarCascaraPlatano(player: Player) {
        val loc = player.location.clone()
        plugin.server.regionScheduler.run(plugin, loc, { _ ->
            val platano = loc.world.spawn(loc, ItemDisplay::class.java) { id ->
                id.setItemStack(ItemStack(Material.YELLOW_DYE))
                id.transformation = Transformation(
                    JomlVector3f(0f, 0.1f, 0f),
                    Quaternionf().rotateX(Math.toRadians(90.0).toFloat()),
                    JomlVector3f(0.5f, 0.5f, 0.5f),
                    Quaternionf()
                )
            }

            var ticks = 0
            val task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (ticks >= 300 || !platano.isValid) {
                    plugin.server.regionScheduler.run(plugin, loc, { _ ->
                        if (platano.isValid) platano.remove()
                    })
                    task.cancel()
                    return@Consumer
                }

                val killer = loc.world.getNearbyPlayers(loc, 1.0).firstOrNull {
                    plugin.sessionManager?.getSession(it)?.esAsesino(it.uniqueId) == true
                }

                if (killer != null) {
                    killer.scheduler.run(plugin, { _ ->
                        killer.playSound(killer.location, Sound.ENTITY_SLIME_SQUISH, 1f, 0.5f)
                        killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 4))
                        killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 40, 0))
                        killer.velocity = Vector(0.0, 0.6, 0.0)
                        killer.setRotation(killer.yaw + 180f, -45f)
                        killer.sendMessage(plugin.mm.deserialize("<yellow>¡Te resbalaste con una cáscara de plátano!"))
                    }, null)

                    plugin.server.regionScheduler.run(plugin, loc, { _ ->
                        loc.world.spawnParticle(Particle.DUST, loc, 10, 0.2, 0.2, 0.2, Particle.DustOptions(Color.YELLOW, 1f))
                        platano.remove()
                    })
                    task.cancel()
                }
                ticks++
            }, 1L, 1L)
        })
    }

    private fun colocarCajaSorpresa(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.0, 0.5)
        plugin.server.regionScheduler.run(plugin, loc, { _ ->
            val caja = loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
                bd.block = Material.CHEST.createBlockData()
                bd.transformation = Transformation(JomlVector3f(-0.5f, 0f, -0.5f), Quaternionf(), JomlVector3f(1f, 1f, 1f), Quaternionf())
            }

            var ticks = 0
            val task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                if (ticks >= 400 || !caja.isValid) {
                    plugin.server.regionScheduler.run(plugin, loc, { _ ->
                        if (caja.isValid) caja.remove()
                    })
                    task.cancel()
                    return@Consumer
                }

                val killer = loc.world.getNearbyPlayers(loc, 2.0).firstOrNull {
                    plugin.sessionManager?.getSession(it)?.esAsesino(it.uniqueId) == true
                }
                if (killer != null) {
                    plugin.server.regionScheduler.run(plugin, loc, { _ ->
                        loc.world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
                        loc.world.playSound(loc, Sound.ENTITY_WITCH_CELEBRATE, 1f, 1f)
                        loc.world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1)
                        caja.remove()
                    })

                    killer.scheduler.run(plugin, { _ ->
                        killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 80, 0))
                        killer.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 1))
                        killer.sendMessage(plugin.mm.deserialize("<red><b>¡BOOM!</b> <gray>¡Era una trampa!"))
                    }, null)

                    task.cancel()
                }
                ticks++
            }, 1L, 1L)
        })
    }
}
