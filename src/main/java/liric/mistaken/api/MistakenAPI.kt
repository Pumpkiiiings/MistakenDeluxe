package liric.mistaken.api

import liric.mistaken.Mistaken
import liric.mistaken.asesinos.Asesino
import liric.mistaken.game.enums.GameState
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * [LIRIC-MISTAKEN 2.0]
 * API Pública de Mistaken.
 * Adaptada al nuevo sistema Multiarena / SessionManager.
 */
@Suppress("unused") // Quita la advertencia amarilla de getInstance
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
     * @param asesino La instancia de la clase que hereda de[Asesino].
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

    // --- UTILIDADES PÚBLICAS MULTIARENA ---

    /**
     * Devuelve true si la partida en la que se encuentra el jugador está en curso.
     * @param player El jugador del cual queremos comprobar su sesión.
     */
    fun isGameRunning(player: Player): Boolean {
        val session = plugin.sessionManager.getSession(player) ?: return false
        return session.currentState == GameState.INGAME
    }

    /**
     * Verifica si un jugador es el asesino en su partida actual.
     */
    fun isAssassin(player: Player): Boolean {
        val session = plugin.sessionManager.getSession(player) ?: return false
        return session.asesinosUUIDs.contains(player.uniqueId)
    }

    /**
     * Obtiene al jugador que es el asesino en la misma partida que el jugador dado.
     * @param player Un jugador cualquiera dentro de una arena.
     * @return El Player que es el asesino en esa arena, o null si no hay partida o está desconectado.
     */
    fun getAssassinInSession(player: Player): Player? {
        val session = plugin.sessionManager.getSession(player) ?: return null

        // Asumiendo que solo hay 1 asesino por arena, tomamos el primero
        val assassinUUID = session.asesinosUUIDs.firstOrNull() ?: return null
        return Bukkit.getPlayer(assassinUUID)
    }
}
