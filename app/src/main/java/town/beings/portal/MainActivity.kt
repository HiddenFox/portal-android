package town.beings.portal

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var connectInput: EditText
    private lateinit var mcpTokenInput: EditText
    private lateinit var sourceDirInput: EditText
    private lateinit var transportUrlInput: EditText
    private lateinit var transportTokenInput: EditText
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
            // Service cleanup emits STOPPED after FAILED; retain the failure so the recovery action stays visible.
            if (receivedStatus.state != PortalProcessState.STOPPED || currentStatus.state != PortalProcessState.FAILED) {
                currentStatus = receivedStatus
            }
            renderStatus(currentStatus)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        connectInput = findViewById(R.id.connect_input)
        mcpTokenInput = findViewById(R.id.mcp_token_input)
        sourceDirInput = findViewById(R.id.source_dir_input)
        transportUrlInput = findViewById(R.id.transport_url_input)
        transportTokenInput = findViewById(R.id.transport_token_input)
        statusView = findViewById(R.id.portal_status)
        val configStore = PortalConfigStore(File(filesDir, PORTAL_CONFIG_NAME))
        val deliveryConfig = configStore.readDeliveryConfig()
        sourceDirInput.setText(deliveryConfig.sourceDir)
        transportUrlInput.setText(deliveryConfig.transportUrl.orEmpty())
        transportTokenInput.setText(deliveryConfig.transportBearerToken.orEmpty())
        processActions = PortalServiceCommandHandler(object : PortalServiceCommands {
            override fun start() = PortalService.start(this@MainActivity)
            override fun stop() = PortalService.stop(this@MainActivity)
        })
        findViewById<Button>(R.id.start_button).setOnClickListener {
            processActions.start(currentStatus.state)
        }
        findViewById<Button>(R.id.stop_button).setOnClickListener { processActions.stop() }
        findViewById<Button>(R.id.delivery_scan_button).setOnClickListener {
            if (!hasRecordingReadPermission()) {
                requestPermissions(arrayOf(recordingReadPermission()), REQUEST_RECORDING_PERMISSION)
                Toast.makeText(this, R.string.recording_permission_required, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            DeliveryWorkScheduler.enqueueScan(this)
            Log.i(TAG, "Delivery scan requested")
            Toast.makeText(this, R.string.delivery_scan_queued, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.keep_alive_guide_button).setOnClickListener {
            startActivity(KeepAliveGuideActivity.intent(this))
        }
        findViewById<Button>(R.id.save_button).setOnClickListener {
            val result = runCatching {
                configStore.save(
                    connect = connectInput.text.toString(),
                    mcpToken = mcpTokenInput.text.toString(),
                    sourceDir = sourceDirInput.text.toString(),
                    transportUrl = transportUrlInput.text.toString(),
                    transportBearerToken = transportTokenInput.text.toString(),
                )
            }
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

    private fun hasRecordingReadPermission(): Boolean =
        checkSelfPermission(recordingReadPermission()) == PackageManager.PERMISSION_GRANTED

    private fun recordingReadPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private companion object {
        const val TAG = "MainActivity"
        const val PORTAL_CONFIG_NAME = "portal.toml"
        const val REQUEST_RECORDING_PERMISSION = 5_005
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
