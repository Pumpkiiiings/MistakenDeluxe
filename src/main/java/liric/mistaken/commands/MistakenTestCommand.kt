package liric.mistaken.commands

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import liric.mistaken.Mistaken
import liric.mistaken.game.entities.GeoffreyEXE
import liric.mistaken.game.entities.AmongUsEXE
import liric.mistaken.game.entities.PouEXE
import liric.mistaken.game.entities.Axolotl
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * [LIRIC-MISTAKEN 2.0] - MODO TEST SUPREMO
 * MistakenTestCommand: El patio de juegos prohibido de los Admins.
 * Ahora con soporte para Geoffrey, AmongUs, Pou y Axolotl Gigante.
 */
object MistakenTestCommand {

    // Rastreros de instancias para poder detenerlas
    private val activeGeoffreys = ConcurrentHashMap<Int, GeoffreyEXE>()
    private val activeSus = ConcurrentHashMap<Int, AmongUsEXE>()
    private val activePous = ConcurrentHashMap<Int, PouEXE>()
    private val activeAxos = ConcurrentHashMap<Int, Axolotl>()

    private var instanceCounter = 0

    fun get(plugin: Mistaken): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("mistakentest")
            .requires { it.sender.hasPermission("mistaken.admin") }

            // --- 1. COMANDO GEOFFREY ---
            .then(Commands.literal("geoffrey")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val id = instanceCounter++
                    val entity = GeoffreyEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activeGeoffreys[id] = entity
                    p.sendMessage("§4[!] §cAnomalía §lGEOFFREY.EXE §r§ciniciada.")
                    1
                })
            )

            // --- 2. COMANDO AMONG US (SUS) ---
            .then(Commands.literal("amongus")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val id = instanceCounter++
                    val entity = AmongUsEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activeSus[id] = entity
                    p.sendMessage("§c[!] §fHay un §lIMPOSTOR §fentre nosotros...")
                    1
                })
            )

            // --- 3. COMANDO POU ---
            .then(Commands.literal("pou")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val id = instanceCounter++
                    val entity = PouEXE(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activePous[id] = entity
                    p.sendMessage("§6[!] §eTu mascota virtual §lPOU.EXE §eha despertado.")
                    1
                })
            )

            // --- 4. COMANDO AXOLOTL ---
            .then(Commands.literal("axolotl")
                .then(Commands.literal("start").executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val id = instanceCounter++
                    val entity = Axolotl(plugin).apply { spawn(p.location.add(p.location.direction.multiply(-5))) }
                    activeAxos[id] = entity
                    p.sendMessage("§b[!] §3El §lAXOLOTL GIGANTE §3ha emergido.")
                    1
                })
            )

            // --- 5. 🔥 COMANDO SPAWN ALL (CHAOS MODE) 🔥 ---
            .then(Commands.literal("spawnall")
                .executes { ctx ->
                    val p = ctx.source.sender as? Player ?: return@executes 0
                    val loc = p.location

                    p.sendMessage("§4§l[!] ADVERTENCIA: §cIniciando protocolo de colapso de realidad...")

                    // Geoffrey
                    val gId = instanceCounter++
                    activeGeoffreys[gId] = GeoffreyEXE(plugin).apply { spawn(loc.clone().add(5.0, 0.0, 0.0)) }

                    // AmongUs
                    val sId = instanceCounter++
                    activeSus[sId] = AmongUsEXE(plugin).apply { spawn(loc.clone().add(-5.0, 0.0, 0.0)) }

                    // Pou
                    val poId = instanceCounter++
                    activePous[poId] = PouEXE(plugin).apply { spawn(loc.clone().add(0.0, 0.0, 5.0)) }

                    // Axolotl
                    val axId = instanceCounter++
                    activeAxos[axId] = Axolotl(plugin).apply { spawn(loc.clone().add(0.0, 0.0, -5.0)) }

                    p.showTitle(net.kyori.adventure.title.Title.title(
                        plugin.mm.deserialize("<dark_red><bold>CAOS TOTAL"),
                        plugin.mm.deserialize("<red>¡SUERTE SOBREVIVIENDO!")
                    ))
                    1
                }
            )

            // --- 6. COMANDO STOP (LIMPIEZA TOTAL) ---
            .then(Commands.literal("stop")
                .executes { ctx ->
                    val sender = ctx.source.sender

                    activeGeoffreys.values.forEach { it.remove() }
                    activeSus.values.forEach { it.remove() }
                    activePous.values.forEach { it.remove() }
                    activeAxos.values.forEach { it.remove() }

                    activeGeoffreys.clear()
                    activeSus.clear()
                    activePous.clear()
                    activeAxos.clear()

                    sender.sendMessage("§a§l[✔] §aTodas las anomalías han sido eliminadas del sistema.")
                    1
                }
            )
            .build()
    }
}
