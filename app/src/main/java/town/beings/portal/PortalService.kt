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

class PortalService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var controller: PortalProcessController
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        controller = PortalProcessController(
            launcher = ProcessBuilderPortalLauncher(
                executable = File(applicationInfo.nativeLibraryDir, PORTAL_LIBRARY_NAME),
                config = File(filesDir, PORTAL_CONFIG_NAME),
                callbackHandler = mainHandler,
            ),
            scheduler = RestartScheduler { delayMillis, task ->
                mainHandler.postDelayed(task, delayMillis)
            },
            onStatus = ::onProcessStatus,
        )
        Log.i(TAG, "Portal service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(PortalProcessStatus(PortalProcessState.RESTARTING))
        val workspace = File(filesDir, WORKSPACE_DIRECTORY_NAME)
        if (!workspace.exists() && !workspace.mkdirs()) {
            Log.e(TAG, "Portal workspace creation failed")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        controller.start()
        return START_NOT_STICKY
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

    private fun startAsForeground(status: PortalProcessStatus) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

    private fun notificationText(status: PortalProcessStatus): String = when (status.state) {
        PortalProcessState.RUNNING -> "Running: Portal process ${status.pid} is active"
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

    companion object {
        const val ACTION_PORTAL_STATUS = "town.beings.portal.action.PORTAL_STATUS"
        const val EXTRA_STATE = "state"
        const val EXTRA_PID = "pid"
        const val EXTRA_EXIT_CODE = "exit_code"
        const val EXTRA_RESTART_ATTEMPT = "restart_attempt"

        private const val TAG = "PortalService"
        private const val PORTAL_LIBRARY_NAME = "libportal.so"
        private const val PORTAL_CONFIG_NAME = "portal.toml"
        private const val WORKSPACE_DIRECTORY_NAME = "workspace"
        private const val NOTIFICATION_CHANNEL_ID = "portal"
        private const val NOTIFICATION_ID = 2_002
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
    private val callbackHandler: Handler,
) : PortalProcessLauncher {
    override fun start(onExit: (exitCode: Int) -> Unit): PortalProcess {
        check(executable.isFile) { "Portal executable is missing at ${executable.absolutePath}" }
        val process = ProcessBuilder(executable.absolutePath, "--config", config.absolutePath)
            .redirectErrorStream(true)
            .start()
        val portalProcess = AndroidPortalProcess(process)
        Thread({
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { }
            }
            val exitCode = process.waitFor()
            callbackHandler.post { onExit(exitCode) }
        }, "portal-exit-watcher").start()
        return portalProcess
    }
}

private class AndroidPortalProcess(private val process: Process) : PortalProcess {
    override val pid: Long
        get() = process.pid()

    override fun destroy() {
        process.destroy()
    }
}
