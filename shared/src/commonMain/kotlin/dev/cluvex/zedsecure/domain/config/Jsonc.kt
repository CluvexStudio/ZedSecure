package dev.cluvex.zedsecure.domain.config

object Jsonc {
    fun strip(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        val n = input.length
        var inString = false
        var quote = ' '

        while (i < n) {
            val c = input[i]

            if (inString) {
                out.append(c)
                if (c == '\\' && i + 1 < n) {
                    out.append(input[i + 1])
                    i += 2
                    continue
                }
                if (c == quote) inString = false
                i++
                continue
            }

            when {
                c == '"' || c == '\'' -> {
                    inString = true
                    quote = c
                    out.append(c)
                    i++
                }
                c == '/' && i + 1 < n && input[i + 1] == '/' -> {
                    i += 2
                    while (i < n && input[i] != '\n') i++
                }
                c == '/' && i + 1 < n && input[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(input[i] == '*' && input[i + 1] == '/')) i++
                    i += 2
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }

        return removeTrailingCommas(out.toString())
    }

    private fun removeTrailingCommas(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        val n = s.length
        var inString = false
        var quote = ' '
        while (i < n) {
            val c = s[i]
            if (inString) {
                out.append(c)
                if (c == '\\' && i + 1 < n) {
                    out.append(s[i + 1]); i += 2; continue
                }
                if (c == quote) inString = false
                i++
                continue
            }
            if (c == '"' || c == '\'') {
                inString = true; quote = c; out.append(c); i++; continue
            }
            if (c == ',') {
                var j = i + 1
                while (j < n && s[j].isWhitespace()) j++
                if (j < n && (s[j] == '}' || s[j] == ']')) {
                    i++
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
