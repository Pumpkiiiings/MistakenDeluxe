package liric.mistaken.roles.killers.clases

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo
import liric.mistaken.Mistaken
import liric.mistaken.roles.killers.Killer
import liric.mistaken.roles.killers.CoreKiller
import liric.mistaken.utils.hooks.CraftEngine
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.util.Vector
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f as JomlVector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LIRIC-MISTAKEN 2.0]
 * Romeo: El Administrador del Mundo.
 * FIX: Finishers Aleatorios de Admin agregados.
 */
class Romeo : CoreKiller(
    "romeo",
    pumpking.lib.service.PumpkingServiceManager.messages.getStrictString(null, "asesinos.romeo.nombre", "killers_info")
), Listener { // 🔥 Agregado Listener para los Finishers

    private val pathBase = "asesinos.romeo"
    private val orbitadores = ConcurrentHashMap<UUID, liric.mistaken.packet.fake.VirtualBlockDisplay>()
    private val angulos = ConcurrentHashMap<UUID, Double>()
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Anti-spam para los efectos de muerte
    private val lastKillEffect = ConcurrentHashMap<UUID, Long>()

    init {
        preLoadKit()
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getKillerConfig(this.id)
        val armorMap = mapOf(
            "helmet" to Material.NETHERITE_HELMET,
            "chestplate" to Material.NETHERITE_CHESTPLATE,
            "leggings" to Material.NETHERITE_LEGGINGS,
            "boots" to Material.NETHERITE_BOOTS
        )

        armorMap.forEach { (key, fallbackMat) ->
            val id = config.getString("armor.$key") ?: "none"
            if (id != "none" && id.isNotBlank()) {
                val item = CraftEngine.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: fallbackMat)
            }
        }

        listOf("weapon", "skill1", "skill2", "skill3", "skill4").forEach { key ->
            val id = config.getString("items.$key") ?: "none"
            if (id != "none" && id.isNotBlank()) {
                val item = CraftEngine.getCustomItem(id)
                itemKitCache[key] = item ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
            }
        }
    }

    override fun useSkill(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return
        when (slot) {
            1 -> { habilidadAdminDash(player); playSkillEffects(player, 1) }
            2 -> { habilidadAdminVision(player); playSkillEffects(player, 2) }
            3 -> { habilidadTripleColmillo(player); playSkillEffects(player, 3) }
            4 -> { habilidadNetherStar(player); playSkillEffects(player, 4) }
        }
    }

    // --- 💀 FINISHERS: EFECTOS DE ASESINATO ALEATORIOS DE ADMIN ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRomeoKill(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val session = plugin.sessionManager.getSession(attacker) ?: return
        if (session.isKiller(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
            // Verificamos que la víctima haya muerto (GameMode cambiado por CombatManager)
            if (victim.gameMode == GameMode.SPECTATOR) {
                val now = System.currentTimeMillis()
                if (now - lastKillEffect.getOrDefault(victim.uniqueId, 0L) > 2000L) {
                    lastKillEffect[victim.uniqueId] = now
                    triggerFinisherAleatorio(victim.location)
                }
            }
        }
    }

    private fun triggerFinisherAleatorio(loc: Location) {
        val effectType = ThreadLocalRandom.current().nextInt(3)
        val world = loc.world ?: return

        when (effectType) {
            0 -> {
                // EFECTO 1: //SET 0 (BORRADO DE CÓDIGO)
                world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f)

                // Cárcel de cristal rojo glitch
                val jail = world.spawn(loc.clone().add(0.0, 1.0, 0.0), BlockDisplay::class.java) {
                    it.block = Material.RED_STAINED_GLASS.createBlockData()
                    it.transformation = Transformation(JomlVector3f(-1f, -1f, -1f), Quaternionf(), JomlVector3f(2f, 2f, 2f), Quaternionf())
                    it.isGlowing = true
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 2f)
                    world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3, 0.5, 0.5, 0.5, 0.0)
                    world.spawnParticle(Particle.WHITE_ASH, loc, 300, 1.0, 1.0, 1.0, 0.5)
                    jail.remove()
                }, 20L) // 1 segundo
            }
            1 -> {
                // EFECTO 2: JUICIO DEL ADMINISTRADOR (BLOQUE APLASTANTE)
                val dropLoc = loc.clone().add(0.0, 10.0, 0.0)
                val block = liric.mistaken.packet.PacketFactory.displays.buildBlockDisplay(org.bukkit.Bukkit.getOnlinePlayers().toList(), dropLoc) {
                    it.block = Material.COMMAND_BLOCK.createBlockData()
                    it.transformation = Transformation(JomlVector3f(-1.5f, -1.5f, -1.5f), Quaternionf(), JomlVector3f(3f, 3f, 3f), Quaternionf())
                    it.teleportDuration = 10 // Cae súper rápido
                }

                // Lo hacemos caer al suelo
                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    block.teleport(loc)
                }, 1L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2f, 0.1f)
                    world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 2f, 0.5f)
                    world.spawnParticle(Particle.EXPLOSION, loc, 2)
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 50, 1.5, 0.5, 1.5, 0.1)
                }, 11L)

                // El bloque desaparece poco a poco
                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ -> block.remove() }, 30L)
            }
            2 -> {
                // EFECTO 3: ASCENSIÓN A LA TERMINAL
                world.playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 2f)
                val star = world.spawn(loc.clone().add(0.0, 0.5, 0.0), ItemDisplay::class.java) {
                    it.setItemStack(ItemStack(Material.NETHER_STAR))
                    it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(1f, 1f, 1f), Quaternionf())
                    it.teleportDuration = 30 // Sube lento
                    it.interpolationDuration = 30
                }

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    star.teleport(loc.clone().add(0.0, 4.0, 0.0)) // Asciende 4 bloques
                    val t = star.transformation
                    t!!.leftRotation.rotateY(5f) // Gira como loco
                    star.transformation = t
                }, 1L)

                plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                    world.playSound(loc.clone().add(0.0, 4.0, 0.0), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.5f)
                    world.spawnParticle(Particle.FIREWORK, loc.clone().add(0.0, 4.0, 0.0), 100, 0.5, 0.5, 0.5, 0.2)
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0.0, 4.0, 0.0), 50, 0.5, 0.5, 0.5, 0.5)
                    star.remove()
                }, 31L)
            }
        }
    }

    // --- HABILIDADES ---

    private fun habilidadAdminDash(player: Player) {
        player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f)
        var duration = 100

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (duration <= 0 || !player.isOnline) {
                task.cancel()
                return@Consumer
            }
            val dir = player.location.direction.normalize().multiply(1.4)
            player.velocity = dir

            val checkLoc = player.location.clone().add(dir.clone().multiply(0.8))
            if (checkLoc.block.type.isSolid) {
                plugin.combatManager.takeDamage(player)
                player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f)
                task.cancel()
                return@Consumer
            }

            player.world.getNearbyPlayers(player.location, 2.0).firstOrNull { isValidTarget(player, it) }?.let { victim ->
                plugin.combatManager.takeDamage(victim)
                victim.velocity = player.location.direction.normalize().multiply(1.5).setY(0.4)
                player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f)
                duration = 0
            }
            duration--
        }, null, 1L, 1L)
    }

    private fun habilidadAdminVision(player: Player) {
        val teamName = "admin_glow"
        val teamInfo = ScoreBoardTeamInfo(
            Component.text("AdminTeam"), Component.empty(), Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.NONE
        )

        player.world.getNearbyPlayers(player.location, 100.0).forEach { victim ->
            if (isValidTarget(player, victim)) {
                val createTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, teamInfo, listOf(victim.name))
                PacketEvents.getAPI().playerManager.sendPacket(player, createTeam)
                val metadata = listOf(EntityData(0, EntityDataTypes.BYTE, 0x40.toByte()))
                PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerEntityMetadata(victim.entityId, metadata))
            }
        }

        player.scheduler.runDelayed(plugin, Consumer { _ ->
            if (player.isOnline) {
                val removeTeam = WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty())
                PacketEvents.getAPI().playerManager.sendPacket(player, removeTeam)
            }
        }, null, 200L)
    }

    private fun habilidadTripleColmillo(player: Player) {
        val startLoc = player.location
        val angles = listOf(-25.0, 0.0, 25.0)

        angles.forEach { offset ->
            val direction = startLoc.direction.clone().rotateAroundY(Math.toRadians(offset)).setY(0.0).normalize()
            val currentLoc = startLoc.clone()

            for (i in 0 until 15) {
                currentLoc.add(direction)
                val locToSpawn = currentLoc.clone()
                plugin.server.regionScheduler.runDelayed(plugin, locToSpawn, Consumer { _ ->
                    if (!locToSpawn.block.type.isSolid) {
                        locToSpawn.world.spawn(locToSpawn, EvokerFangs::class.java)
                        locToSpawn.world.getNearbyPlayers(locToSpawn, 1.5).forEach { victim ->
                            if (isValidTarget(player, victim)) {
                                plugin.combatManager.takeDamage(victim)
                                victim.velocity = Vector(0.0, 0.5, 0.0)
                                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 40, 0))
                            }
                        }
                    }
                }, (i * 2 + 1).toLong())
            }
        }
    }

    private fun habilidadNetherStar(player: Player) {
        val star = liric.mistaken.packet.PacketFactory.displays.buildItemDisplay(org.bukkit.Bukkit.getOnlinePlayers().toList(), player.eyeLocation) {
            it.setItemStack(ItemStack(Material.NETHER_STAR))
            it.transformation = Transformation(JomlVector3f(), Quaternionf(), JomlVector3f(0.7f, 0.7f, 0.7f), Quaternionf())
            it.interpolationDuration = 1; it.teleportDuration = 1
        }
        val direction = player.location.direction.multiply(1.5)

        var ticks = 0
        star.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 40 || !star.isValid) {
                if (star.isValid) star.remove()
                task.cancel()
                return@Consumer
            }
            star.teleport(star.location.add(direction))

            val hit = player.world.getNearbyPlayers(star.location, 1.5).firstOrNull { isValidTarget(player, it) }

            if (hit != null || star.location.block.type.isSolid) {
                star.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, star.location, 1)
                hit?.let { plugin.combatManager.takeDamage(it) }
                star.remove()
                task.cancel()
            }
            ticks++
        }, null, 1L, 1L)
    }

    override fun equip(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val configMecanica = plugin.configManager.getKillerConfig(this.id)
        val langInfo = pumpking.lib.service.PumpkingServiceManager.messages.getSpecificFile(player, "killers_info")

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = if (isArmor) configMecanica.getString("armor.$key")
            else configMecanica.getString("items.$key")

            if (id == null || id == "none") return

            val item = CraftEngine.getCustomItem(id) ?: run {
                val matName = id.replace(".*:".toRegex(), "").uppercase()
                val mat = Material.matchMaterial(matName)
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "weapon") "asesinos.romeo.skill_names.weapon"
            else "asesinos.romeo.skill_names.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(pumpking.lib.color.ColorTranslator.translate(it)) }
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
        deliver("skill1", 1); deliver("skill2", 2)
        deliver("skill3", 3); deliver("skill4", 4)
        deliver("weapon", 8)

        player.inventory.heldItemSlot = 8
        player.updateInventory()
    }

    override fun showPhysicalTrail(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.isKiller(player)) { limpiar(uuid); return }

        val playerWorld = player.world
        if (orbitadores[uuid]?.world != playerWorld) limpiar(uuid)

        val display = orbitadores.getOrPut(uuid) {
            liric.mistaken.packet.PacketFactory.displays.buildBlockDisplay(org.bukkit.Bukkit.getOnlinePlayers().toList(), player.location) {
                it.block = Material.COMMAND_BLOCK.createBlockData()
                it.transformation = Transformation(
                    JomlVector3f(-0.2f, -0.2f, -0.2f),
                    Quaternionf(),
                    JomlVector3f(0.4f, 0.4f, 0.4f),
                    Quaternionf()
                )
                it.teleportDuration = 3
                it.interpolationDuration = 3
            }
        }

        val angulo = (angulos.getOrDefault(uuid, 0.0) + 0.12) % (Math.PI * 2)
        val pLoc = player.location
        val radio = 1.5

        val x = radio * cos(angulo)
        val z = radio * sin(angulo)
        val y = 1.2 + (0.2 * sin(angulo * 2))

        val targetLoc = pLoc.clone().add(x, y, z)
        targetLoc.yaw = (angulo * 120).toFloat() % 360
        targetLoc.pitch = (angulo * 60).toFloat() % 360

        display.teleport(targetLoc)
        angulos[uuid] = angulo
    }

    override fun showTrail(player: Player) {}

    private fun limpiar(uuid: UUID) {
        orbitadores.remove(uuid)?.remove()
        angulos.remove(uuid)
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            limpiar(it.uniqueId)
            lastKillEffect.remove(it.uniqueId)
        }
    }
}










