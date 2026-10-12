package town.beings.portal

import java.io.File

/** Writes the configuration consumed directly by the native Portal executable. */
class PortalConfigStore(
    private val file: File,
    private val workspace: File,
) {
    fun save(connectLink: String) {
        file.writeText(PortalTomlSerializer.serialize(workspace.absolutePath, connectLink))
    }

    fun readConnectLink(): String? {
        if (!file.isFile) return null
        return file.readLines()
            .mapNotNull { line ->
                TOML_VALUE.matchEntire(line.trim())?.let { match ->
                    match.groupValues[1] to unescapeString(match.groupValues[2])
                }
            }
            .toMap()["connect_link"]
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val TOML_VALUE = Regex("^([A-Za-z0-9_]+)\\s*=\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"$")

        fun unescapeString(value: String): String = buildString {
            var index = 0
            while (index < value.length) {
                val character = value[index++]
                if (character != '\\' || index == value.length) {
                    append(character)
                    continue
                }
                append(
                    when (val escaped = value[index++]) {
                        '\\' -> '\\'
                        '\"' -> '\"'
                        'b' -> '\b'
                        't' -> '\t'
                        'n' -> '\n'
                        'f' -> '\u000C'
                        'r' -> '\r'
                        else -> escaped
                    },
                )
            }
        }
    }
}

object PortalTomlSerializer {
    fun serialize(workspace: String, connectLink: String): String = buildString {
        appendString("workspace", workspace)
        appendString("connect_link", connectLink)
    }

    private fun StringBuilder.appendString(name: String, value: String) {
        append(name)
        append(" = ")
        append('"')
        append(escapeString(value))
        append('"')
        append('\n')
    }

    private fun escapeString(value: String): String = buildString {
        value.forEach { character ->
            append(
                when (character) {
                    '\\' -> "\\\\"
                    '\"' -> "\\\""
                    '\b' -> "\\b"
                    '\t' -> "\\t"
                    '\n' -> "\\n"
                    '\u000C' -> "\\f"
                    '\r' -> "\\r"
                    else if (character.code < 0x20 || character == '\u007F') ->
                        "\\u" + character.code.toString(16).padStart(4, '0')
                    else -> character
                },
            )
        }
    }
}
