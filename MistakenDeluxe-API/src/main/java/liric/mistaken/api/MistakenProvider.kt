package liric.mistaken.api

/**
 * [MistakenDeluxe]
 * Static provider for the Mistaken API.
 */
object MistakenProvider {
    private var instance: MistakenAPI? = null

    @JvmStatic
    fun get(): MistakenAPI {
        return instance ?: throw IllegalStateException("MistakenAPI has not been initialized by the Core yet.")
    }

    @JvmStatic
    fun isRegistered(): Boolean {
        return instance != null
    }

    fun register(api: MistakenAPI) {
        if (instance != null) {
            throw IllegalStateException("MistakenAPI is already registered.")
        }
        instance = api
    }
}
