package town.beings.portal

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.URI

class MainActivity : Activity() {
    private lateinit var connectInput: EditText
    private lateinit var statusView: TextView
    private lateinit var processActions: PortalServiceCommandHandler
    private var currentStatus = PortalProcessStatus(PortalProcessState.STOPPED)

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PortalService.ACTION_KEEP_ALIVE_GUIDANCE) {
                Log.i(TAG, "Opening keep-alive guidance after repeated unexpected disconnects")
                startActivity(KeepAliveGuideActivity.intent(this@MainActivity))
                return
            }
            if (intent.action != PortalService.ACTION_PORTAL_STATUS) return
            val receivedStatus = PortalStatusIntentParser.parse(intent) ?: return
            if (receivedStatus.state != PortalProcessState.STOPPED || currentStatus.state != PortalProcessState.FAILED) {
                currentStatus = receivedStatus
            }
            renderStatus(currentStatus)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets(findViewById(R.id.main_content))
        connectInput = findViewById(R.id.connect_input)
        statusView = findViewById(R.id.portal_status)
        val configStore = PortalConfigStore(
            file = File(filesDir, PORTAL_CONFIG_NAME),
            workspace = File(filesDir, WORKSPACE_DIRECTORY_NAME),
        )
        connectInput.setText(configStore.readConnectLink().orEmpty())
        processActions = PortalServiceCommandHandler(object : PortalServiceCommands {
            override fun start() = PortalService.start(this@MainActivity)
            override fun stop() = PortalService.stop(this@MainActivity)
        })
        findViewById<Button>(R.id.start_button).setOnClickListener {
            processActions.start(currentStatus.state)
        }
        findViewById<Button>(R.id.stop_button).setOnClickListener { processActions.stop() }
        findViewById<Button>(R.id.keep_alive_guide_button).setOnClickListener {
            startActivity(KeepAliveGuideActivity.intent(this))
        }
        findViewById<Button>(R.id.save_button).setOnClickListener {
            val connectLink = connectInput.text.toString().trim()
            if (!isValidLoomLink(connectLink)) {
                Toast.makeText(this, R.string.invalid_loom_link, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val result = runCatching { configStore.save(connectLink) }
            if (result.isFailure) {
                Log.e(TAG, "Portal configuration save failed", result.exceptionOrNull())
                Toast.makeText(this, R.string.configuration_save_failed, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Log.i(TAG, "Portal configuration saved; service restart requested")
            processActions.restart()
            Toast.makeText(this, R.string.configuration_saved, Toast.LENGTH_SHORT).show()
        }
        renderStatus(currentStatus)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(PortalService.ACTION_PORTAL_STATUS)
            addAction(PortalService.ACTION_KEEP_ALIVE_GUIDANCE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun renderStatus(status: PortalProcessStatus) {
        val pid = status.pid?.toString() ?: getString(R.string.not_available)
        statusView.text = when (status.state) {
            PortalProcessState.RUNNING -> getString(R.string.status_running, pid, status.restartAttempt)
            PortalProcessState.RESTARTING -> getString(R.string.status_restarting, pid, status.restartAttempt)
            PortalProcessState.STOPPED -> getString(R.string.status_stopped, pid, status.restartAttempt)
            PortalProcessState.FAILED -> getString(R.string.status_failed, pid, status.restartAttempt)
        }
    }

    private fun applySystemBarInsets(content: View) {
        content.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
            insets
        }
    }

    private fun isValidLoomLink(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.rawQuery.orEmpty().split('&').any { parameter ->
                parameter.substringBefore('=') == "token" && parameter.contains('=')
            }
    }.getOrDefault(false)

    private companion object {
        const val TAG = "MainActivity"
        const val PORTAL_CONFIG_NAME = "portal.toml"
        const val WORKSPACE_DIRECTORY_NAME = "workspace"
    }
}

/** Encapsulates the FAILED recovery sequence so it is explicit and unit-testable. */
interface PortalServiceCommands {
    fun start()
    fun stop()
}

class PortalServiceCommandHandler(private val commands: PortalServiceCommands) {
    fun start(state: PortalProcessState) {
        if (state == PortalProcessState.FAILED) commands.stop()
        commands.start()
    }

    fun stop() = commands.stop()

    fun restart() {
        commands.stop()
        commands.start()
    }
}
