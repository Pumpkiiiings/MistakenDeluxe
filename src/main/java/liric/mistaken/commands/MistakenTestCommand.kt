package liric.mistaken.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.entities.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TEST SUPREMO
 * MistakenTestCommand: El patio de juegos prohibido de los Admins.
 */
object MistakenTestCommand {

    private val activeGeoffreys = ConcurrentHashMap<Int, GeoffreyEXE>()
    private val activeSus = ConcurrentHashMap<Int, AmongUsEXE>()
    private val activePous = ConcurrentHashMap<Int, PouEXE>()
    private val activeAxos = ConcurrentHashMap<Int, Axolotl>()
    private val activeObservants = ConcurrentHashMap<Int, ObservantEXE>()
    private val activeEyedrooms = ConcurrentHashMap<Int, EyedroomsEXE>()

    private var instanceCounter = 0

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("mistakentest")
            .requires { it.sender.hasPermission("mistaken.admin") }

            // --- 🔥 NUEVO: COMANDO IGNORE ---
            .then(Commands.literal("ignore")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests { _, builder ->
                        Bukkit.getOnlinePlayers().forEach { builder.suggest(it.name) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        val targetName = StringArgumentType.getString(ctx, "player")
                        val target = Bukkit.getPlayer(targetName)

                        if (target == null) {
                            sender.sendMessage("§cEse plebe no anda por aquí, pariente.")
                            return@executes 0
                        }

                        val uuid = target.uniqueId
                        if (plugin.ignoredTestPlayers.contains(uuid)) {
                            plugin.ignoredTestPlayers.remove(uuid)
                            sender.sendMessage("§a[!] §e${target.name} §fya no es ignorado por las anomalías.")
                        } else {
                            plugin.ignoredTestPlayers.add(uuid)
                            sender.sendMessage("§c[!] §e${target.name} §fahora es invisible para los entes .EXE")
                        }
                        1
                    }
                )
            )

            // --- 1. GEOFFREY ---
            .then(Commands.literal("geoffrey")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val entity = GeoffreyEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activeGeoffreys[instanceCounter++] = entity
                    p.sendMessage("§4[!] §cAnomalía §lGEOFFREY.EXE §r§ciniciada.")
                    1
                })
            )

            // --- 2. AMONG US (SUS) ---
            .then(Commands.literal("amongus")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val entity = AmongUsEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activeSus[instanceCounter++] = entity
                    p.sendMessage("§c[!] §fHay un §lIMPOSTOR §fentre nosotros...")
                    1
                })
            )

            // --- 3. POU ---
            .then(Commands.literal("pou")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val entity = PouEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activePous[instanceCounter++] = entity
                    p.sendMessage("§6[!] §eTu mascota virtual §lPOU.EXE §eha despertado.")
                    1
                })
            )

            // --- 4. AXOLOTL ---
            .then(Commands.literal("axolotl")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val entity = Axolotl(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activeAxos[instanceCounter++] = entity
                    p.sendMessage("§b[!] §3El §lAXOLOTL GIGANTE §3ha emergido.")
                    1
                })
            )

            // --- 5. OBSERVANT ---
            .then(Commands.literal("observant")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val entity = ObservantEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-10))) }
                    activeObservants[instanceCounter++] = entity
                    p.sendMessage("§8[!] §7Sientes que alguien te observa... §lOBSERVANT.EXE")
                    1
                })
            )

            // --- 6. EYEDROOMS ---
            .then(Commands.literal("eyedrooms")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val entity = EyedroomsEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activeEyedrooms[instanceCounter++] = entity
                    p.sendMessage("§5[!] §dEl ojo de los Backrooms te está vigilando... §lEYEDROOMS.EXE")
                    1
                })
            )

            // --- 7. 🔥 SPAWN ALL ---
            .then(Commands.literal("spawnall")
                .executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val loc = p.location
                    p.sendMessage("§4§l[!] ADVERTENCIA: §cIniciando colapso de realidad... 6 ANOMALÍAS DETECTADAS.")

                    activeGeoffreys[instanceCounter++] = GeoffreyEXE(plugin).apply { spawn(loc.clone().add(5.0, 0.0, 0.0)) }
                    activeSus[instanceCounter++] = AmongUsEXE(plugin).apply { spawn(loc.clone().add(-5.0, 0.0, 0.0)) }
                    activePous[instanceCounter++] = PouEXE(plugin).apply { spawn(loc.clone().add(0.0, 0.0, 5.0)) }
                    activeAxos[instanceCounter++] = Axolotl(plugin).apply { spawn(loc.clone().add(0.0, 0.0, -5.0)) }
                    activeObservants[instanceCounter++] = ObservantEXE(plugin).apply { spawn(loc.clone().add(0.0, 10.0, 0.0)) }
                    activeEyedrooms[instanceCounter++] = EyedroomsEXE(plugin).apply { spawn(loc.clone().add(5.0, 5.0, 5.0)) }

                    p.showTitle(net.kyori.adventure.title.Title.title(
                        plugin.mm.deserialize("<dark_red><bold>APOCALIPSIS EXE"),
                        plugin.mm.deserialize("<red>6 ENTES HAN APARECIDO. No sobreviviras...")
                    ))
                    1
                }
            )

            // --- 8. STOP ---
// --- EL MERO MERO COMANDO DE STOP ---
            .then(Commands.literal("stop")
                .executes { ctx ->
                    val sender = ctx.source.sender

                    // Borramos a los vatos uno por uno por su especie
                    activeGeoffreys.values.forEach { it.remove() }
                    activeGeoffreys.clear()

                    activeSus.values.forEach { it.remove() }
                    activeSus.clear()

                    activePous.values.forEach { it.remove() }
                    activePous.clear()

                    activeAxos.values.forEach { it.remove() }
                    activeAxos.clear()

                    activeObservants.values.forEach { it.remove() }
                    activeObservants.clear()

                    activeEyedrooms.values.forEach { it.remove() }
                    activeEyedrooms.clear()

                    sender.sendMessage("§a§l[✔] §aProtocolo de contención exitoso.")
                    1
                }
            )
            .build()
    }
}
