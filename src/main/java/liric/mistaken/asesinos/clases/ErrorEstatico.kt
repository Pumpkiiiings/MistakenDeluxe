package liric.mistaken.asesinos.clases

import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.Particle.DustOptions
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.max

/**
 * [LIRIC-MISTAKEN 2.0]
 * Error Estático (Miku Glitch Edition).
 * FIX: Schedulers Nativos y Físicas Estables.
 */
class ErrorEstatico : Asesino(
    "error_estatico",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.error_estatico.nombre", "<gradient:#00ffff:#ff00ff><b>ERROR ESTÁTICO</b></gradient>", "asesinos_info")
), Listener {

    private val pathBase = "asesinos.error_estatico"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Variables de control
    private val isUltimateActive = ConcurrentHashMap.newKeySet<UUID>()
    private val passiveTicks = ConcurrentHashMap<UUID, Int>()

    init {
        preLoadKit()
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun preLoadKit() {
        val config = plugin.configManager.getAsesinos()
        val armor = listOf("casco", "pechera", "pantalones", "botas")
        val items = listOf("arma", "habilidad1", "habilidad2", "habilidad3")

        armor.forEach { k ->
            config.getString("$pathBase.armadura.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.LEATHER_HELMET)
                }
            }
        }

        items.forEach { k ->
            config.getString("$pathBase.items.$k")?.let { id ->
                if (id != "none") {
                    itemKitCache[k] = CraftEngineUtils.getCustomItem(id) ?: ItemStack(Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase()) ?: Material.PAPER)
                }
            }
        }
    }

    override fun usarHabilidad(player: Player, slot: Int) {
        if (checkCooldown(player, slot)) return

        when (slot) {
            1 -> habilidadSaltoCuadro(player)
            2 -> habilidadSintoniaForzada(player)
            3 -> habilidadPantallazoAzul(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 📡 PASIVA: FRECUENCIA DISTORSIONADA ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { cleanup(player); return }

        val ticks = passiveTicks.getOrDefault(uuid, 0) + 1
        passiveTicks[uuid] = ticks

        // Cada 5 segundos (100 ticks) da un "parpadeo" visual
        if (ticks % 100 == 0) {
            player.world.spawnParticle(Particle.WHITE_ASH, player.location.add(0.0, 1.0, 0.0), 30, 0.5, 1.0, 0.5, 0.1)
            player.world.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 2.0f)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onGlitchDodge(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return

        if (plugin.gameManager.esAsesino(victim.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(victim.uniqueId)) {
            // 20% de probabilidad de esquivar un golpe por completo
            if (Math.random() <= 0.20) {
                event.isCancelled = true
                victim.world.playSound(victim.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f)
                victim.world.spawnParticle(Particle.REVERSE_PORTAL, victim.location.add(0.0, 1.0, 0.0), 15, 0.5, 0.5, 0.5, 0.1)

                val attacker = event.damager as? Player
                attacker?.sendActionBar(plugin.mm.deserialize("<dark_gray><i>Atacaste a un holograma...</i>"))
            }
        }
    }

    // --- 🏃‍♂️ HABILIDAD 1: SALTO DE CUADRO (GLITCH DASH) ---

    private fun habilidadSaltoCuadro(player: Player) {
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f)

        val startLoc = player.location.clone()
        val targetBlock = player.getTargetBlockExact(8, FluidCollisionMode.NEVER)

        val endLoc = if (targetBlock != null) {
            targetBlock.location.add(0.5, 1.0, 0.5).apply {
                yaw = player.location.yaw
                pitch = player.location.pitch
            }
        } else {
            player.location.add(player.location.direction.multiply(8.0))
        }

        player.teleportAsync(endLoc)

        // Rastro trampa en la vieja ubicación
        val affectedPlayers = mutableSetOf<UUID>()
        var ticks = 0

        // Creamos una tarea estática en el lugar donde inició el dash
        plugin.server.regionScheduler.runAtFixedRate(plugin, startLoc, Consumer { task ->
            if (ticks >= 60) { // Dura 3 segundos la trampa
                task.cancel()
                return@Consumer
            }

            // Partículas de "colores invertidos" (Rosa neón y Cian)
            startLoc.world.spawnParticle(Particle.DUST, startLoc.clone().add(0.0, 1.0, 0.0), 5, 0.4, 0.8, 0.4, DustOptions(Color.FUCHSIA, 1f))
            startLoc.world.spawnParticle(Particle.DUST, startLoc.clone().add(0.0, 1.0, 0.0), 5, 0.4, 0.8, 0.4, DustOptions(Color.AQUA, 1f))

            startLoc.world.getNearbyPlayers(startLoc, 1.5).forEach { victim ->
                if (esObjetivoValido(player, victim) && !affectedPlayers.contains(victim.uniqueId)) {
                    affectedPlayers.add(victim.uniqueId)

                    victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 20, 255, false, false, false))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 20, 250, false, false, false))
                    victim.playSound(victim.location, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f)
                    victim.sendActionBar(plugin.mm.deserialize("<dark_purple><obfuscated>|||</obfuscated> ERROR DE SISTEMA <obfuscated>|||</obfuscated>"))
                }
            }
            ticks++
        }, 1L, 1L)
    }

    // --- 🎵 HABILIDAD 2: SINTONÍA FORZADA ---

    private fun habilidadSintoniaForzada(player: Player) {
        val loc = player.location
        loc.world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BIT, 2f, 0.5f)
        loc.world.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1f, 1.5f)

        // Onda expansiva visual
        for (i in 1..8) {
            plugin.server.regionScheduler.runDelayed(plugin, loc, Consumer { _ ->
                loc.world.spawnParticle(Particle.SONIC_BOOM, loc, 1, (i * 0.5), 0.0, (i * 0.5), 0.0)
            }, i.toLong())
        }

        loc.world.getNearbyPlayers(loc, 8.0).forEach { victim ->
            if (esObjetivoValido(player, victim)) {
                victim.sendActionBar(plugin.mm.deserialize("<aqua>♫ Sintonizando... ♫"))

                var ticks = 0
                victim.scheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (ticks >= 60 || !victim.isOnline) { // 3 Segundos
                        task.cancel()
                        return@Consumer
                    }

                    // Sacude la cámara (Solo en Paper)
                    val randomYaw = (Math.random() * 30 - 15).toFloat()
                    val randomPitch = (Math.random() * 20 - 10).toFloat()
                    victim.setRotation(victim.yaw + randomYaw, victim.pitch + randomPitch)

                    if (ticks % 10 == 0) {
                        victim.playSound(victim.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, (Math.random() * 2f).toFloat())
                    }
                    ticks++
                }, null, 1L, 1L)
            }
        }
    }

    // --- 🖥️ HABILIDAD 3: PANTALLAZO AZUL (ULTIMATE) ---

    private fun habilidadPantallazoAzul(player: Player) {
        val uuid = player.uniqueId
        isUltimateActive.add(uuid)

        // Efecto global inicial
        player.world.players.forEach { online ->
            online.playSound(online.location, Sound.ENTITY_WITHER_DEATH, 0.5f, 0.5f)
            if (esObjetivoValido(player, online)) {
                online.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, true))
                online.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false, true))

                try { plugin.glowingAPI.setGlowing(online, player, ChatColor.AQUA) } catch (_: Exception) {}
            }
        }

        // Retraso de 5 segundos
        player.scheduler.runDelayed(plugin, Consumer { _ ->
            isUltimateActive.remove(uuid)
            if (!player.isOnline) return@Consumer

            val endLoc = player.location
            endLoc.world.playSound(endLoc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.8f)
            endLoc.world.spawnParticle(Particle.EXPLOSION_EMITTER, endLoc, 3)

            endLoc.world.players.forEach { online ->
                if (online.location.distanceSquared(endLoc) < 2500) {
                    online.showTitle(Title.title(
                        plugin.mm.deserialize("<dark_aqua><obfuscated>||</obfuscated> <aqua>This is how it should be</aqua> <dark_aqua><obfuscated>||</obfuscated>"),
                        plugin.mm.deserialize("<gray>Fallo crítico en el sistema.")
                    ))
                }

                if (esObjetivoValido(player, online)) {
                    try { plugin.glowingAPI.unsetGlowing(online, player) } catch (_: Exception) {}

                    if (online.location.distanceSquared(endLoc) <= 64.0) {
                        plugin.gameManager.combatManager.takeDamage(online)
                        val extraDamage = 3.0
                        online.health = max(0.0, online.health - extraDamage)
                        online.velocity = online.location.toVector().subtract(endLoc.toVector()).normalize().multiply(1.5).setY(0.5)
                    }
                }
            }
        }, null, 100L) // 100 ticks = 5 segundos
    }

    // --- ⚔️ IGNORAR ARMADURA DURANTE LA ULTIMATE ---

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onUltimateDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        if (plugin.gameManager.esAsesino(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
            if (isUltimateActive.contains(attacker.uniqueId) && esObjetivoValido(attacker, victim)) {
                val trueDamage = 2.0
                victim.health = max(0.0, victim.health - trueDamage)

                victim.world.spawnParticle(Particle.SCRAPE, victim.location.add(0.0, 1.0, 0.0), 10, 0.5, 0.5, 0.5, 0.1)
                victim.playSound(victim.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 2f)
            }
        }
    }

    // --- 🛠️ EQUIPAMIENTO Y LIMPIEZA ---

    override fun equipar(player: Player) {
        val inv = player.inventory
        inv.clear()
        inv.armorContents = arrayOfNulls(4)

        val langInfo = plugin.messageConfig.getSpecificFile(player, "asesinos_info")
        val configMecanica = plugin.configManager.getAsesinos()

        fun deliver(key: String, slot: Int, isArmor: Boolean = false) {
            val id = configMecanica.getString("$pathBase.armadura.$key") ?:
            configMecanica.getString("$pathBase.items.$key")

            if (id == null || id == "none") return

            val item = CraftEngineUtils.getCustomItem(id) ?: run {
                val mat = Material.matchMaterial(id.replace(".*:".toRegex(), "").uppercase())
                if (mat != null) ItemStack(mat) else null
            } ?: return

            val namePath = if (key == "arma") "asesinos.error_estatico.habilidades_nombres.arma"
            else "asesinos.error_estatico.habilidades_nombres.$key"

            langInfo.getString(namePath)?.let {
                item.editMeta { meta -> meta.displayName(mm.deserialize(it)) }
            }

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

        deliver("casco", 0, true)
        deliver("pechera", 0, true)
        deliver("pantalones", 0, true)
        deliver("botas", 0, true)
        deliver("habilidad1", 1)
        deliver("habilidad2", 2)
        deliver("habilidad3", 3)
        deliver("arma", 8)
    }

    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return

        if (Math.random() > 0.5) {
            player.world.spawnParticle(
                Particle.DUST,
                player.location.add(0.0, 0.2, 0.0),
                1, 0.2, 0.2, 0.2,
                DustOptions(Color.AQUA, 0.8f)
            )
        }
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            val uuid = it.uniqueId
            isUltimateActive.remove(uuid)
            passiveTicks.remove(uuid)

            plugin.server.onlinePlayers.forEach { target ->
                try { plugin.glowingAPI.unsetGlowing(target, it) } catch (_: Exception) {}
            }
        }
    }
}
