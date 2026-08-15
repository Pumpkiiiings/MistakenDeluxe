package liric.mistaken.roles.survivors.clases

import liric.mistaken.utils.sessionViewers
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
import liric.mistaken.api.util.Sounds
import liric.mistaken.roles.survivors.Survivor
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
import liric.mistaken.packet.PacketFactory
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import pumpking.lib.color.ColorTranslator
import pumpking.lib.service.PumpkingServiceManager

/**
 *[LIRIC-MISTAKEN 2.0]
 * Troll: El maestro del engaño.
 * FIX: Actualizado al constructor moderno de PlayerInfo de PacketEvents.
 */
class Troll : Survivor(
    "troll",
    PumpkingServiceManager.messages.getStrictString(null, "supervivientes.troll.nombre", "survivors_info")
) {

    private val pathBase = "supervivientes.troll"
    private val activeClones = ConcurrentHashMap<Int, UUID>() // FakeEntityID -> PlayerUUID

    override fun useSkill(player: Player, slot: Int) {
        val mechConfig = plugin.configManager.getSurvivorConfig(this.id)
        val langConfig = PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")

        when (slot) {
            0 -> if (!checkCooldown(player, 0, mechConfig.getInt("items.skill1_cooldown", 30))) {
                invocarClonInteligente(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill1")
            }
            1 -> if (!checkCooldown(player, 1, mechConfig.getInt("items.skill2_cooldown", 20))) {
                colocarCascaraPlatano(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill2")
            }
            2 -> if (!checkCooldown(player, 2, mechConfig.getInt("items.skill3_cooldown", 30))) {
                colocarCajaSorpresa(player)
                sendAbilityMessage(player, langConfig, mechConfig, "skill3")
            }
        }
    }

    private fun sendAbilityMessage(player: Player, lang: FileConfiguration, mech: FileConfiguration, key: String) {
        val msg = lang.getString("$pathBase.habilidades_mensajes.$key")
        if (!msg.isNullOrEmpty()) player.sendMessage(ColorTranslator.translate(msg))
        val soundName = mech.getString("$pathBase.items.${key}_sound", "ENTITY_BAT_TAKEOFF")
        Sounds.orNull(soundName)?.let { player.playSound(player.location, it, 1f, 1f) }
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val langInfo = PumpkingServiceManager.messages.getSpecificFile(player, "survivors_info")
        val configMecanica = plugin.configManager.getSurvivorConfig(this.id)

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("armor.$key") else configMecanica.getString("items.$key")
            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
                val mat = Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase())
                if (mat != null) ItemStack(mat) else null
            } ?: return

            langInfo.getString("$pathBase.skill_names.$key")?.let {
                item.editMeta { meta -> meta.displayName(ColorTranslator.translate(it)) }
            }

            if (isArmor) {
                when(key) {
                    "helmet" -> inv.helmet = item
                    "chestplate" -> inv.chestplate = item
                    "leggings" -> inv.leggings = item
                    "boots" -> inv.boots = item
                }
            } else inv.setItem(slot, item)
        }

        deliver("helmet", 0, true); deliver("chestplate", 0, true)
        deliver("leggings", 0, true); deliver("boots", 0, true)
        deliver("skill1", 0); deliver("skill2", 1); deliver("skill3", 2)

        player.updateInventory()
    }

    // --- 🏃‍♂️ HABILIDAD 1: CLON INTELIGENTE (PAQUETES FALSOS) ---

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

        // 3. Copiar Equipamiento (PacketEvents 2.0+)
        val equipmentList = mutableListOf<com.github.retrooper.packetevents.protocol.player.Equipment>()
        val fromBukkit = { item: ItemStack? -> io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack(item) }
        
        player.inventory.helmet?.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET, fromBukkit(it))) }
        player.inventory.chestplate?.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE, fromBukkit(it))) }
        player.inventory.leggings?.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS, fromBukkit(it))) }
        player.inventory.boots?.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS, fromBukkit(it))) }
        player.inventory.itemInMainHand.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND, fromBukkit(it))) }
        player.inventory.itemInOffHand.let { equipmentList.add(com.github.retrooper.packetevents.protocol.player.Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND, fromBukkit(it))) }
        
        val equipPacket = WrapperPlayServerEntityEquipment(fakeId, equipmentList)

        // Enviar a todos
        plugin.server.onlinePlayers.forEach { viewer ->
            pm.sendPacket(viewer, infoPacket)
            pm.sendPacket(viewer, spawnPacket)
            if (equipmentList.isNotEmpty()) pm.sendPacket(viewer, equipPacket)
        }

        // Quitar del TAB casi al instante
        player.scheduler.runDelayed(plugin, Consumer { _ ->
            val removeInfo = WrapperPlayServerPlayerInfoRemove(profile.uuid)
            plugin.server.onlinePlayers.forEach { pm.sendPacket(it, removeInfo) }
        }, null, 5L)
        
        // 4. Hacer invisible al jugador real y esconder armadura
        val savedArmor = player.inventory.armorContents.clone()
        val savedHand = player.inventory.itemInMainHand.clone()
        val savedOffHand = player.inventory.itemInOffHand.clone()
        
        player.inventory.armorContents = arrayOfNulls(4)
        player.inventory.setItemInMainHand(null)
        player.inventory.setItemInOffHand(null)
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 140, 1, false, false))

        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline) {
                player.inventory.armorContents = savedArmor
                player.inventory.setItemInMainHand(savedHand)
                player.inventory.setItemInOffHand(savedOffHand)
            }
        }, null, 140L)

        // --- IA DE MOVIMIENTO DEL CLON MEJORADA (MÁS NATURAL Y EVASIVA) ---
        var ticks = 0
        val currentLoc = loc.clone()
        var currentYaw = loc.yaw
        var targetYaw = currentYaw
        var velocityY = 0.0
        var lastJumpTick = 0
        
        var stuckCheckLoc = currentLoc.clone()

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 600 || !player.isOnline) { // Dura 30 segundos
                val destroyPacket = WrapperPlayServerDestroyEntities(fakeId)
                plugin.server.onlinePlayers.forEach { pm.sendPacket(it, destroyPacket) }
                player.world.spawnParticle(Particle.CLOUD, currentLoc.clone().add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.05)
                task.cancel()
                return@Consumer
            }

            // Chequeo de atasco cada 20 ticks (1 segundo)
            if (ticks > 0 && ticks % 20 == 0) {
                if (currentLoc.distanceSquared(stuckCheckLoc) < 0.8) {
                    // Está atascado, forzar giro y salto
                    targetYaw += 180f + (Math.random() * 90 - 45).toFloat()
                    if (velocityY <= 0) velocityY = 0.5
                }
                stuckCheckLoc = currentLoc.clone()
            }

            // Función para verificar si un bloque es sólido (Ignora puertas abiertas y carteles)
            val isSolidBlock = { b: org.bukkit.block.Block ->
                if (!b.type.isSolid) false
                else {
                    val data = b.blockData
                    if (data is org.bukkit.block.data.Openable) {
                        !data.isOpen // No es sólido si está abierto
                    } else {
                        !b.type.name.contains("SIGN")
                    }
                }
            }

            // --- 0. Interacción con Asesinos (Huir o Explotar) ---
            var isFleeing = false
            for (viewer in plugin.server.onlinePlayers) {
                val session = plugin.sessionManager.getSession(viewer)
                if (session != null && session.isKiller(viewer.uniqueId) && viewer.world == currentLoc.world) {
                    val dist = viewer.location.distanceSquared(currentLoc)
                    if (dist < 6.25) { // Aprox 2.5 bloques. Si el asesino está muy cerca (golpeado)
                        val destroyPacket = WrapperPlayServerDestroyEntities(fakeId)
                        plugin.server.onlinePlayers.forEach { pm.sendPacket(it, destroyPacket) }
                        
                        player.world.spawnParticle(Particle.END_ROD, currentLoc.clone().add(0.0, 1.0, 0.0), 50, 0.5, 0.8, 0.5, 0.1)
                        player.world.playSound(currentLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f)
                        
                        task.cancel()
                        return@Consumer
                    } else if (dist < 100.0) { // Menos de 10 bloques: Huir
                        val fleeDir = currentLoc.clone().subtract(viewer.location).toVector().setY(0).normalize()
                        if (fleeDir.lengthSquared() > 0) {
                            targetYaw = Math.toDegrees(Math.atan2(-fleeDir.x, fleeDir.z)).toFloat()
                            isFleeing = true
                            // Pánico: salta más rápido
                            if (Math.random() > 0.85 && velocityY <= 0.0) velocityY = 0.42
                        }
                    }
                }
            }

            // --- 1. Movimientos irregulares (Zig-zag suave) ---
            if (!isFleeing && ticks % 10 == 0 && Math.random() > 0.2) {
                targetYaw += (Math.random() * 40 - 20).toFloat()
            }

            // Suavizar el yaw
            var yawDiff = (targetYaw - currentYaw) % 360
            if (yawDiff > 180) yawDiff -= 360
            if (yawDiff < -180) yawDiff += 360
            currentYaw += yawDiff * (if (isFleeing) 0.3f else 0.15f)

            val direction = Vector(
                -Math.sin(Math.toRadians(currentYaw.toDouble())),
                0.0,
                Math.cos(Math.toRadians(currentYaw.toDouble()))
            ).normalize()

            // --- 2. Detección de suelo y salto ---
            val isOnGround = currentLoc.clone().subtract(0.0, 0.1, 0.0).block.type.isSolid || currentLoc.y <= Math.floor(currentLoc.y) + 0.1

            if (isOnGround && ticks - lastJumpTick > 15) {
                val jumpChance = if (isFleeing) 0.40 else 0.85 // Salta más fácil si huye
                if (Math.random() > jumpChance) { 
                    velocityY = 0.42
                    lastJumpTick = ticks
                }
            }

            // --- 3. Steering / Navegación inteligente (Whiskers / Raytracing simulado) ---
            val eyeLoc = currentLoc.clone().add(0.0, 1.0, 0.0)
            
            // Función para "lanzar un rayo" y medir distancia libre
            val getFreeDistance = { dirVector: Vector ->
                var dist = 0.0
                while (dist < 4.0) {
                    val b = eyeLoc.clone().add(dirVector.clone().multiply(dist)).block
                    
                    // Si encontramos una puerta cerrada cerca, la abrimos para poder pasar
                    if (dist < 1.5 && b.blockData is org.bukkit.block.data.Openable) {
                        val openable = b.blockData as org.bukkit.block.data.Openable
                        if (!openable.isOpen) {
                            openable.isOpen = true
                            b.blockData = openable
                            b.world.playSound(b.location, Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f)
                        }
                    }
                    
                    if (isSolidBlock(b)) break
                    dist += 0.5
                }
                dist
            }

            val frontDist = getFreeDistance(direction)
            val leftDir = direction.clone().rotateAroundY(Math.toRadians(45.0))
            val rightDir = direction.clone().rotateAroundY(Math.toRadians(-45.0))
            
            val leftDist = getFreeDistance(leftDir)
            val rightDist = getFreeDistance(rightDir)

            // Lógica de dirección (Steering) guiada por los rayos
            if (frontDist < 1.5) {
                // Hay un obstáculo al frente. Decidir mejor ruta (evita chocar).
                if (leftDist > rightDist && leftDist > 1.5) {
                    targetYaw -= 35f // Más peso hacia la izquierda
                } else if (rightDist > leftDist && rightDist > 1.5) {
                    targetYaw += 35f // Más peso a la derecha
                } else {
                    // Atrapado o pasillo ciego, dar media vuelta suave
                    targetYaw += 60f
                }
                
                // Si está MUY cerca de la pared, intentar saltarla si es escalón
                if (frontDist < 0.8 && isOnGround) {
                    val footBlock = currentLoc.clone().add(direction.clone().multiply(0.8)).block
                    val headBlock = footBlock.getRelative(0, 1, 0)
                    if (isSolidBlock(footBlock) && !isSolidBlock(headBlock)) {
                        velocityY = 0.42 // Saltar escalón
                    }
                }
            } else {
                // Si el frente está libre, centrarse en los pasillos (repulsión de paredes laterales)
                if (leftDist < 1.2) targetYaw += 20f // Aléjate de la izquierda
                if (rightDist < 1.2) targetYaw -= 20f // Aléjate de la derecha
            }

            // --- 4. Físicas de caída y desplazamiento ---
            velocityY -= 0.08
            if (velocityY < -0.8) velocityY = -0.8
            
            val baseSpeed = if (isFleeing) 0.32 else 0.26
            val speed = if (!isOnGround) baseSpeed + 0.05 else baseSpeed
            
            val moveX = direction.x * speed
            val moveZ = direction.z * speed
            
            // Colisión X/Z
            val nextXLoc = currentLoc.clone().add(moveX, 0.2, 0.0)
            if (!isSolidBlock(nextXLoc.block) && !isSolidBlock(nextXLoc.clone().add(0.0, 1.0, 0.0).block)) {
                currentLoc.x += moveX
            } else {
                targetYaw += if (Math.random() > 0.5) 50f else -50f 
            }

            val nextZLoc = currentLoc.clone().add(0.0, 0.2, moveZ)
            if (!isSolidBlock(nextZLoc.block) && !isSolidBlock(nextZLoc.clone().add(0.0, 1.0, 0.0).block)) {
                currentLoc.z += moveZ
            } else {
                targetYaw += if (Math.random() > 0.5) 50f else -50f 
            }

            // Colisión Y
            currentLoc.y += velocityY
            if (velocityY < 0 && currentLoc.block.type.isSolid) {
                currentLoc.y = currentLoc.block.y + 1.0
                velocityY = 0.0
            } else if (velocityY > 0 && currentLoc.clone().add(0.0, 1.8, 0.0).block.type.isSolid) {
                velocityY = -0.08
            }

            currentLoc.yaw = currentYaw

            // --- 5. Envío de paquetes ---
            val tpPacket = WrapperPlayServerEntityTeleport(fakeId, Vector3d(currentLoc.x, currentLoc.y, currentLoc.z), currentYaw, currentLoc.pitch, isOnGround)
            val headPacket = WrapperPlayServerEntityHeadLook(fakeId, currentYaw)
            
            val metadataPacket = WrapperPlayServerEntityMetadata(fakeId, listOf(
                EntityData(0, EntityDataTypes.BYTE, 0x08.toByte())
            ))

            plugin.server.onlinePlayers.forEach { viewer ->
                pm.sendPacket(viewer, tpPacket)
                pm.sendPacket(viewer, headPacket)
                pm.sendPacket(viewer, metadataPacket)
            }

            if (isOnGround && ticks % (if(isFleeing) 5 else 6) == 0) {
                player.world.playSound(currentLoc, Sound.BLOCK_STONE_STEP, 0.3f, 1f)
            }

            ticks++
        }, null, 1L, 1L)
    }

    // --- 🍌 HABILIDAD 2: CÁSCARA DE PLÁTANO ---

    private fun colocarCascaraPlatano(player: Player) {
        val loc = player.location.clone()
        val platano = PacketFactory.displays.buildItemDisplay(player.sessionViewers(), loc) { id ->
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
                plugin.sessionManager.getSession(it)?.isKiller(it.uniqueId) == true
            }
            if (killer != null) {
                // ¡Se resbaló!
                killer.playSound(killer.location, Sound.ENTITY_SLIME_SQUISH, 1f, 0.5f)
                killer.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 4))
                killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 40, 0))

                // Sacudida de cámara fuerte y salto involuntario
                killer.velocity = Vector(0.0, 0.6, 0.0)
                killer.setRotation(killer.yaw + 180f, -45f)

                killer.sendMessage(ColorTranslator.translate(
                    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(killer, "supervivientes.troll.habilidades.resbalaste_platano", "survivors_info")
                ))

                platano.world.spawnParticle(Particle.DUST, platano.location, 10, 0.2, 0.2, 0.2, Particle.DustOptions(Color.YELLOW, 1f))
                platano.remove()
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

    // --- 🎁 HABILIDAD 3: CAJA SORPRESA ---

    private fun colocarCajaSorpresa(player: Player) {
        val loc = player.location.block.location.add(0.5, 0.0, 0.5)
        val caja = PacketFactory.displays.buildBlockDisplay(player.sessionViewers(), loc) { bd ->
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
                plugin.sessionManager.getSession(it)?.isKiller(it.uniqueId) == true
            }
            if (killer != null) {
                // ¡Sorpresa!
                caja.world.playSound(caja.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
                caja.world.playSound(caja.location, Sound.ENTITY_WITCH_CELEBRATE, 1f, 1f)
                caja.world.spawnParticle(Particle.EXPLOSION_EMITTER, caja.location, 1)

                killer.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 80, 0))
                killer.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 140, 1))
                killer.sendMessage(ColorTranslator.translate(
                    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(killer, "supervivientes.troll.habilidades.boom_trampa", "survivors_info")
                ))

                caja.remove()
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }
}






