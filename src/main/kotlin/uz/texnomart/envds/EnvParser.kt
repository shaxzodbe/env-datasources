package uz.texnomart.envds

/**
 * Разбор `.env` в стиле dotenv/Laravel: `export`, комментарии, кавычки,
 * подстановка `${VAR}` из уже прочитанных значений.
 */
object EnvParser {

    fun parse(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val body = line.removePrefix("export ").trimStart()
            val eq = body.indexOf('=')
            if (eq <= 0) continue

            val key = body.substring(0, eq).trim()
            if (!key.matches(KEY_REGEX)) continue

            result[key] = readValue(body.substring(eq + 1).trim(), result)
        }
        return result
    }

    private val KEY_REGEX = Regex("[A-Za-z_][A-Za-z0-9_.]*")

    private fun readValue(raw: String, known: Map<String, String>): String = when {
        raw.startsWith("'") && raw.length > 1 -> raw.substring(1, closingQuote(raw, '\''))
        raw.startsWith("\"") && raw.length > 1 ->
            interpolate(unescape(raw.substring(1, closingQuote(raw, '"'))), known)
        else -> interpolate(stripInlineComment(raw), known)
    }

    private fun closingQuote(raw: String, quote: Char): Int {
        var i = 1
        while (i < raw.length) {
            if (raw[i] == '\\' && quote == '"') {
                i += 2
                continue
            }
            if (raw[i] == quote) return i
            i++
        }
        return raw.length
    }

    /** Незакавыченное значение обрывается на ` #`. */
    private fun stripInlineComment(raw: String): String {
        val idx = raw.indexOf(" #")
        return (if (idx >= 0) raw.substring(0, idx) else raw).trim()
    }

    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                i++
                sb.append(
                    when (val next = value[i]) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> next
                    }
                )
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private val VAR_REGEX = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?}""")

    private fun interpolate(value: String, known: Map<String, String>): String =
        VAR_REGEX.replace(value) { match ->
            known[match.groupValues[1]] ?: match.groupValues[2]
        }
}
