package liric.mistaken.game.managers

import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

class CinematicManager(private val plugin: Mistaken) {

    private val activeDisplays = ConcurrentHashMap.newKeySet<Entity>()

    // =========================================================================================
    // =                                   INTRO DEL ASESINO                                   =
    // =========================================================================================

    fun playKillerIntro(killer: Player, asesino: Asesino) {
        val centerLoc = killer.location.clone()
        val isFloating = asesino.id.lowercase() == "romeo" || asesino.id.lowercase() == "romeodebuff"
        centerLoc.add(0.0, if (isFloating) 2.5 else 0.0, 0.0)

        // 1. Dummy de Intro
        val visualDummy = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) { dummy ->
            dummy.isInvisible = false
            dummy.setGravity(!isFloating)
            dummy.isMarker = true
            dummy.setArms(true)
            dummy.setBasePlate(false)
            aplicarPose(dummy, asesino.id.lowercase(), isIntro = true)
            copiarEquipamiento(killer, dummy, asesino.id.lowercase())
        }

        // 2. Cámara (Anchor)
        val cameraAnchor = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) {
            it.isInvisible = true; it.setGravity(false); it.isMarker = true
        }

        // 3. Títulos y Textos de Intro
        val titlePair = obtenerTextosIntro(asesino.id.lowercase(), asesino.nombre)
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(6), Duration.ofMillis(1000))

        plugin.server.onlinePlayers.forEach { p ->
            p.gameMode = GameMode.SPECTATOR
            p.teleport(centerLoc)

            p.scheduler.runDelayed(plugin, { _ ->
                p.spectatorTarget = cameraAnchor
                p.showTitle(Title.title(titlePair.first, titlePair.second, times))
            }, null, 2L)

            if (p == killer) p.isInvisible = true
        }

        // 4. Ejecutar Diálogos y Efectos
        ejecutarEfectos(asesino, centerLoc, visualDummy, isIntro = true)
        iniciarRutaDeCamara(killer, cameraAnchor, visualDummy, centerLoc, duracionTicks = 160) // 8 segundos
    }

    // =========================================================================================
    // =                                   OUTRO DEL ASESINO                                   =
    // =========================================================================================

    fun playKillerOutro(killer: Player, asesino: Asesino) {
        val isFloating = asesino.id.lowercase() == "romeo" || asesino.id.lowercase() == "romeodebuff"
        val centerLoc = killer.location.clone().add(0.0, if (isFloating) 2.5 else 0.0, 0.0)

        val visualDummy = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) { dummy ->
            dummy.isInvisible = false
            dummy.setGravity(!isFloating)
            dummy.isMarker = true
            dummy.setArms(true)
            dummy.setBasePlate(false)
            aplicarPose(dummy, asesino.id.lowercase(), isIntro = false)
            copiarEquipamiento(killer, dummy, asesino.id.lowercase())
        }

        val cameraAnchor = centerLoc.world.spawn(centerLoc, ArmorStand::class.java) {
            it.isInvisible = true; it.setGravity(false); it.isMarker = true
        }

        val title = plugin.mm.deserialize("<dark_red><bold>¡MASCARADA FINAL!</bold>")
        val subtitle = plugin.mm.deserialize("<gray><b>${asesino.nombre}</b> <white>ha reclamado todas las almas.")
        val times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(8), Duration.ofMillis(1000))

        plugin.server.onlinePlayers.forEach { p ->
            p.gameMode = GameMode.SPECTATOR
            p.teleport(centerLoc)

            p.scheduler.runDelayed(plugin, { _ ->
                p.spectatorTarget = cameraAnchor
                p.showTitle(Title.title(title, subtitle, times))
            }, null, 2L)

            if (p == killer) p.isInvisible = true
        }

        ejecutarEfectos(asesino, centerLoc, visualDummy, isIntro = false)
        iniciarRutaDeCamara(killer, cameraAnchor, visualDummy, centerLoc, duracionTicks = 200) // 10 segundos
    }

    // =========================================================================================
    // =                                 LÓGICA COMPARTIDA                                     =
    // =========================================================================================

    private fun iniciarRutaDeCamara(killer: Player, anchor: ArmorStand, dummy: ArmorStand, center: Location, duracionTicks: Int) {
        var ticks = 0

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (ticks >= duracionTicks || !killer.isOnline || !anchor.isValid) {
                plugin.server.onlinePlayers.forEach {
                    it.spectatorTarget = null
                    if (it == killer) it.isInvisible = false
                }
                anchor.remove()
                dummy.remove()
                activeDisplays.forEach { if (it.isValid) it.remove() }
                activeDisplays.clear()
                task.cancel()
                return@Consumer
            }

            // Órbita circular
            val angulo = ticks * 0.04
            val radio = 6.5
            val camLoc = center.clone().add(radio * cos(angulo), 2.5 + (ticks * 0.005), radio * sin(angulo))
            camLoc.direction = center.clone().add(0.0, 1.2, 0.0).toVector().subtract(camLoc.toVector())

            anchor.teleport(camLoc)
            ticks++
        }, 1L, 1L)
    }

    private fun aplicarPose(dummy: ArmorStand, id: String, isIntro: Boolean) {
        when (id) {
            "sowoul" -> {
                if (isIntro) {
                    // Pose de Bienvenida
                    dummy.leftArmPose = EulerAngle(Math.toRadians(-60.0), Math.toRadians(-45.0), 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-60.0), Math.toRadians(45.0), 0.0)
                } else {
                    // Pose de Reverencia (Agradecimiento)
                    dummy.bodyPose = EulerAngle(Math.toRadians(20.0), 0.0, 0.0)
                    dummy.headPose = EulerAngle(Math.toRadians(30.0), 0.0, 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(-45.0), 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(45.0), 0.0, 0.0)
                }
            }
            "colorandelectricity" -> {
                if (isIntro) { // Preparando poder
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-90.0), Math.toRadians(-45.0), 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(-90.0), Math.toRadians(45.0), 0.0)
                } else { // Pose Triunfal
                    dummy.headPose = EulerAngle(Math.toRadians(-15.0), 0.0, 0.0)
                    dummy.rightArmPose = EulerAngle(0.0, 0.0, Math.toRadians(120.0))
                    dummy.leftArmPose = EulerAngle(0.0, 0.0, Math.toRadians(-120.0))
                }
            }
            "pizzano" -> {
                if (isIntro) { // Listo para correr
                    dummy.bodyPose = EulerAngle(Math.toRadians(15.0), 0.0, 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-60.0), 0.0, 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(60.0), 0.0, 0.0)
                } else { // Pose Relajada
                    dummy.headPose = EulerAngle(Math.toRadians(-10.0), Math.toRadians(20.0), 0.0)
                    dummy.rightArmPose = EulerAngle(Math.toRadians(-120.0), Math.toRadians(45.0), 0.0)
                    dummy.leftArmPose = EulerAngle(Math.toRadians(-20.0), 0.0, 0.0)
                }
            }
            "romeo", "romeodebuff" -> {
                dummy.rightArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(-45.0), 0.0)
                dummy.leftArmPose = EulerAngle(Math.toRadians(-45.0), Math.toRadians(45.0), 0.0)
            }
            "slasher", "entity303" -> dummy.rightArmPose = EulerAngle(Math.toRadians(-120.0), Math.toRadians(30.0), 0.0)
            "herobrine", "nullasesino" -> {
                dummy.headPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
                dummy.rightArmPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
                dummy.leftArmPose = EulerAngle(Math.toRadians(10.0), 0.0, 0.0)
            }
            else -> {
                dummy.leftArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
                dummy.rightArmPose = EulerAngle(Math.toRadians(-100.0), 0.0, 0.0)
            }
        }
    }

    private fun copiarEquipamiento(killer: Player, dummy: ArmorStand, id: String) {
        val inv = killer.inventory
        dummy.setItem(EquipmentSlot.HEAD, inv.helmet)
        dummy.setItem(EquipmentSlot.CHEST, inv.chestplate)
        dummy.setItem(EquipmentSlot.LEGS, inv.leggings)
        dummy.setItem(EquipmentSlot.FEET, inv.boots)
        if (id != "sowoul" && id != "pizzano") dummy.setItem(EquipmentSlot.HAND, inv.itemInMainHand)
    }

    private fun ejecutarEfectos(asesino: Asesino, loc: Location, dummy: ArmorStand, isIntro: Boolean) {
        val world = loc.world ?: return
        val id = asesino.id.lowercase()

        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0, 1.0, 0.0), 3)
        val dialogos = obtenerDialogos(id, isIntro)

        // Enviar diálogo en ActionBar progresivamente
        var tickDial = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!dummy.isValid) { task.cancel(); return@Consumer }
            val index = (tickDial / 40) % dialogos.size // Cambia frase cada 2 segundos
            if (tickDial < dialogos.size * 40) {
                val comp = plugin.mm.deserialize(dialogos[index])
                plugin.server.onlinePlayers.forEach { it.sendActionBar(comp) }
            }
            tickDial++
        }, 1L, 1L)

        // Efectos Visuales
        when (id) {
            "sowoul" -> {
                if (isIntro) {
                    world.playSound(loc, Sound.ITEM_TRIDENT_RETURN, 2f, 1f)
                    spawnRotatingItem(loc.clone().add(0.0, 1.5, 0.0), Material.PAPER, 1.5f)
                    world.spawnParticle(Particle.WITCH, loc, 100, 2.0, 1.0, 2.0, 0.1)
                } else {
                    world.playSound(loc, Sound.ENTITY_VILLAGER_CELEBRATE, 2f, 1f)
                    world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 2f, 1f)
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) { task.cancel(); return@Consumer }
                        val roseLoc = loc.clone().add(Math.random() * 8 - 4, 6.0, Math.random() * 8 - 4)
                        spawnFallingItem(roseLoc, Material.POPPY)
                        world.spawnParticle(Particle.HEART, roseLoc, 1)
                    }, 1L, 5L)
                }
            }
            "charlieinferno", "herobrine" -> {
                world.playSound(loc, Sound.ENTITY_GHAST_SCREAM, 2f, 0.5f)
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) { task.cancel(); return@Consumer }
                    val angle = Math.random() * Math.PI * 2
                    val radius = Math.random() * 5 + 2
                    val pLoc = loc.clone().add(radius * cos(angle), 0.0, radius * sin(angle))

                    world.spawnParticle(Particle.FLAME, pLoc, 10, 0.2, 2.0, 0.2, 0.1)
                    world.spawnParticle(Particle.LAVA, pLoc, 2)
                    if (id == "herobrine" && Math.random() > 0.8) world.strikeLightningEffect(pLoc)
                }, 1L, 2L)
            }
            "colorandelectricity" -> {
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1f)
                val colors = listOf(Color.RED, Color.YELLOW, Color.AQUA, Color.LIME, Color.FUCHSIA)
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) { task.cancel(); return@Consumer }
                    val pLoc = loc.clone().add(Math.random() * 10 - 5, Math.random() * 5, Math.random() * 10 - 5)
                    world.spawnParticle(Particle.DUST, pLoc, 5, 0.5, 0.5, 0.5, Particle.DustOptions(colors.random(), 2f))
                    world.spawnParticle(Particle.ELECTRIC_SPARK, pLoc, 3, 0.1, 0.1, 0.1, 0.5)
                    if (Math.random() > 0.9) world.strikeLightningEffect(pLoc.clone().apply { y = loc.y })
                }, 1L, 3L)
            }
            "romeo", "romeodebuff" -> {
                world.playSound(loc, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 2f)
                spawnOrbitingBlock(loc, Material.COMMAND_BLOCK, 2.0f, 4.0, 0.05, 1.0)
                spawnOrbitingBlock(loc, Material.BEDROCK, 1.5f, 3.5, -0.06, -1.0)
                spawnOrbitingBlock(loc, Material.REDSTONE_BLOCK, 1.0f, 5.0, 0.08, 0.0)
            }
            "pizzano" -> {
                if (isIntro) {
                    world.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1.5f, 1f)
                    world.spawnParticle(Particle.CLOUD, loc, 100, 1.0, 1.0, 1.0, 0.5)
                } else {
                    world.playSound(loc, Sound.ENTITY_PLAYER_BURP, 1.5f, 0.8f)
                    spawnRotatingItem(loc.clone().add(1.5, 1.0, 0.0), Material.COOKIE, 1.5f)
                    spawnRotatingItem(loc.clone().add(-1.5, 1.5, 0.0), Material.SUGAR, 1.5f)
                    plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                        if (!dummy.isValid) { task.cancel(); return@Consumer }
                        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0.0, 0.1, 0.0), 2, 0.5, 0.5, 0.5, 0.05)
                    }, 1L, 10L)
                }
            }
            "nullasesino" -> {
                world.playSound(loc, Sound.AMBIENT_CAVE, 2f, 0.5f)
                var dTicks = 0
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) { task.cancel(); return@Consumer }
                    world.spawnParticle(Particle.SQUID_INK, loc.clone().add(0.0, dTicks * 0.02, 0.0), 20, 1.5, 0.5, 1.5, 0.0)
                    world.spawnParticle(Particle.SMOKE, loc, 30, 2.0, 2.0, 2.0, 0.05)
                    if (!isIntro && dTicks == 100) {
                        dummy.isInvisible = true
                        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.5f)
                    }
                    dTicks++
                }, 1L, 1L)
            }
            "entity303" -> {
                world.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 2f)
                plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
                    if (!dummy.isValid) { task.cancel(); return@Consumer }
                    if (Math.random() > 0.85) {
                        val tntLoc = loc.clone().add(Math.random() * 10 - 5, Math.random() * 6 + 2, Math.random() * 10 - 5)
                        spawnGlitchBlock(tntLoc, Material.TNT)
                        world.playSound(tntLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f)
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, tntLoc, 1)
                    }
                }, 1L, 5L)
            }
            "slasher" -> {
                world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f)
                world.spawnParticle(Particle.BLOCK, loc.clone().add(0.0, 1.0, 0.0), 500, 2.0, 2.0, 2.0, Material.REDSTONE_BLOCK.createBlockData())
                spawnGlitchBlock(loc.clone().add(0.0, 2.0, 0.0), Material.NETHER_WART_BLOCK)
            }
            "errorestatico" -> {
                world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.1f)
                spawnGlitchBlock(loc, Material.BLACK_CONCRETE)
                spawnGlitchBlock(loc, Material.CYAN_CONCRETE)
            }
            else -> spawnRotatingItem(loc.clone().add(0.0, 2.0, 0.0), Material.NETHER_STAR, 2.0f)
        }
    }

    // --- TEXTOS ---

    private fun obtenerTextosIntro(id: String, nombreReal: String): Pair<net.kyori.adventure.text.Component, net.kyori.adventure.text.Component> {
        return when (id) {
            "sowoul" -> Pair(plugin.mm.deserialize("<dark_purple>EL MAGO HA LLEGADO"), plugin.mm.deserialize("<light_purple>Que comience el espectáculo..."))
            "pizzano" -> Pair(plugin.mm.deserialize("<gold>¡TIEMPO DE AZÚCAR!"), plugin.mm.deserialize("<yellow>Nadie puede alcanzarme."))
            "romeo", "romeodebuff" -> Pair(plugin.mm.deserialize("<dark_red>EL ADMINISTRADOR"), plugin.mm.deserialize("<red>Este mundo me pertenece."))
            "slasher" -> Pair(plugin.mm.deserialize("<dark_red>LA EJECUCIÓN"), plugin.mm.deserialize("<red>Nadie escapa de mi hacha."))
            "entity303" -> Pair(plugin.mm.deserialize("<white><obfuscated>||</obfuscated> ERROR <obfuscated>||</obfuscated>"), plugin.mm.deserialize("<gray>S1st3m4 C0rrupt0..."))
            else -> Pair(plugin.mm.deserialize("<red>LA CAZA COMIENZA"), plugin.mm.deserialize("<gray>El Asesino es: $nombreReal"))
        }
    }

    private fun obtenerDialogos(id: String, isIntro: Boolean): List<String> {
        return when (id) {
            "sowoul" -> if (isIntro) listOf("<light_purple>Damas y caballeros...", "<light_purple>¡Bienvenidos a la función final!")
            else listOf("<light_purple>¡Muchas gracias audiencia!", "<light_purple>¡Gracias por ver mi gran espectáculo!")
            "pizzano" -> if (isIntro) listOf("<yellow>¡Demasiada energía!", "<gold>¡Corran, corran, corran!")
            else listOf("<yellow>Ufff... eso fue un buen ejercicio.", "<gold>¿Alguien tiene un poco de azúcar?")
            "colorandelectricity" -> if (isIntro) listOf("<aqua>No no no...", "<yellow>Que le ha pasado a mi color... Quiero volver a ser normal.")
            else listOf("<yellow>Todo se ha apagado.", "<aqua>Yo soy la única luz que queda.")
            "romeo", "romeodebuff" -> if (isIntro) listOf("<dark_red>¿Creen que pueden vencerme?", "<red>Yo escribí las reglas de este mundo.")
            else listOf("<dark_red>Patético.", "<red>Nadie supera a un Admin.")
            "entity303" -> listOf("<gray><obfuscated>xd</obfuscated> D3str0y_W0rld.exe Iniciado...", "<dark_red>Su existencia ha sido borrada.")
            "slasher" -> listOf("<dark_red>Más sangre para mi hacha...", "<red>Griten todo lo que quieran.")
            "errorestatico" -> listOf("<dark_aqua>T H I S   I S   H O W   I T   S H O U L D   B E.")
            "charlieinferno" -> listOf("<gold>Todo arde...", "<red>¡Y ustedes arderán conmigo!")
            "nullasesino" -> listOf("<dark_gray>...", "<black>...")
            else -> listOf("<red>No tienen escapatoria.", "<dark_red>Es el fin.")
        }
    }

    // --- FUNCIONES UTILS (Spawn Displays) ---
    private fun spawnFallingItem(loc: Location, mat: Material) {
        val display = loc.world.spawn(loc, ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(0.8f, 0.8f, 0.8f), Quaternionf())
        }
        activeDisplays.add(display)
        var yOffset = 0.0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid || yOffset < -6.0) { display.remove(); task.cancel(); return@Consumer }
            yOffset -= 0.15
            val t = display.transformation; t.leftRotation.rotateX(0.2f).rotateY(0.1f)
            display.transformation = t; display.teleport(loc.clone().add(0.0, yOffset, 0.0))
        }, 1L, 1L)
    }

    private fun spawnOrbitingBlock(center: Location, mat: Material, scale: Float, radius: Double, speed: Double, yOffset: Double) {
        val display = center.world.spawn(center, BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(Vector3f(-scale/2, 0f, -scale/2), Quaternionf(), Vector3f(scale, scale, scale), Quaternionf())
        }
        activeDisplays.add(display)
        var angle = Math.random() * Math.PI * 2
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) { task.cancel(); return@Consumer }
            angle += speed
            val offsetLoc = center.clone().add(radius * cos(angle), 2.0 + yOffset + sin(angle * 2) * 0.5, radius * sin(angle))
            offsetLoc.direction = center.clone().add(0.0, 2.0, 0.0).toVector().subtract(offsetLoc.toVector())
            display.teleport(offsetLoc)
        }, 1L, 1L)
    }

    private fun spawnRotatingItem(loc: Location, mat: Material, scale: Float) {
        val display = loc.world.spawn(loc.clone().add(0.0, 1.2, 0.0), ItemDisplay::class.java) { id ->
            id.setItemStack(ItemStack(mat))
            id.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(scale, scale, scale), Quaternionf())
        }
        activeDisplays.add(display)
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) { task.cancel(); return@Consumer }
            val t = display.transformation; t.leftRotation.rotateY(0.1f); display.transformation = t
        }, 1L, 1L)
    }

    private fun spawnGlitchBlock(loc: Location, mat: Material) {
        val display = loc.world.spawn(loc.clone().add(0.0, 1.2, 0.0), BlockDisplay::class.java) { bd ->
            bd.block = mat.createBlockData()
            bd.transformation = Transformation(Vector3f(-0.5f, 0f, -0.5f), Quaternionf(), Vector3f(1.0f, 1.0f, 1.0f), Quaternionf())
        }
        activeDisplays.add(display)
        var ticks = 0
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, Consumer { task ->
            if (!display.isValid) { task.cancel(); return@Consumer }
            ticks++
            val s = if (ticks % 3 == 0) 1.5f else 0.8f
            display.transformation = Transformation(Vector3f(-s/2, 0f, -s/2), Quaternionf(), Vector3f(s, s, s), Quaternionf())
        }, 1L, 2L)
    }
}
