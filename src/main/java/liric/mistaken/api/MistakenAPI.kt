package liric.mistaken.api

import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import org.bukkit.entity.Player
import java.util.*

/**
 *[LIRIC-MISTAKEN 2.0]
 * API Pública de Mistaken.
 * Permite a desarrolladores externos interactuar con el minijuego y registrar contenido custom.
 */
class MistakenAPI(private val plugin: Mistaken) {

    companion object {
        private var instance: MistakenAPI? = null

        /**
         * Obtiene la instancia de la API.
         * @throws IllegalStateException si el plugin aún no se ha cargado.
         */
        @JvmStatic
        fun getInstance(): MistakenAPI {
            return instance ?: throw IllegalStateException("MistakenAPI no está inicializada aún.")
        }

        // Método interno para que el plugin principal registre la instancia.
        internal fun init(plugin: Mistaken) {
            instance = MistakenAPI(plugin)
        }
    }

    // --- REGISTRO DE ADDONS ---

    /**
     * Registra una clase Asesino personalizada desde un plugin externo.
     * @param asesino La instancia de la clase que hereda de [Asesino].
     */
    fun registerCustomAssassin(asesino: Asesino) {
        val id = asesino.id.lowercase()

        // Evita sobreescribir los asesinos originales por accidente
        if (plugin.asesinoManager.catalogo.containsKey(id)) {
            plugin.componentLogger.warn(plugin.mm.deserialize("<yellow>[API] El addon intentó registrar un asesino que ya existe: $id</yellow>"))
            return
        }

        plugin.asesinoManager.registrarClase(asesino)
        plugin.componentLogger.info(plugin.mm.deserialize("<green>[API] Asesino externo registrado con éxito: ${asesino.nombre} ($id)</green>"))
    }

    // --- UTILIDADES PÚBLICAS ---

    /**
     * Devuelve true si la partida actual está en curso.
     */
    fun isGameRunning(): Boolean {
        return plugin.gameManager.currentState == liric.mistaken.game.enums.GameState.INGAME
    }

    /**
     * Verifica si un jugador es el asesino actual.
     */
    fun isAssassin(player: Player): Boolean {
        return plugin.gameManager.esAsesino(player.uniqueId)
    }

    /**
     * Obtiene al jugador que actualmente es el asesino.
     * @return El Player si está online, o null si no hay partida o está desconectado.
     */
    fun getCurrentAssassinPlayer(): Player? {
        return plugin.gameManager.getCurrentAsesino()
    }
}
