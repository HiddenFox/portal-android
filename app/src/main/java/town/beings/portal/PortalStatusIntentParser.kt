package town.beings.portal

import android.content.Intent

object PortalStatusIntentParser {
    fun parse(intent: Intent): PortalProcessStatus? {
        val stateName = intent.getStringExtra(PortalService.EXTRA_STATE) ?: return null
        val state = runCatching { PortalProcessState.valueOf(stateName) }.getOrNull() ?: return null
        val pid = intent.getLongExtra(PortalService.EXTRA_PID, NO_PID).takeUnless { it == NO_PID }
        val exitCode = intent.getIntExtra(PortalService.EXTRA_EXIT_CODE, NO_EXIT_CODE).takeUnless { it == NO_EXIT_CODE }
        return PortalProcessStatus(state, pid, exitCode, intent.getIntExtra(PortalService.EXTRA_RESTART_ATTEMPT, 0))
    }

    private const val NO_PID = -1L
    private const val NO_EXIT_CODE = Int.MIN_VALUE
}
