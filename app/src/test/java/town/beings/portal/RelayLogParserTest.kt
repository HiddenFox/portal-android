package town.beings.portal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelayLogParserTest {

    @Test
    fun parsesHandshakeOk() {
        assertEquals(
            RelayConnectionState.CONNECTED,
            RelayLogParser.parseLine("2026-10-13T02:31:41.123456Z  INFO Portal relay handshake OK — starting MCP server on WebSocket bridge"),
        )
    }

    @Test
    fun parsesSessionEndedCleanly() {
        assertEquals(
            RelayConnectionState.RETRYING,
            RelayLogParser.parseLine("INFO relay session ended cleanly; reconnecting in 5s"),
        )
    }

    @Test
    fun parsesSessionError() {
        assertEquals(
            RelayConnectionState.RETRYING,
            RelayLogParser.parseLine("WARN relay session error after 2s: ws recv error: broken pipe; retry in 5s"),
        )
    }

    @Test
    fun parsesHealthySessionReset() {
        assertEquals(
            RelayConnectionState.RETRYING,
            RelayLogParser.parseLine("INFO relay session ran 90s before error, resetting backoff: heartbeat timeout"),
        )
    }

    @Test
    fun parsesRejectedHandshake() {
        assertEquals(RelayConnectionState.REJECTED, RelayLogParser.parseLine("Error: relay rejected handshake"))
    }

    @Test
    fun parsesInvalidLoomLink() {
        assertEquals(RelayConnectionState.INVALID_LINK, RelayLogParser.parseLine("WARN invalid Loom link: missing token parameter"))
    }

    @Test
    fun returnsNullForUnrelatedLines() {
        assertNull(RelayLogParser.parseLine("INFO Workspace ready: /data/user/0/town.beings.portal/files/workspace"))
        assertNull(RelayLogParser.parseLine("INFO Config path: /data/user/0/town.beings.portal/files/portal.toml"))
    }

    @Test
    fun redactsTokensInLogs() {
        assertEquals(
            "https://host/frank/?token=•••&x=1",
            RelayLogParser.redact("https://host/frank/?token=abc123&x=1"),
        )
    }

    @Test
    fun masksTokenKeepsHostVisible() {
        assertEquals(
            "https://echo.beings.town/frank/?token=•••",
            PortalLinkFormatter.mask("https://echo.beings.town/frank/?token=abc123"),
        )
    }

    @Test
    fun maskKeepsLinkWithoutTokenUnchanged() {
        assertEquals("https://example.com/x", PortalLinkFormatter.mask("https://example.com/x"))
    }

    @Test
    fun bufferTrimsToCapacity() {
        PortalLogBuffer.clear()
        repeat(205) { PortalLogBuffer.append("line $it") }
        assertEquals(200, PortalLogBuffer.snapshot().size)
        assertEquals("line 204", PortalLogBuffer.snapshot().last())
        PortalLogBuffer.clear()
    }
}