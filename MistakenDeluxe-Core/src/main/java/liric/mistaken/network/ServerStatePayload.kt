package liric.mistaken.network

import com.google.gson.Gson

data class ServerStatePayload(
    val serverName: String,
    val state: String,
    val players: Int,
    val maxPlayers: Int,
    var lastUpdate: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): ServerStatePayload? {
            return try {
                Gson().fromJson(json, ServerStatePayload::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
