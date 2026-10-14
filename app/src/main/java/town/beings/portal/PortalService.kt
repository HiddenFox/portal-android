package town.beings.portal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.io.IOException

class PortalService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var controller: PortalProcessController
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    @Volatile
    private var lastStatus: PortalProcessStatus? = null

    @Volatile
    private var relayState = RelayConnectionState.CONNECTING

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        controller = PortalProcessController(
            launcher = ProcessBuilderPortalLauncher(
                executable = File(applicationInfo.nativeLibraryDir, PORTAL_LIBRARY_NAME),
                config = File(filesDir, PORTAL_CONFIG_NAME),
                caBundle = File(filesDir, CA_BUNDLE_NAME),
                callbackHandler = mainHandler,
                onLogLine = ::onPortalLogLine,
            ),
            scheduler = RestartScheduler { delayMillis, task ->
                mainHandler.postDelayed(task, delayMillis)
            },
            onStatus = ::onProcessStatus,
        )
        Log.i(TAG, "Portal service created (app ${BuildConfig.VERSION_NAME} vc${BuildConfig.VERSION_CODE})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(PortalStatusHolder.latest ?: PortalProcessStatus(PortalProcessState.RESTARTING))
        PortalConfigStore(
            file = File(filesDir, PORTAL_CONFIG_NAME),
            workspace = File(filesDir, WORKSPACE_DIRECTORY_NAME),
            kitsDir = File(File(filesDir, ".heart-portal"), "kits"),
        ).ensureKitsDir()
        val workspace = File(filesDir, WORKSPACE_DIRECTORY_NAME)
        if (!workspace.exists() && !workspace.mkdirs()) {
            Log.e(TAG, "Portal workspace creation failed")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        logRuntimePermissionStatus()
        controller.start()
        // START_STICKY: after an external kill (permission change, package update,
        // OEM background cleanup) the system recreates the service with a null
        // intent; the code above never dereferences the intent, so the service
        // comes back and relaunches libportal on its own.
        return START_STICKY
    }

    private fun logRuntimePermissionStatus() {
        val recordingPermission = RuntimePermissions.recordingPermissionForApi(Build.VERSION.SDK_INT)
        val recordingGranted = checkSelfPermission(recordingPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.i(
            TAG,
            "Permission status: recording=$recordingGranted notifications=$notificationsGranted" +
                if (notificationsGranted) "" else " (foreground notification hidden by denied permission)",
        )
    }

    override fun onDestroy() {
        controller.stop()
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Portal service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onProcessStatus(status: PortalProcessStatus) {
        lastStatus = status
        PortalStatusHolder.latest = status
        if (status.state == PortalProcessState.RUNNING) setRelayState(RelayConnectionState.CONNECTING)
        if (status.state == PortalProcessState.RESTARTING && recordUnexpectedDisconnect()) {
            sendBroadcast(Intent(ACTION_KEEP_ALIVE_GUIDANCE).setPackage(packageName))
            Log.w(TAG, "Keep-alive guidance threshold reached")
        }
        updateLocks(status.state)
        updateForegroundNotification(status)
        sendBroadcast(
            Intent(ACTION_PORTAL_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATE, status.state.name)
                .putExtra(EXTRA_PID, status.pid ?: NO_PID)
                .putExtra(EXTRA_EXIT_CODE, status.exitCode ?: NO_EXIT_CODE)
                .putExtra(EXTRA_RESTART_ATTEMPT, status.restartAttempt),
        )
        Log.i(TAG, "Portal process state=${status.state} pid=${status.pid} restart=${status.restartAttempt}")
        if (status.state == PortalProcessState.STOPPED || status.state == PortalProcessState.FAILED) {
            stopSelf()
        }
    }

    private fun setRelayState(state: RelayConnectionState) {
        if (relayState == state) return
        relayState = state
        RelayStateHolder.state = state
        sendBroadcast(
            Intent(ACTION_PORTAL_RELAY)
                .setPackage(packageName)
                .putExtra(EXTRA_RELAY_STATE, state.name),
        )
        lastStatus?.let { updateForegroundNotification(it) }
    }

    private fun onPortalLogLine(raw: String) {
        val redacted = RelayLogParser.redact(raw)
        Log.i(PORTAL_STDOUT_TAG, redacted)
        PortalLogBuffer.append(redacted)
        sendBroadcast(
            Intent(ACTION_PORTAL_LOG)
                .setPackage(packageName)
                .putExtra(EXTRA_LOG_TEXT, redacted),
        )
        RelayLogParser.parseLine(raw)?.let { parsed -> setRelayState(parsed) }
    }

    private fun startAsForeground(status: PortalProcessStatus) {
        try {
            val notification = buildNotification(status)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Foreground start failed; continuing in background", error)
        }
    }

    private fun updateForegroundNotification(status: PortalProcessStatus) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: PortalProcessStatus): Notification {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Portal")
            .setContentText(notificationText(status))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun relayNotificationText(): String = when (relayState) {
        RelayConnectionState.CONNECTING -> "relay connecting"
        RelayConnectionState.CONNECTED -> "relay connected"
        RelayConnectionState.RETRYING -> "relay reconnecting"
        RelayConnectionState.REJECTED -> "relay rejected handshake"
        RelayConnectionState.INVALID_LINK -> "invalid Loom link"
    }

    private fun notificationText(status: PortalProcessStatus): String = when (status.state) {
        PortalProcessState.RUNNING -> "Running: Portal process ${status.pid} is active; ${relayNotificationText()}"
        PortalProcessState.RESTARTING -> "Reconnecting: retry ${status.restartAttempt} of ${PortalProcessController.MAX_RESTART_ATTEMPTS}"
        PortalProcessState.STOPPED -> "Stopped"
        PortalProcessState.FAILED -> "Stopped after ${status.restartAttempt} failed restarts"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Portal",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the Portal connection active"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateLocks(state: PortalProcessState) {
        when (state) {
            PortalProcessState.RUNNING, PortalProcessState.RESTARTING -> acquireLocks()
            PortalProcessState.STOPPED, PortalProcessState.FAILED -> releaseLocks()
        }
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:portal")
                .apply { setReferenceCounted(false) }
        }
        if (wifiLock == null) {
            wifiLock = getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$packageName:portal")
                .apply { setReferenceCounted(false) }
        }
        if (wakeLock?.isHeld == false) wakeLock?.acquire()
        if (wifiLock?.isHeld == false) wifiLock?.acquire()
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }

    private fun recordUnexpectedDisconnect(): Boolean {
        val preferences = getSharedPreferences(KEEP_ALIVE_PREFERENCES, MODE_PRIVATE)
        val previous = preferences.getStringSet(KEEP_ALIVE_DISCONNECT_TIMESTAMPS, emptySet())
            .orEmpty()
            .mapNotNull(String::toLongOrNull)
        val trigger = DisconnectGuideTrigger()
        val updated = trigger.recordDisconnect(previous, System.currentTimeMillis())
        preferences.edit()
            .putStringSet(KEEP_ALIVE_DISCONNECT_TIMESTAMPS, updated.map(Long::toString).toSet())
            .apply()
        return trigger.shouldShow(updated)
    }

    companion object {
        const val ACTION_PORTAL_STATUS = "town.beings.portal.action.PORTAL_STATUS"

        const val ACTION_KEEP_ALIVE_GUIDANCE = "town.beings.portal.action.KEEP_ALIVE_GUIDANCE"
        const val EXTRA_STATE = "state"
        const val EXTRA_PID = "pid"
        const val EXTRA_EXIT_CODE = "exit_code"
        const val EXTRA_RESTART_ATTEMPT = "restart_attempt"

        const val ACTION_PORTAL_RELAY = "town.beings.portal.action.PORTAL_RELAY"
        const val ACTION_PORTAL_LOG = "town.beings.portal.action.PORTAL_LOG"
        const val EXTRA_RELAY_STATE = "relay_state"
        const val EXTRA_LOG_TEXT = "log_text"

        private const val TAG = "PortalService"
        private const val PORTAL_STDOUT_TAG = "portal-stdout"
        private const val PORTAL_LIBRARY_NAME = "libportal.so"
        private const val PORTAL_CONFIG_NAME = "portal.toml"
        private const val CA_BUNDLE_NAME = "ca-bundle.pem"
        private const val WORKSPACE_DIRECTORY_NAME = "workspace"
        private const val NOTIFICATION_CHANNEL_ID = "portal"
        private const val NOTIFICATION_ID = 2_002
        private const val KEEP_ALIVE_PREFERENCES = "keep_alive"
        private const val KEEP_ALIVE_DISCONNECT_TIMESTAMPS = "unexpected_disconnect_timestamps"
        private const val NO_PID = -1L
        private const val NO_EXIT_CODE = Int.MIN_VALUE

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PortalService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PortalService::class.java))
        }
    }
}

private class ProcessBuilderPortalLauncher(
    private val executable: File,
    private val config: File,
    private val caBundle: File,
    private val callbackHandler: Handler,
    private val onLogLine: (String) -> Unit,
) : PortalProcessLauncher {
    override fun start(onExit: (exitCode: Int) -> Unit): PortalProcess {
        check(executable.isFile) { "Portal executable is missing at ${executable.absolutePath}" }
        ensureCaBundle()
        val process = ProcessBuilder(executable.absolutePath, "--config", config.absolutePath)
            .redirectErrorStream(true)
            .apply { environment()["SSL_CERT_FILE"] = caBundle.absolutePath }
            .start()
        val portalProcess = AndroidPortalProcess(process)
        Thread({
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line -> onLogLine(line) }
                }
            } catch (error: IOException) {
                Log.i(EXIT_WATCHER_TAG, "Stdout pipe closed during process destroy; waiting for exit: ${error.message}")
            }
            val exitCode = try {
                process.waitFor()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                -1
            }
            callbackHandler.post { onExit(exitCode) }
        }, "portal-exit-watcher").start()
        return portalProcess
    }

    /**
     * heart-portal relies on rustls-native-certs, which finds no root certificates on
     * Android because the standard Linux CA paths do not exist there. Merge the system
     * trust store into one bundle and point SSL_CERT_FILE at it so the relay WSS
     * handshake can verify the server certificate.
     */
    private fun ensureCaBundle() {
        if (caBundle.isFile && caBundle.length() > 0L) return
        val systemCerts = File(SYSTEM_CA_DIR)
        if (!systemCerts.isDirectory) return
        val certificates = systemCerts.listFiles().orEmpty().sortedBy { it.name }
        if (certificates.isEmpty()) return
        caBundle.outputStream().use { output ->
            certificates.forEach { certificate ->
                certificate.inputStream().use { it.copyTo(output) }
            }
        }
    }

    private companion object {
        const val SYSTEM_CA_DIR = "/system/etc/security/cacerts"
        const val EXIT_WATCHER_TAG = "portal-exit-watcher"
    }
}

private class AndroidPortalProcess(private val process: Process) : PortalProcess {
    override val pid: Long
        // java.lang.Process.pid() requires API 34; on Android the ProcessImpl hashCode is the pid.
        get() = process.hashCode().toLong()

    override fun destroy() {
        // SIGTERM first so libportal can close the relay socket cleanly; escalate
        // to SIGKILL so a destroyed Service never leaves libportal behind.
        process.destroy()
        Thread {
            try {
                val deadline = System.currentTimeMillis() + DESTROY_GRACE_MILLIS
                while (System.currentTimeMillis() < deadline) {
                    try {
                        process.exitValue()
                        return@Thread
                    } catch (_: IllegalThreadStateException) {
                        Thread.sleep(DESTROY_POLL_MILLIS)
                    }
                }
                process.destroyForcibly()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                // Process already gone; nothing to escalate.
            }
        }.apply {
            name = "portal-destroy-escalator"
            isDaemon = true
        }.start()
    }

    private companion object {
        const val DESTROY_GRACE_MILLIS = 2_000L
        const val DESTROY_POLL_MILLIS = 200L
    }
}
