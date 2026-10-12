package town.beings.portal

import android.provider.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeepAliveGuideTest {
    @Test
    fun vivoManufacturerUsesVivoInstructions() {
        assertEquals(DeviceManufacturer.VIVO, KeepAliveGuide.manufacturer("vivo"))
    }

    @Test
    fun requestBatteryOptimizationIntentUsesAppPackageUri() {
        val spec = KeepAliveGuide.batteryOptimizationIntentSpec("town.beings.portal")

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, spec.action)
        assertEquals("package:town.beings.portal", spec.dataUri)
    }

    @Test
    fun thirdDisconnectInOneHourTriggersGuide() {
        val trigger = DisconnectGuideTrigger()
        val first = trigger.recordDisconnect(emptyList(), 1_000L)
        val second = trigger.recordDisconnect(first, 2_000L)
        val third = trigger.recordDisconnect(second, 3_000L)

        assertTrue(!trigger.shouldShow(first))
        assertTrue(!trigger.shouldShow(second))
        assertTrue(trigger.shouldShow(third))
    }
}
