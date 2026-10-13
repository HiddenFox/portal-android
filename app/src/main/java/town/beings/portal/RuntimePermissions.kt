package town.beings.portal

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Runtime permissions this app needs, by API level.
 *
 * Recording access is required to read sound-recorder output via FUSE;
 * POST_NOTIFICATIONS (Android 13+) keeps the foreground-service status visible.
 */
object RuntimePermissions {

    /** Permissions that must be requested at runtime for the given API level. */
    fun requiredForApi(apiLevel: Int): List<String> = when {
        apiLevel >= 33 -> listOf(
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
        else -> listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** The permission required to read recordings on the given API level. */
    fun recordingPermissionForApi(apiLevel: Int): String = when {
        apiLevel >= 33 -> android.Manifest.permission.READ_MEDIA_AUDIO
        else -> android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    /** Currently-missing runtime permissions on this device. */
    fun missing(context: Context): List<String> =
        requiredForApi(Build.VERSION.SDK_INT).filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

    /**
     * True when the recording permission is missing AND the system will no longer
     * show a request dialog (user permanently denied). Call only after a request
     * cycle: before the first request this returns false negatives on some devices.
     */
    fun recordingPermanentlyDenied(activity: Activity): Boolean {
        val permission = recordingPermissionForApi(Build.VERSION.SDK_INT)
        return activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
            !activity.shouldShowRequestPermissionRationale(permission)
    }
}