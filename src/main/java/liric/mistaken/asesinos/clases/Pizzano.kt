package liric.mistaken.asesinos.clases

import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.utils.CraftEngineUtils
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

/**
 *[LIRIC-MISTAKEN 2.0]
 * Pizzano: Asesino hiperactivo impulsado por azúcar (VERSIÓN DEV OP).
 * FIX: Pasiva de velocidad infinita de subida rápida y música anti-spam.
 */
class Pizzano : Asesino(
    "pizzano",
    Mistaken.instance.messageConfig.getRawString(null, "asesinos.pizzano.nombre", "<gradient:#ff4500:#ff8c00><b>PIZZANO</b></gradient>", "asesinos_info")
), Listener {

    private val pathBase = "asesinos.pizzano"
    private val itemKitCache = ConcurrentHashMap<String, ItemStack>()

    // Variables de la Pasiva (Subidón de Azúcar)
    private val lastLocations = ConcurrentHashMap<UUID, Location>()
    private val moveTicks = ConcurrentHashMap<UUID, Int>()
    private val sugarStacks = ConcurrentHashMap<UUID, Int>()

    // Variable para la Definitiva
    private val isUltimateActive = ConcurrentHashMap.newKeySet<UUID>()

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
            1 -> habilidadEmbestidaRegaliz(player)
            2 -> habilidadTornadoEspiral(player)
            3 -> habilidadEscapeFrenetico(player)
        }
        reproducirEfectosHabilidad(player, slot)
    }

    // --- 🏃‍♂️ PASIVA: SUBIDÓN DE AZÚCAR (VERSIÓN OP) ---

    override fun mostrarTrailFisico(player: Player) {
        val uuid = player.uniqueId
        if (!plugin.asesinoManager.esElAsesino(player)) { cleanup(player); return }

        // Lógica de movimiento
        val currentLoc = player.location
        val lastLoc = lastLocations[uuid]

        if (lastLoc != null && currentLoc.world == lastLoc.world) {
            val distSq = currentLoc.distanceSquared(lastLoc)

            // Si se movió lo suficiente (no está AFK)
            if (distSq > 0.01) {
                val ticks = moveTicks.getOrDefault(uuid, 0) + 1
                moveTicks[uuid] = ticks

                // 🔥 DEV BUFF: Sube de nivel cada 13 ticks (casi el triple de rápido que antes) y SIN LÍMITE.
                if (ticks % 13 == 0) {
                    val currentStacks = sugarStacks.getOrDefault(uuid, 0)
                    sugarStacks[uuid] = currentStacks + 1

                    // Limitar el pitch del sonido para que no se rompa el audio en Java
                    val pitch = (1.5f + (currentStacks * 0.05f)).coerceAtMost(2.0f)
                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, pitch)
                    player.world.spawnParticle(Particle.HAPPY_VILLAGER, player.location.add(0.0, 1.0, 0.0), 5, 0.3, 0.3, 0.3, 0.0)
                }
            } else {
                // Se detuvo. Pierde todo al instante.
                if (sugarStacks.getOrDefault(uuid, 0) > 0) {
                    player.playSound(player.location, Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1f)
                }
                moveTicks[uuid] = 0
                sugarStacks[uuid] = 0
            }
        }
        lastLocations[uuid] = currentLoc.clone()

        // Aplicar los bufos según las cargas
        val stacks = sugarStacks.getOrDefault(uuid, 0)
        if (stacks > 0) {
            // 🔥 DEV BUFF: Speed infinito según cargas
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 40, stacks - 1, false, false, false))

            // Limitamos el Haste para que no se bugee la animación de pegar, pero la velocidad de caminar no tiene tope.
            val hasteLevel = if (stacks > 4) 4 else stacks
            player.addPotionEffect(PotionEffect(PotionEffectType.HASTE, 40, hasteLevel, false, false, false))

            // Partículas de "Azúcar" (Máximo 20 partículas para no laguear el cliente si llega a stacks absurdos)
            val particulasVis = stacks.coerceAtMost(20)
            player.world.spawnParticle(Particle.CLOUD, player.location.add(0.0, 0.1, 0.0), particulasVis, 0.2, 0.0, 0.2, 0.05)
        }
    }

    // --- 🚀 HABILIDADES ACTIVAS ---

    private fun habilidadEmbestidaRegaliz(player: Player) {
        player.playSound(player.location, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f)
        val dir = player.location.direction.normalize().multiply(1.8).setY(0.2) // Disparo potente
        var ticks = 0
        var hitEntity = false

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 10 || !player.isOnline || hitEntity) {
                task.cancel()
                return@Consumer
            }

            player.velocity = dir

            // Rastro azul de regaliz
            player.world.spawnParticle(Particle.DUST, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.3, 0.3, DustOptions(Color.BLUE, 1.5f))

            // 1. Detección de colisión con Entidades (Choque)
            val victims = player.getNearbyEntities(1.5, 1.5, 1.5).filterIsInstance<Player>().filter { esObjetivoValido(player, it) }
            if (victims.isNotEmpty()) {
                hitEntity = true
                victims.forEach { victim ->
                    plugin.gameManager.combatManager.takeDamage(victim)
                    victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 20, 10, false, false, false))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false))
                    victim.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 20, 200, false, false, false))

                    val knockback = dir.clone().multiply(0.8).setY(0.4)
                    victim.velocity = knockback
                    victim.playSound(victim.location, Sound.ITEM_MACE_SMASH_GROUND, 1f, 1f)
                }
                task.cancel()
                return@Consumer
            }

            // 2. Detección de colisión con Paredes (Explosión AoE)
            val frontBlock = player.location.add(dir.clone().normalize().multiply(1.0)).block
            if (frontBlock.type.isSolid) {
                hitEntity = true
                val loc = player.location
                loc.world.spawnParticle(Particle.EXPLOSION, loc, 2)
                loc.world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f)

                loc.world.getNearbyPlayers(loc, 3.0).forEach { victim ->
                    if (esObjetivoValido(player, victim)) {
                        plugin.gameManager.combatManager.takeDamage(victim)
                        victim.velocity = victim.location.toVector().subtract(loc.toVector()).normalize().multiply(0.5).setY(0.3)
                    }
                }
                task.cancel()
                return@Consumer
            }

            ticks++
        }, null, 1L, 1L)
    }

    private fun habilidadTornadoEspiral(player: Player) {
        player.playSound(player.location, Sound.ENTITY_PHANTOM_FLAP, 1f, 1.5f)
        var ticks = 0

        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 60 || !player.isOnline) {
                task.cancel()
                return@Consumer
            }

            val loc = player.location
            val angle = ticks * 0.5

            for (i in 0..2) {
                val y = i * 0.8
                val radius = 0.5 + (i * 0.5)
                val x = radius * Math.cos(angle + i)
                val z = radius * Math.sin(angle + i)
                loc.world.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0)
                loc.world.spawnParticle(Particle.CLOUD, loc.clone().add(-x, y, -z), 2, 0.1, 0.1, 0.1, 0.0)
            }

            // Efecto de Atracción (Vacuum)
            loc.world.getNearbyPlayers(loc, 6.0).forEach { victim ->
                if (esObjetivoValido(player, victim)) {
                    val dist = victim.location.distanceSquared(loc)
                    if (dist > 1.0) {
                        val pullDir = loc.toVector().subtract(victim.location.toVector()).normalize().multiply(0.15)
                        victim.velocity = pullDir
                    }

                    if (ticks % 10 == 0) {
                        plugin.gameManager.combatManager.takeDamage(victim)
                        victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 0.5f, 1.5f)
                    }
                }
            }

            ticks++
        }, null, 1L, 1L)
    }

    private fun habilidadEscapeFrenetico(player: Player) {
        val uuid = player.uniqueId

        // 🔥 FIX MÚSICA ANTI-SPAM: Si ya está activa, no hacemos NADA (ni efectos ni sonidos nuevos).
        if (isUltimateActive.contains(uuid)) return

        isUltimateActive.add(uuid)

        player.world.getNearbyPlayers(player.location, 30.0).forEach {
            it.playSound(player.location, "mistaken:lms", 1.5f, 1f)
        }

        player.world.spawnParticle(Particle.FLASH, player.location, 1)

        var ticks = 0
        player.scheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= 160 || !player.isOnline) { // 8 segundos exactos (160 ticks)
                isUltimateActive.remove(uuid)
                task.cancel()
                return@Consumer
            }

            // 1. Inmunidad a la lentitud/aturdimiento
            player.removePotionEffect(PotionEffectType.SLOWNESS)
            player.removePotionEffect(PotionEffectType.BLINDNESS)
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 10, 2, false, false, false))

            // 2. Rastro de Regaliz Pegajoso (Piso falso)
            if (ticks % 3 == 0) {
                val trailLoc = player.location.clone()
                trailLoc.world.spawnParticle(Particle.FALLING_HONEY, trailLoc.add(0.0, 0.5, 0.0), 5, 0.3, 0.0, 0.3, 0.0)
                trailLoc.world.spawnParticle(Particle.DUST, trailLoc, 5, 0.3, 0.0, 0.3, DustOptions(Color.BLACK, 1f))

                trailLoc.world.getNearbyPlayers(trailLoc, 1.5).forEach { victim ->
                    if (esObjetivoValido(player, victim)) {
                        victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false, false))
                    }
                }
            }

            ticks++
        }, null, 1L, 1L)
    }

    // --- ⚔️ MODIFICADOR DE DAÑO DE LA DEFINITIVA ---

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPizzanoDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        // Verificar que sea Pizzano atacando, y que su definitiva esté activa
        if (plugin.gameManager.esAsesino(attacker.uniqueId) && this.id == plugin.playerDataManager.getSelectedKiller(attacker.uniqueId)) {
            if (isUltimateActive.contains(attacker.uniqueId)) {

                // +25% Daño Extra verdadero
                if (esObjetivoValido(attacker, victim)) {
                    val extraDamage = 1.0
                    val newHp = (victim.health - extraDamage).coerceAtLeast(0.0)
                    victim.health = newHp

                    victim.world.spawnParticle(Particle.DAMAGE_INDICATOR, victim.location.add(0.0, 1.0, 0.0), 3, 0.2, 0.2, 0.2, 0.1)
                }
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

            val namePath = if (key == "arma") "asesinos.${this.id}.habilidades_nombres.arma"
            else "asesinos.${this.id}.habilidades_nombres.$key"

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
        // habilidad4 (Pasiva, no se entrega ítem físico)
        deliver("arma", 8)
    }

    // --- 👣 RASTRO AL CAMINAR ---
    override fun mostrarTrail(player: Player) {
        if (player.velocity.lengthSquared() < 0.001) return

        player.world.spawnParticle(
            Particle.DUST,
            player.location.add(0.0, 0.2, 0.0),
            1, 0.1, 0.1, 0.1,
            DustOptions(Color.ORANGE, 0.8f)
        )
    }

    override fun cleanup(player: Player?) {
        super.cleanup(player)
        player?.let {
            val uuid = it.uniqueId
            lastLocations.remove(uuid)
            moveTicks.remove(uuid)
            sugarStacks.remove(uuid)
            isUltimateActive.remove(uuid)

            // 🔥 Detenemos la música si termina la partida y él la estaba escuchando
            it.world.players.forEach { p ->
                p.stopSound("mistaken:lms", SoundCategory.MASTER)
            }
        }
    }
}