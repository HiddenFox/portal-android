# TASK-004 Decisions

## OEM guidance

- The app uses `Build.MANUFACTURER` to select Chinese instructions for VIVO, Xiaomi, Huawei, or a generic device.
- The VIVO path is: Settings -> Battery -> Background high power usage -> allow Portal.
- No OEM SDK or undocumented OEM intent is used. The guide first attempts the standard Android battery-optimization request and falls back to the system battery-optimization settings screen when available.

## Battery optimization status

- The guide reads `PowerManager.isIgnoringBatteryOptimizations(packageName)` and explains whether the standard Android exemption is active.
- An exemption alone does not guarantee OEM background behavior, so the manufacturer-specific manual path remains visible in both states.

## Repeated-disconnect trigger

- Portal does not currently expose a WSS protocol-level disconnect callback. The implementation therefore treats an unexpected non-zero Portal process exit that enters `RESTARTING` as the observable disconnect proxy.
- Three such events in a rolling hour trigger the guidance broadcast while the main activity is visible. The guide is also always available from the main screen.
- Timestamp-only trigger state is stored in private app preferences. It contains no credentials or connection content.

## Deliberately excluded

- No one-pixel activity, double-process mutual restart, OEM SDK, or other keep-alive workaround is included. The supported approach is foreground service, locks already present in the app, and user-controlled system/OEM whitelist settings.
