package town.beings.portal

import kotlin.test.Test
import kotlin.test.assertEquals

class PortalProcessControllerTest {
    @Test
    fun startLaunchesProcessAndReportsPid() {
        val launcher = FakeLauncher()
        val statuses = mutableListOf<PortalProcessStatus>()
        val controller = PortalProcessController(launcher, FakeScheduler(), statuses::add)

        controller.start()

        assertEquals(1, launcher.starts)
        assertEquals(PortalProcessState.RUNNING, statuses.single().state)
        assertEquals(100L, statuses.single().pid)
    }

    @Test
    fun abnormalExitRestartsAfterFiveSeconds() {
        val launcher = FakeLauncher()
        val scheduler = FakeScheduler()
        val statuses = mutableListOf<PortalProcessStatus>()
        val controller = PortalProcessController(launcher, scheduler, statuses::add)
        controller.start()

        launcher.exit(17)

        assertEquals(PortalProcessState.RESTARTING, statuses.last().state)
        assertEquals(1, statuses.last().restartAttempt)
        assertEquals(PortalProcessController.RESTART_DELAY_MILLIS, scheduler.delayMillis)
        scheduler.runNext()
        assertEquals(2, launcher.starts)
        assertEquals(PortalProcessState.RUNNING, statuses.last().state)
    }

    @Test
    fun givesUpAfterThreeRestartAttempts() {
        val launcher = FakeLauncher()
        val scheduler = FakeScheduler()
        val statuses = mutableListOf<PortalProcessStatus>()
        val controller = PortalProcessController(launcher, scheduler, statuses::add)
        controller.start()

        repeat(PortalProcessController.MAX_RESTART_ATTEMPTS) {
            launcher.exit(1)
            scheduler.runNext()
        }
        launcher.exit(1)

        assertEquals(PortalProcessState.FAILED, statuses.last().state)
        assertEquals(PortalProcessController.MAX_RESTART_ATTEMPTS, statuses.last().restartAttempt)
        assertEquals(4, launcher.starts)
    }

    @Test
    fun stopDestroysRunningProcess() {
        val launcher = FakeLauncher()
        val scheduler = FakeScheduler()
        val statuses = mutableListOf<PortalProcessStatus>()
        val controller = PortalProcessController(launcher, scheduler, statuses::add)
        controller.start()

        controller.stop()

        assertEquals(1, launcher.starts)
        assertEquals(true, launcher.lastProcess.destroyed)
        assertEquals(PortalProcessState.STOPPED, statuses.last().state)
    }

    @Test
    fun stopSuppressesQueuedRestart() {
        val launcher = FakeLauncher()
        val scheduler = FakeScheduler()
        val controller = PortalProcessController(launcher, scheduler, {})
        controller.start()
        launcher.exit(1)

        controller.stop()
        scheduler.runNext()

        assertEquals(1, launcher.starts)
    }

    private class FakeLauncher : PortalProcessLauncher {
        var starts = 0
        private var exitCallback: ((Int) -> Unit)? = null
        lateinit var lastProcess: FakeProcess

        override fun start(onExit: (exitCode: Int) -> Unit): PortalProcess {
            starts += 1
            exitCallback = onExit
            lastProcess = FakeProcess(starts * 100L)
            return lastProcess
        }

        fun exit(code: Int) {
            exitCallback?.invoke(code)
        }
    }

    private class FakeProcess(override val pid: Long) : PortalProcess {
        var destroyed = false

        override fun destroy() {
            destroyed = true
        }
    }

    private class FakeScheduler : RestartScheduler {
        var delayMillis: Long? = null
        private var task: (() -> Unit)? = null

        override fun schedule(delayMillis: Long, task: () -> Unit) {
            this.delayMillis = delayMillis
            this.task = task
        }

        fun runNext() {
            task?.also {
                task = null
                it()
            }
        }
    }
}
