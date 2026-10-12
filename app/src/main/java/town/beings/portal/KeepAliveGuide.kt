package town.beings.portal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

enum class DeviceManufacturer {
    VIVO,
    XIAOMI,
    HUAWEI,
    OTHER,
}

object KeepAliveGuide {
    fun manufacturer(value: String): DeviceManufacturer = when {
        value.lowercase(Locale.ROOT).contains("vivo") -> DeviceManufacturer.VIVO
        value.lowercase(Locale.ROOT).contains("xiaomi") || value.lowercase(Locale.ROOT).contains("redmi") -> DeviceManufacturer.XIAOMI
        value.lowercase(Locale.ROOT).contains("huawei") || value.lowercase(Locale.ROOT).contains("honor") -> DeviceManufacturer.HUAWEI
        else -> DeviceManufacturer.OTHER
    }

    fun batteryOptimizationIntent(packageName: String): Intent = batteryOptimizationIntentSpec(packageName).let { spec ->
        Intent(spec.action).setData(Uri.parse(spec.dataUri))
    }

    fun batteryOptimizationIntentSpec(packageName: String): BatteryOptimizationIntentSpec =
        BatteryOptimizationIntentSpec(
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            dataUri = "package:$packageName",
        )

    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun currentManufacturer(): DeviceManufacturer = manufacturer(Build.MANUFACTURER.orEmpty())
}

data class BatteryOptimizationIntentSpec(
    val action: String,
    val dataUri: String,
)

/** Keeps a rolling count of unexpected Portal process exits for the guide trigger. */
class DisconnectGuideTrigger(
    private val threshold: Int = THRESHOLD,
    private val windowMillis: Long = WINDOW_MILLIS,
) {
    fun recordDisconnect(previous: List<Long>, nowMillis: Long): List<Long> =
        (previous.filter { nowMillis - it in 0..windowMillis } + nowMillis).takeLast(threshold)

    fun shouldShow(disconnects: List<Long>): Boolean = disconnects.size >= threshold

    companion object {
        const val THRESHOLD = 3
        const val WINDOW_MILLIS = 60 * 60 * 1000L
    }
}
