package town.beings.portal

import kotlin.test.Test
import kotlin.test.assertEquals

class PortalServiceCommandHandlerTest {
    @Test
    fun failedStartStopsThenStartsService() {
        val commands = RecordingCommands()
        PortalServiceCommandHandler(commands).start(PortalProcessState.FAILED)
        assertEquals(listOf("stop", "start"), commands.calls)
    }

    @Test
    fun normalStartOnlyStartsService() {
        val commands = RecordingCommands()
        PortalServiceCommandHandler(commands).start(PortalProcessState.STOPPED)
        assertEquals(listOf("start"), commands.calls)
    }

    @Test
    fun restartStopsThenStartsService() {
        val commands = RecordingCommands()
        PortalServiceCommandHandler(commands).restart()
        assertEquals(listOf("stop", "start"), commands.calls)
    }

    private class RecordingCommands : PortalServiceCommands {
        val calls = mutableListOf<String>()
        override fun start() { calls += "start" }
        override fun stop() { calls += "stop" }
    }
}
