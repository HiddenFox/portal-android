package town.beings.portal

import java.io.File

/** Writes the Portal configuration consumed directly by the native Portal executable. */
class PortalConfigStore(private val file: File) {
    fun save(
        connect: String?,
        mcpToken: String?,
        sourceDir: String? = null,
        transportUrl: String? = null,
        transportBearerToken: String? = null,
    ) {
        file.writeText(
            PortalTomlSerializer.serialize(
                connect,
                mcpToken,
                sourceDir,
                transportUrl,
                transportBearerToken,
            ),
        )
    }

    fun readDeliveryConfig(): DeliveryConfig {
        if (!file.isFile) return DeliveryConfig()
        val values = file.readLines()
            .mapNotNull { line ->
                TOML_VALUE.matchEntire(line.trim())?.let { match ->
                    match.groupValues[1] to unescapeString(match.groupValues[2])
                }
            }
            .toMap()
        return DeliveryConfig(
            sourceDir = values["source_dir"]?.takeIf { it.isNotBlank() } ?: DeliveryConfig.DEFAULT_SOURCE_DIR,
            transportUrl = values["transport_url"]?.takeIf { it.isNotBlank() },
            transportBearerToken = values["transport_bearer_token"]?.takeIf { it.isNotBlank() },
        )
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

data class DeliveryConfig(
    val sourceDir: String = DEFAULT_SOURCE_DIR,
    val transportUrl: String? = null,
    val transportBearerToken: String? = null,
) {
    companion object {
        const val DEFAULT_SOURCE_DIR = "/storage/emulated/0/Recordings"
    }
}

object PortalTomlSerializer {
    fun serialize(
        connect: String?,
        mcpToken: String?,
        sourceDir: String? = null,
        transportUrl: String? = null,
        transportBearerToken: String? = null,
    ): String = buildString {
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
        sourceDir?.takeIf { it.isNotBlank() }?.let { appendString("source_dir", it) }
        transportUrl?.takeIf { it.isNotBlank() }?.let { appendString("transport_url", it) }
        transportBearerToken?.takeIf { it.isNotBlank() }?.let { appendString("transport_bearer_token", it) }
    }

    private fun StringBuilder.appendString(name: String, value: String) {
        append(name)
        append(" = \"")
        append(escapeString(value))
        append("\"\n")
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
