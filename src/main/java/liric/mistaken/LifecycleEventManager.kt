package liric.mistaken

import com.github.retrooper.packetevents.PacketEvents
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

class LifecycleEventManager : JavaPlugin() {

    override fun onLoad() {
        // Inicializar PacketEvents antes de que cargue el servidor
        PacketEvents.getAPI().init()
    }

    override fun onEnable() {
        val manager = this.lifecycleManager

        // Registro de comandos usando la nueva API de Brigadier (Paper)
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            // Aquí registraremos los comandos más adelante
        }

        logger.info("Mistaken reescrito en Kotlin (1.21.4+) activado con éxito.")
    }

    override fun onDisable() {
        PacketEvents.getAPI().terminate()
    }
}
