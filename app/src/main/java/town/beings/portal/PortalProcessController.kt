package town.beings.portal

/** A process abstraction that keeps lifecycle policy testable without Android runtime classes. */
interface PortalProcess {
    val pid: Long

    fun destroy()
}

/** Starts a process and invokes [onExit] exactly once when it terminates. */
fun interface PortalProcessLauncher {
    fun start(onExit: (exitCode: Int) -> Unit): PortalProcess
}

fun interface RestartScheduler {
    fun schedule(delayMillis: Long, task: () -> Unit)
}

enum class PortalProcessState {
    RUNNING,
    RESTARTING,
    STOPPED,
    FAILED,
}

data class PortalProcessStatus(
    val state: PortalProcessState,
    val pid: Long? = null,
    val exitCode: Int? = null,
    val restartAttempt: Int = 0,
)

/**
 * Serial lifecycle policy for Portal. All methods must be called from one serialized executor.
 * A clean exit is treated as intentional; only non-zero exits consume the restart budget.
 */
class PortalProcessController(
    private val launcher: PortalProcessLauncher,
    private val scheduler: RestartScheduler,
    private val onStatus: (PortalProcessStatus) -> Unit,
    private val restartDelayMillis: Long = RESTART_DELAY_MILLIS,
) {
    private var process: PortalProcess? = null
    private var stopRequested = false
    private var restartAttempts = 0

    fun start() {
        if (process != null || !stopRequested && restartAttempts > 0) {
            return
        }
        stopRequested = false
        restartAttempts = 0
        launch()
    }

    fun stop() {
        stopRequested = true
        process?.destroy()
        process = null
        onStatus(PortalProcessStatus(PortalProcessState.STOPPED))
    }

    private fun launch() {
        if (stopRequested) {
            return
        }

        try {
            lateinit var started: PortalProcess
            started = launcher.start { exitCode ->
                if (process === started) {
                    process = null
                    onExit(exitCode)
                }
            }
            process = started
            onStatus(PortalProcessStatus(PortalProcessState.RUNNING, pid = started.pid))
        } catch (_: Exception) {
            onExit(EXIT_CODE_START_FAILURE)
        }
    }

    private fun onExit(exitCode: Int) {
        if (stopRequested || exitCode == 0) {
            onStatus(PortalProcessStatus(PortalProcessState.STOPPED, exitCode = exitCode))
            return
        }

        if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
            onStatus(
                PortalProcessStatus(
                    PortalProcessState.FAILED,
                    exitCode = exitCode,
                    restartAttempt = restartAttempts,
                ),
            )
            return
        }

        restartAttempts += 1
        onStatus(
            PortalProcessStatus(
                PortalProcessState.RESTARTING,
                exitCode = exitCode,
                restartAttempt = restartAttempts,
            ),
        )
        scheduler.schedule(restartDelayMillis) {
            if (!stopRequested) {
                launch()
            }
        }
    }

    companion object {
        const val EXIT_CODE_START_FAILURE = -1
        const val RESTART_DELAY_MILLIS = 5_000L
        const val MAX_RESTART_ATTEMPTS = 3
    }
}
