package town.beings.portal

/**
 * Relay connection state as observed from the native portal process stdout.
 *
 * heart-portal logs connection lifecycle transitions to stdout/stderr
 * (merged into one stream by the process launcher). These markers map those
 * log lines to a UI-level connection state.
 */
enum class RelayConnectionState {
    /** Process started, no relay verdict yet. */
    CONNECTING,

    /** "Portal relay handshake OK" — relay session established. */
    CONNECTED,

    /** Session ended (cleanly or with error) and will be retried. */
    RETRYING,

    /** Relay rejected the handshake token. */
    REJECTED,

    /** The stored Loom link failed validation. */
    INVALID_LINK,
}

/** Pure parser for relay lifecycle markers in heart-portal log lines. */
object RelayLogParser {
    fun parseLine(line: String): RelayConnectionState? = when {
        "Portal relay handshake OK" in line -> RelayConnectionState.CONNECTED
        "relay session ended cleanly" in line -> RelayConnectionState.RETRYING
        "relay session error after" in line -> RelayConnectionState.RETRYING
        "relay session ran" in line -> RelayConnectionState.RETRYING
        "relay rejected handshake" in line -> RelayConnectionState.REJECTED
        "invalid Loom link" in line -> RelayConnectionState.INVALID_LINK
        else -> null
    }

    /** Redacts token query parameters so raw logs can be shown in the UI. */
    fun redact(line: String): String =
        line.replace(Regex("([?&]token=)[^&\\s]+"), "$1•••")
}

/** Masks the token in a saved Loom link for display, keeping host visible. */
object PortalLinkFormatter {
    fun mask(link: String): String =
        link.replace(Regex("([?&]token=)[^&\\s]+"), "$1•••")
}

/**
 * Process-wide ring buffer of the most recent portal log lines. The service
 * appends; the activity snapshots on start and receives per-line broadcasts
 * while visible. Service and activity share one process, so a plain
 * synchronized object is sufficient.
 */
/** Process-wide holder so the UI can read the latest relay state after recreation. */
object RelayStateHolder {
    @Volatile
    var state: RelayConnectionState = RelayConnectionState.CONNECTING
}

object PortalLogBuffer {
    private const val MAX_LINES = 200
    private val lines = ArrayDeque<String>(MAX_LINES)
    private val lock = Any()

    fun append(line: String) {
        synchronized(lock) {
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(line)
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    fun clear() = synchronized(lock) { lines.clear() }
}
