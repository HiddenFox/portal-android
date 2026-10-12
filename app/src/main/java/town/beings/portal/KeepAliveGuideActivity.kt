package town.beings.portal

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class KeepAliveGuideActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keep_alive_guide)

        findViewById<TextView>(R.id.keep_alive_manufacturer_steps).setText(
            when (KeepAliveGuide.currentManufacturer()) {
                DeviceManufacturer.VIVO -> R.string.keep_alive_steps_vivo
                DeviceManufacturer.XIAOMI -> R.string.keep_alive_steps_xiaomi
                DeviceManufacturer.HUAWEI -> R.string.keep_alive_steps_huawei
                DeviceManufacturer.OTHER -> R.string.keep_alive_steps_generic
            },
        )
        findViewById<Button>(R.id.open_battery_settings_button).setOnClickListener {
            openBatterySettings()
        }
        refreshOptimizationStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BATTERY_SETTINGS) {
            Log.i(TAG, "Returned from battery optimization settings (resultCode=$resultCode)")
            refreshOptimizationStatus()
        }
    }

    private fun refreshOptimizationStatus() {
        val ignoringOptimizations = KeepAliveGuide.isIgnoringBatteryOptimizations(this)
        findViewById<TextView>(R.id.keep_alive_optimization_status).setText(
            if (ignoringOptimizations) R.string.keep_alive_optimization_enabled else R.string.keep_alive_optimization_disabled,
        )
    }

    private fun openBatterySettings() {
        val alreadyIgnoringOptimizations = KeepAliveGuide.isIgnoringBatteryOptimizations(this)
        val preferredIntent = if (alreadyIgnoringOptimizations) {
            KeepAliveGuide.batteryOptimizationSettingsIntent()
        } else {
            KeepAliveGuide.batteryOptimizationIntent(packageName)
        }
        val fallbackIntent = KeepAliveGuide.batteryOptimizationSettingsIntent()
        val intent = when {
            preferredIntent.resolveActivity(packageManager) != null -> preferredIntent
            fallbackIntent.resolveActivity(packageManager) != null -> fallbackIntent
            else -> null
        }
        if (intent == null) {
            Log.w(TAG, "Battery optimization settings activity unavailable")
            Toast.makeText(this, R.string.battery_settings_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        Log.i(TAG, "Opening battery optimization settings")
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_BATTERY_SETTINGS)
    }

    companion object {
        private const val TAG = "KeepAliveGuide"
        private const val REQUEST_BATTERY_SETTINGS = 1001

        fun intent(context: android.content.Context): Intent =
            Intent(context, KeepAliveGuideActivity::class.java)
    }
}