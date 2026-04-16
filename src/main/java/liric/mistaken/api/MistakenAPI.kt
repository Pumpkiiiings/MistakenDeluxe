package liric.mistaken.api

import liric.mistaken.Mistaken
import liric.mistaken.roles.asesinos.Asesino
import liric.mistaken.game.enums.GameState
import org.bukkit.entity.Player

/**
 * [LIRIC-MISTAKEN 2.0]
 * API Pública de Mistaken.
 * Adaptada a la arquitectura Multiarena de Paper 1.21.4+.
 */
@Suppress("unused")
class MistakenAPI(private val plugin: Mistaken) {

    companion object {
        private var instance: MistakenAPI? = null

        @JvmStatic
        fun getInstance(): MistakenAPI {
            return instance ?: throw IllegalStateException("MistakenAPI no está inicializada aún.")
        }

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
        val asesinoManager = plugin.asesinoManager
        if (asesinoManager == null) {
            plugin.componentLogger.warn(plugin.mm.deserialize("<yellow>[API] Addon intentó registrar un asesino en un servidor LOBBY. Ignorado.</yellow>"))
            return
        }

        val id = asesino.id.lowercase()

        if (asesinoManager.catalogo.containsKey(id)) {
            plugin.componentLogger.warn(plugin.mm.deserialize("<yellow>[API] El addon intentó registrar un asesino que ya existe: $id</yellow>"))
            return
        }

        asesinoManager.registrarClase(asesino)
        plugin.componentLogger.info(plugin.mm.deserialize("<green>[API] Asesino externo registrado con éxito: ${asesino.nombre} ($id)</green>"))
    }

    // --- UTILIDADES PÚBLICAS MULTIARENA ---

    fun isGameRunning(player: Player): Boolean {
        val session = plugin.sessionManager?.getSession(player) ?: return false
        return session.currentState == GameState.INGAME
    }

    fun isAssassin(player: Player): Boolean {
        val session = plugin.sessionManager?.getSession(player) ?: return false
        return session.asesinosUUIDs.contains(player.uniqueId)
    }

    fun getAssassinInSession(player: Player): Player? {
        val session = plugin.sessionManager?.getSession(player) ?: return null
        val assassinUUID = session.asesinosUUIDs.firstOrNull() ?: return null
        return plugin.server.getPlayer(assassinUUID)
    }
}
