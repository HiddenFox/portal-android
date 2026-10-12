# TASK-004 Build Note

## Static verification

- Confirmed the manifest declares `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and the non-exported keep-alive guide activity.
- Added local unit tests for manufacturer selection, the battery-optimization request intent specification, and the three-events-per-hour trigger threshold.
- Reviewed the UI resource references and the service-to-activity guidance broadcast path.

## Build status

- Not compiled in this environment: no JDK is installed or available to Gradle.
- Run `./gradlew test` and `./gradlew assembleDebug` in an environment with a compatible JDK before device validation.

## Device validation checklist

- On a VIVO device, open **后台保活指引**, follow the displayed VIVO path, and verify Portal is allowed under background high power usage.
- Lock the device for 10 minutes and for 1 hour; confirm the WSS connection remains available.
- Verify behavior after a process kill and after reboot according to the task checklist.
