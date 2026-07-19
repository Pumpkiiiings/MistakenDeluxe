package pumpking.lib.color

import java.util.regex.Pattern

object ColorNormalizer {
    private val HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})")
    private val BUKKIT_HEX_PATTERN = Pattern.compile("§x(§[A-Fa-f0-9]){6}")

    // FIX #11: Was compiled inside normalizeToMiniMessage() on every call.
    // Pattern.compile() is expensive (NFA construction). Moved here as a compile-once constant.
    private val STANDALONE_HEX_PATTERN = Pattern.compile("(?<!<)#([A-Fa-f0-9]{6})")

    // Map of legacy color codes to MiniMessage tags
    private val LEGACY_MAP = mapOf(
        '0' to "<black>", '1' to "<dark_blue>", '2' to "<dark_green>", '3' to "<dark_aqua>",
        '4' to "<dark_red>", '5' to "<dark_purple>", '6' to "<gold>", '7' to "<gray>",
        '8' to "<dark_gray>", '9' to "<blue>", 'a' to "<green>", 'b' to "<aqua>",
        'c' to "<red>", 'd' to "<light_purple>", 'e' to "<yellow>", 'f' to "<white>",
        'k' to "<obfuscated>", 'l' to "<bold>", 'm' to "<strikethrough>", 'n' to "<underlined>",
        'o' to "<italic>", 'r' to "<reset>"
    )

    /**
     * Normalizes a mixed string (Legacy, Hex, MiniMessage) into pure MiniMessage syntax.
     */
    fun normalizeToMiniMessage(input: String): String {
        var text = input

        // 1. Convert &#RRGGBB or #RRGGBB to <#RRGGBB>
        // Catch &#FF0000
        var hexMatcher = HEX_PATTERN.matcher(text)
        val sbHex = StringBuffer()
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(sbHex, "<#${hexMatcher.group(1)}>")
        }
        hexMatcher.appendTail(sbHex)
        text = sbHex.toString()

        // Catch standalone #FF0000 if it's not already inside a tag like <#FF0000>
        // FIX #11: STANDALONE_HEX_PATTERN is now a pre-compiled constant.
        val standaloneHexMatcher = STANDALONE_HEX_PATTERN.matcher(text)
        val sbStand = StringBuffer()
        while (standaloneHexMatcher.find()) {
            standaloneHexMatcher.appendReplacement(sbStand, "<#${standaloneHexMatcher.group(1)}>")
        }
        standaloneHexMatcher.appendTail(sbStand)
        text = sbStand.toString()

        // 2. Normalize Bukkit §x§R§R§G§G§B§B -> <#RRGGBB>
        var bukkitHexMatcher = BUKKIT_HEX_PATTERN.matcher(text)
        val sbBukkit = StringBuffer()
        while (bukkitHexMatcher.find()) {
            val match = bukkitHexMatcher.group()
            val hex = buildString {
                for (i in 2 until match.length step 2) {
                    append(match[i + 1])
                }
            }
            bukkitHexMatcher.appendReplacement(sbBukkit, "<#$hex>")
        }
        bukkitHexMatcher.appendTail(sbBukkit)
        text = sbBukkit.toString()

        // 3. Convert Legacy &a / §a to MiniMessage tags
        val sbLegacy = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if ((c == '&' || c == '§') && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
                if (LEGACY_MAP.containsKey(code)) {
                    sbLegacy.append(LEGACY_MAP[code])
                    i += 2
                    continue
                }
            }
            sbLegacy.append(c)
            i++
        }

        return sbLegacy.toString()
    }
}
