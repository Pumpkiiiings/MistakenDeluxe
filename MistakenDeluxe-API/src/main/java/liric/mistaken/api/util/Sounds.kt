package liric.mistaken.api.util

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound

/**
 * [LIRIC-MISTAKEN 2.0]
 * Resolucion de sonidos por nombre, para los que llegan desde configuration.
 *
 * Desde 1.21.x Sound dejo de ser un enum y paso a ser una interfaz respaldada por
 * Registry.SOUNDS. Sound.valueOf() sigue existiendo pero esta deprecado y acabara
 * desapareciendo, asi que todas las lecturas de sonidos desde YAML pasan por aqui:
 * el dia que se elimine solo hay un sitio que tocar.
 *
 * Acepta los dos formatos:
 *  - nombre estilo enum de siempre: "BLOCK_NOTE_BLOCK_XYLOPHONE"
 *  - clave de registro: "block.note_block.xylophone" o "minecraft:block.note_block.xylophone"
 */
object Sounds {

    /**
     * Indice nombre-de-enum -> Sound construido una sola vez desde el registro.
     * Evita Sound.valueOf y tambien recorrer ~1500 sonidos en cada lectura de config.
     *
     * El nombre de enum se deriva de la clave igual que lo hacia Bukkit al generar el
     * enum: "block.note_block.xylophone" -> "BLOCK_NOTE_BLOCK_XYLOPHONE". Asi no hace
     * falta llamar a name() ni a Keyed.getKey(), ambos deprecados: la clave se pide al
     * propio registro.
     */
    private val byLegacyName: Map<String, Sound> by lazy {
        Registry.SOUNDS.mapNotNull { sound ->
            Registry.SOUNDS.getKey(sound)?.let { key ->
                key.value().replace('.', '_').uppercase() to sound
            }
        }.toMap()
    }

    /** Devuelve null si el nombre esta vacio o no corresponde a ningun sonido. */
    fun orNull(name: String?): Sound? {
        val raw = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        byLegacyName[raw.uppercase()]?.let { return it }

        return runCatching {
            NamespacedKey.fromString(raw.lowercase())?.let { Registry.SOUNDS.get(it) }
        }.getOrNull()
    }

    /** Igual que [orNull] pero cayendo a un sonido conocido si el nombre no resuelve. */
    fun of(name: String?, fallback: Sound): Sound = orNull(name) ?: fallback
}
