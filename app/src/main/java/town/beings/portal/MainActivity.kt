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
import android.widget.ScrollView
import android.widget.TextView
import android.os.SystemClock
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.net.URI

class MainActivity : Activity() {
    private lateinit var connectInput: EditText
    private lateinit var connectInputLayout: View
    private lateinit var saveButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var savedLinkView: TextView
    private lateinit var savedLinkBox: View
    private lateinit var editLinkButton: Button
    private lateinit var controlSection: View
    private lateinit var statusView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var logView: TextView
    private lateinit var processActions: PortalServiceCommandHandler
    private var currentStatus = PortalProcessStatus(PortalProcessState.STOPPED)
    private val logLines = ArrayDeque<String>()
    private var linkEditMode = false
    private lateinit var configStore: PortalConfigStore
    private lateinit var permissionHintView: TextView
    private lateinit var keepAliveHintView: TextView
    private lateinit var relayTimestampsView: TextView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PortalService.ACTION_PORTAL_STATUS -> {
                    val receivedStatus = PortalStatusIntentParser.parse(intent) ?: return
                    if (receivedStatus.state != PortalProcessState.STOPPED || currentStatus.state != PortalProcessState.FAILED) {
                        currentStatus = receivedStatus
                    }
                    renderStatus(currentStatus)
                }
                PortalService.ACTION_PORTAL_RELAY -> renderStatus(currentStatus)
                PortalService.ACTION_PORTAL_LOG -> {
                    intent.getStringExtra(PortalService.EXTRA_LOG_TEXT)?.let(::appendLogLine)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Combined runtime-permission request (recording access + notifications on 13+).
        val missingRuntimePermissions = RuntimePermissions.missing(this)
        if (missingRuntimePermissions.isNotEmpty()) {
            requestPermissions(missingRuntimePermissions.toTypedArray(), REQUEST_RUNTIME_PERMISSIONS)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets(findViewById(R.id.main_content))
        connectInput = findViewById(R.id.connect_input)
        connectInputLayout = findViewById(R.id.connect_input_layout)
        saveButton = findViewById(R.id.save_button)
        savedLinkView = findViewById(R.id.saved_link_view)
        savedLinkBox = findViewById(R.id.saved_link_box)
        editLinkButton = findViewById(R.id.edit_link_button)
        controlSection = findViewById(R.id.control_section)
        statusView = findViewById(R.id.portal_status)
        logScroll = findViewById(R.id.log_scroll)
        logView = findViewById(R.id.portal_log)
        permissionHintView = findViewById(R.id.permission_hint)
        keepAliveHintView = findViewById(R.id.keep_alive_hint)
        relayTimestampsView = findViewById(R.id.relay_timestamps)
        configStore = PortalConfigStore(
            file = File(filesDir, PORTAL_CONFIG_NAME),
            workspace = File(filesDir, WORKSPACE_DIRECTORY_NAME),
            kitsDir = File(File(filesDir, ".heart-portal"), "kits"),
        )
        configStore.ensureKitsDir()
        processActions = PortalServiceCommandHandler(object : PortalServiceCommands {
            override fun start() = PortalService.start(this@MainActivity)
            override fun stop() = PortalService.stop(this@MainActivity)
        })
        // Diagnostic/automation hook: am start --ez town.beings.portal.AUTOSTART true
        // (bare --ez AUTOSTART true is accepted as well for automation ergonomics)
        val autostartRequested = intent?.getBooleanExtra("town.beings.portal.AUTOSTART", false) == true ||
            intent?.getBooleanExtra("AUTOSTART", false) == true
        if (autostartRequested) {
            val activeNow = currentStatus.state == PortalProcessState.RUNNING ||
                currentStatus.state == PortalProcessState.RESTARTING
            if (!activeNow) processActions.start(currentStatus.state)
        }
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)
        startButton.setOnClickListener {
            val active = currentStatus.state == PortalProcessState.RUNNING || currentStatus.state == PortalProcessState.RESTARTING
            if (active) return@setOnClickListener
            processActions.start(currentStatus.state)
        }
        stopButton.setOnClickListener {
            val idle = currentStatus.state == PortalProcessState.STOPPED || currentStatus.state == PortalProcessState.FAILED
            if (idle) return@setOnClickListener
            processActions.stop()
        }
        findViewById<Button>(R.id.keep_alive_guide_button).setOnClickListener {
            startActivity(KeepAliveGuideActivity.intent(this))
        }
        permissionHintView.setOnClickListener {
            startActivity(appDetailsSettingsIntent())
        }
        keepAliveHintView.setOnClickListener {
            startActivity(KeepAliveGuideActivity.intent(this))
        }
        findViewById<Button>(R.id.notification_setup_button).setOnClickListener { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
        findViewById<Button>(R.id.battery_setup_button).setOnClickListener { startActivity(KeepAliveGuide.batteryOptimizationIntent(packageName)) }
        findViewById<Button>(R.id.autostart_setup_button).setOnClickListener { openAutostartSettings() }
        editLinkButton.setOnClickListener {
            linkEditMode = true
            connectInput.setText(configStore.readConnectLink().orEmpty())
            renderConfigUi()
        }
        saveButton.setOnClickListener {
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
            linkEditMode = false
            renderConfigUi()
        }
        renderConfigUi()
        renderStatus(currentStatus)
        renderLogSnapshot()
        renderKeepAliveHint()
        renderStartupChecklist()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(PortalService.ACTION_PORTAL_STATUS)
            addAction(PortalService.ACTION_KEEP_ALIVE_GUIDANCE)
            addAction(PortalService.ACTION_PORTAL_RELAY)
            addAction(PortalService.ACTION_PORTAL_LOG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        // Status broadcasts only fire on state transitions; a recreated activity would
        // otherwise stay stuck on its initial STOPPED state while the service runs.
        // Adopt the latest known status from the process-wide holder instead.
        PortalStatusHolder.latest?.let { latest ->
            if (latest.state != PortalProcessState.STOPPED || currentStatus.state != PortalProcessState.FAILED) {
                currentStatus = latest
            }
        }
        renderStatus(currentStatus)
        renderKeepAliveHint()
        renderStartupChecklist()
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RUNTIME_PERMISSIONS) return
        val denied = permissions.filterIndexed { index, _ ->
            grantResults.getOrNull(index) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (denied.isEmpty()) {
            Log.i(TAG, "Runtime permissions granted")
            permissionHintView.visibility = View.GONE
        } else {
            Log.w(TAG, "Runtime permissions denied: $denied")
            renderPermissionHint()
        }
    }

    private fun isConfigured(): Boolean {
        val link = configStore.readConnectLink() ?: return false
        return isValidLoomLink(link)
    }

    private fun renderConfigUi() {
        val configured = isConfigured()
        val showEditor = !configured || linkEditMode
        connectInputLayout.visibility = if (showEditor) View.VISIBLE else View.GONE
        saveButton.visibility = if (showEditor) View.VISIBLE else View.GONE
        savedLinkBox.visibility = if (configured && !linkEditMode) View.VISIBLE else View.GONE
        controlSection.visibility = if (configured) View.VISIBLE else View.GONE
        if (configured && !linkEditMode) {
            savedLinkView.text = PortalLinkFormatter.mask(configStore.readConnectLink().orEmpty())
        }
    }

    private fun renderStatus(status: PortalProcessStatus) {
        val pid = status.pid?.toString() ?: getString(R.string.not_available)
        val base = when (status.state) {
            PortalProcessState.RUNNING -> getString(R.string.status_running, pid, status.restartAttempt)
            PortalProcessState.RESTARTING -> getString(R.string.status_restarting, pid, status.restartAttempt)
            PortalProcessState.STOPPED -> getString(R.string.status_stopped, pid, status.restartAttempt)
            PortalProcessState.FAILED -> getString(R.string.status_failed, pid, status.restartAttempt)
        }
        statusView.text = if (status.state == PortalProcessState.RUNNING) {
            base + "\n" + relayLine()
        } else {
            base
        }
        renderRelayTimestamps()
        val active = status.state == PortalProcessState.RUNNING || status.state == PortalProcessState.RESTARTING
        startButton.isEnabled = !active
        stopButton.isEnabled = active
    }

    private fun relayLine(): String = when (RelayStateHolder.state) {
        RelayConnectionState.CONNECTING -> getString(R.string.relay_connecting)
        RelayConnectionState.CONNECTED -> getString(R.string.relay_connected)
        RelayConnectionState.RETRYING -> getString(R.string.relay_retrying)
        RelayConnectionState.REJECTED -> getString(R.string.relay_rejected)
        RelayConnectionState.INVALID_LINK -> getString(R.string.relay_invalid_link)
    }

    private fun appendLogLine(line: String) {
        if (logLines.size >= MAX_LOG_LINES) logLines.removeFirst()
        logLines.addLast(line)
        renderLog()
    }

    private fun renderLogSnapshot() {
        logLines.clear()
        PortalLogBuffer.snapshot().forEach { logLines.addLast(it) }
        renderLog()
    }

    private fun renderLog() {
        logView.text = logLines.joinToString("\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
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

    private fun renderPermissionHint() {
        permissionHintView.visibility =
            if (RuntimePermissions.recordingPermanentlyDenied(this)) View.VISIBLE else View.GONE
    }

    private fun renderKeepAliveHint() {
        keepAliveHintView.visibility =
            if (KeepAliveGuide.isIgnoringBatteryOptimizations(this)) View.GONE else View.VISIBLE
    }

    private fun renderStartupChecklist() {
        val notificationsMissing = Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        findViewById<Button>(R.id.notification_setup_button).setText(
            if (notificationsMissing) R.string.setup_notifications else R.string.setup_notifications_ready,
        )
        val batteryMissing = !KeepAliveGuide.isIgnoringBatteryOptimizations(this)
        findViewById<Button>(R.id.battery_setup_button).setText(
            if (batteryMissing) R.string.setup_battery else R.string.setup_battery_ready,
        )
        findViewById<Button>(R.id.autostart_setup_button).visibility =
            if (KeepAliveGuide.currentManufacturer() == DeviceManufacturer.XIAOMI) View.VISIBLE else View.GONE
    }

    private fun renderRelayTimestamps() {
        fun value(at: Long?): String = at?.let { "${(SystemClock.elapsedRealtime() - it) / 1000}s ago" } ?: getString(R.string.not_available)
        relayTimestampsView.text = getString(R.string.relay_timestamps, value(RelayStateHolder.lastHandshakeElapsedMillis), value(RelayStateHolder.lastRetryElapsedMillis))
    }

    private fun openAutostartSettings() {
        val miui = Intent("miui.intent.action.OP_AUTO_START").setData(Uri.parse("package:$packageName"))
        val fallback = appDetailsSettingsIntent()
        startActivity(if (miui.resolveActivity(packageManager) != null) miui else fallback)
    }

    private fun appDetailsSettingsIntent(): Intent =
        Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null),
        )

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
        const val MAX_LOG_LINES = 300
        const val REQUEST_RUNTIME_PERMISSIONS = 100
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
