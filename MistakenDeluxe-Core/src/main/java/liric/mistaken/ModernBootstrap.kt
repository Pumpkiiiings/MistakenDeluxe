package liric.mistaken

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import org.bukkit.plugin.java.JavaPlugin

/**
 * [LIRIC-MISTAKEN 2.0]
 * ModernBootstrap: El encargado de preparar el plugin antes de que el mundo cargue.
 */
class ModernBootstrap : PluginBootstrap {

    override fun bootstrap(context: BootstrapContext) {
        // Aquí puedes registrar cosas muy avanzadas si las ocupas,
        // por ahora lo dejamos listo para que el server no llore.
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        // Este vato le dice a Paper cuál es tu clase principal
        return Mistaken()
    }
}
