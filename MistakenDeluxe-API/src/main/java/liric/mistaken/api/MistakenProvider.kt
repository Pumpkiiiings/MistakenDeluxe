package liric.mistaken.api

/**
 * [MistakenDeluxe]
 * Proveedor estático de la API de Mistaken.
 */
object MistakenProvider {
    private var instance: MistakenAPI? = null

    @JvmStatic
    fun get(): MistakenAPI {
        return instance ?: throw IllegalStateException("MistakenAPI aún no ha sido inicializada por el Core.")
    }

    fun register(api: MistakenAPI) {
        if (instance != null) {
            throw IllegalStateException("MistakenAPI ya está registrada.")
        }
        instance = api
    }
}
