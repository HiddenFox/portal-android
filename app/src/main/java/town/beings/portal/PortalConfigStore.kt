package town.beings.portal

import java.io.File

/** Writes the Portal configuration consumed directly by the native Portal executable. */
class PortalConfigStore(private val file: File) {
    fun save(connect: String?, mcpToken: String?) {
        file.writeText(PortalTomlSerializer.serialize(connect, mcpToken))
    }
}

object PortalTomlSerializer {
    fun serialize(connect: String?, mcpToken: String?): String = buildString {
        append("workspace = \"./workspace\"\n")
        connect?.takeIf { it.isNotBlank() }?.let {
            append("connect = \"")
            append(escapeString(it))
            append("\"\n")
        }
        mcpToken?.takeIf { it.isNotBlank() }?.let {
            append("portal_mcp_token = \"")
            append(escapeString(it))
            append("\"\n")
        }
    }

    private fun escapeString(value: String): String = buildString {
        value.forEach { character ->
            append(
                when (character) {
                    '\\' -> "\\\\"
                    '"' -> "\\\""
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
