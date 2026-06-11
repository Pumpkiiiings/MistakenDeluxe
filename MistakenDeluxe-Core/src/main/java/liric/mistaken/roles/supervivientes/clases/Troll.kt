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
import liric.mistaken.utils.hooks.CraftEngine
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
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
 * Troll: El maestro del engaÃ±o.
 * FIX: Actualizado al constructor moderno de PlayerInfo de PacketEvents.
 */
class Troll : Superviviente(
    "troll",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "supervivientes.troll.nombre", "supervivientes_info")
) {

    private val pathBase = "supervivientes.troll"
    private val activeClones = ConcurrentHashMap<Int, UUID>() // FakeEntityID -> PlayerUUID

    override fun usarHabilidad(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSupervivientes()
        val langConfig = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "supervivientes_info")

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

    private fun sendAbilityMessage(player: Player, lang: org.bukkit.configuration.file.FileConfiguration, mech: org.bukkit.configuration.file.FileConfiguration, key: String) {
        val msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) player.sendMessage(mm.deserialize(msg))
        val soundName = mech.getString("$pathBase.items.${key}_sonido", "ENTITY_BAT_TAKEOFF")
        runCatching { player.playSound(player.location, Sound.valueOf(soundName!!.uppercase()), 1f, 1f) }
    }

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val langInfo = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "supervivientes_info")
        val configMecanica = plugin.configManager.getSupervivientes()

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("$pathBase.armadura.$key") else configMecanica.getString("$pathBase.items.$key")
            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
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
    }

    // --- ðŸƒâ€â™‚ï¸ HABILIDAD 1: CLON INTELIGENTE (PAQUETES FALSOS) ---

    private fun invocarClonInteligente(player: Player) {
        val loc = player.location.clone()
        val fakeId = ThreadLocalRandom.current().nextInt(200000, 300000)
        val fakeUUID = UUID.randomUUID()
        val pm = PacketEvents.getAPI().playerManager

        // 1. Copiar Texturas del Jugador
        val profile = UserProfile(fakeUUID, player.name)
        player.playerProfile.properties.forEach { prop ->
            profile.textureProperties.add(TextureProperty(prop.name, prop.value, prop.signature))
        }

        // 2. Crear Paquetes Base (Spawn & Metadata)
        // ðŸ”¥ FIX: WrapperPlayServerPlayerInfoUpdate requiere ahora el enum GameMode y el display name.
        val infoData = WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            profile,
            true,
            1,
            GameMode.SURVIVAL,
            Component.text(player.name),
            null
        )

        val infoPacket = WrapperPlayServerPlayerInfoUpdate(
            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
            infoData
        )

        val spawnPacket = WrapperPlayServerSpawnEntity(
            fakeId, Optional.of(fakeUUID), EntityTypes.PLAYER,
            Vector3d(loc.x, loc.y, loc.z), loc.pitch, loc.yaw, loc.yaw, 0, Optional.empty()
        )

        // 0x7F habilita todas las capas de la skin (sombrero, chaqueta, etc.)
        val metaPacket = WrapperPlayServerEntityMetadata(fakeId, listOf(EntityData(17, EntityDataTypes.BYTE, 0x7F.toByte())))

        // Enviar a todos
        plugin.server.onlinePlayers.forEach { viewer ->
            pm.sendPacket(viewer, infoPacket)
            pm.sendPacket(viewer, spawnPacket)
            pm.sendPacket(viewer, metaPacket)
        }

        // Quitar del TAB casi al instante para que no se vea doble
        player.scheduler.runDelayed(plugin, Consumer { _ ->
            val removeInfo = WrapperPlayServerPlayerInfoRemove(profile.uuid)
            plugin.server.onlinePlayers.forEach { pm.sendPacket(it, removeInfo) }
        }, null, 5L)

        // --- IA DE MOVIMIENTO DEL CLON ---
        var ticks = 0
        val currentLoc = loc.clone()
        val direction = loc.direction.setY(0.0).normalize()
        var velocityY = 0.0

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 140 || !player.isOnline) { // Dura 7 segundos
                val destroyPacket = WrapperPlayServerDestroyEntities(fakeId)
                plugin.server.onlinePlayers.forEach { pm.sendPacket(it, destroyPacket) }
                player.world.spawnParticle(Particle.CLOUD, currentLoc.clone().add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.05)
                task.cancel()
                return@Consumer
            }

            // FÃ­sica de IA simple
            val lookAhead = currentLoc.clone().add(direction.clone().multiply(1.0))
            if (lookAhead.block.type.isSolid) {
                // Si hay pared, salta o gira
                if (!lookAhead.clone().add(0.0, 1.0, 0.0).block.type.isSolid) {
                    velocityY = 0.5 // Salto
                } else {
                    direction.rotateAroundY(Math.toRadians((if (Math.random() > 0.5) 90.0 else -90.0))) // Gira 90 grados
                }
            }

            velocityY -= 0.08 // Gravedad
            currentLoc.add(direction.x * 0.4, velocityY, direction.z * 0.4) // Velocidad de correr

            if (currentLoc.block.type.isSolid) {
                currentLoc.y = currentLoc.block.y + 1.0
                velocityY = 0.0
            }

            val tpPacket = WrapperPlayServerEntityTeleport(fakeId, Vector3d(currentLoc.x, currentLoc.y, currentLoc.z), currentLoc.yaw, currentLoc.pitch, true)
            val headPacket = WrapperPlayServerEntityHeadLook(fakeId, currentLoc.yaw)

            plugin.server.onlinePlayers.forEach { viewer ->
                pm.sendPacket(viewer, tpPacket)
                pm.sendPacket(viewer, headPacket)
            }

            if (ticks % 6 == 0) player.world.playSound(currentLoc, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.3f, 1f)

            ticks++
        }, null, 1L, 1L)
    }

    // --- ðŸŒ HABILIDAD 2: CÃSCARA DE PLÃTANO ---

    private fun colocarCascaraPlatano(player: Player) {
        val loc = player.location.clone()
        val platano = loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(Material.YELLOW_DYE))
            // Acostado en el piso
            id.transformation = Transformation(
                JomlVector3f(0f, 0.1f, 0f),
                Quaternionf().rotateX(Math.toRadians(90.0).toFloat()),
                JomlVector3f(0.5f, 0.5f, 0.5f),
                Quaternionf()
            )
        }

        var ticks = 0
        platano.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 300 || !platano.isValid) { // Desaparece en 15s si nadie la pisa
                if (platano.isValid) platano.remove()
                task.cancel()
                return@Consumer
            }

            val killer = platano.world.getNearbyPlayers(platano.location, 1.0).firstOrNull {
                plugin.sessionManager.getSession(it)?.esAsesino(it.uniqueId) == true
            }
            if (killer != null) {
                // Â¡Se resbalÃ³!
                killer.playSound(killer.location, Sound.ENTITY_SLIME_SQUISH, 1f, 0.5f)
                killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 4))
                killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 40, 0))

                // Sacudida de cÃ¡mara fuerte y salto involuntario
                killer.velocity = Vector(0.0, 0.6, 0.0)
                killer.setRotation(killer.yaw + 180f, -45f)

                killer.sendMessage(plugin.mm.deserialize("<yellow>Â¡Te resbalaste con una cÃ¡scara de plÃ¡tano!"))

                platano.world.spawnParticle(Particle.DUST, platano.location, 10, 0.2, 0.2, 0.2, Particle.DustOptions(Color.YELLOW, 1f))
                platano.remove()
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

    // --- ðŸŽ HABILIDAD 3: CAJA SORPRESA ---

    private fun colocarCajaSorpresa(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.0, 0.5)
        val caja = loc.world.spawn(loc, BlockDisplay::class.java) { bd ->
            bd.block = Material.CHEST.createBlockData()
            bd.transformation = Transformation(JomlVector3f(-0.5f, 0f, -0.5f), Quaternionf(), JomlVector3f(1f, 1f, 1f), Quaternionf())
        }

        var ticks = 0
        caja.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 400 || !caja.isValid) { // 20s
                if (caja.isValid) caja.remove()
                task.cancel()
                return@Consumer
            }

            val killer = caja.world.getNearbyPlayers(caja.location, 2.0).firstOrNull {
                plugin.sessionManager.getSession(it)?.esAsesino(it.uniqueId) == true
            }
            if (killer != null) {
                // Â¡Sorpresa!
                caja.world.playSound(caja.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
                caja.world.playSound(caja.location, Sound.ENTITY_WITCH_CELEBRATE, 1f, 1f)
                caja.world.spawnParticle(Particle.EXPLOSION_EMITTER, caja.location, 1)

                killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 80, 0))
                killer.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 1))
                killer.sendMessage(plugin.mm.deserialize("<red><b>Â¡BOOM!</b> <gray>Â¡Era una trampa!"))

                caja.remove()
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }
}




