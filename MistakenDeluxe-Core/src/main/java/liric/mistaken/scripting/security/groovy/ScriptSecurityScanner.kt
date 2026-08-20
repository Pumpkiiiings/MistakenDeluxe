package liric.mistaken.scripting.security.groovy

import org.bukkit.Bukkit

object ScriptSecurityScanner {

    private val BANNED_PATTERNS = listOf(
        Regex("""\bjava\.io\b"""),
        Regex("""\bjava\.net\b"""),
        Regex("""\bjava\.lang\.Runtime\b"""),
        Regex("""\bjava\.lang\.ProcessBuilder\b"""),
        Regex("""\bjava\.lang\.System\.exit\b"""),
        Regex("""\bjava\.lang\.Thread\.sleep\b"""),
        Regex("""\borg\.bukkit\.plugin\b"""),
        Regex("""\bwhile\s*\(\s*true\s*\)"""),
        Regex("""\bThread\s*\.\s*sleep\b""")
    )

    /**
     * Revisa el contenido de un script antes de compilarlo.
     * Retorna true si es seguro, false si detecta cÃ³digo sospechoso.
     */
    fun isSafe(scriptContent: String, scriptName: String): Boolean {
        for (pattern in BANNED_PATTERNS) {
            if (pattern.containsMatchIn(scriptContent)) {
                Bukkit.getLogger().severe("[Mistaken Script Engine] ALERTA: Script '$scriptName' denegado. Contiene patrÃ³n prohibido: ${pattern.pattern}")
                return false
            }
        }
        return true
    }
}

