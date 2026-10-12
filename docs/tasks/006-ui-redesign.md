# TASK-006 UI Redesign

## Changes

- Rebuilt the main screen with Material 3 XML widgets, a teal primary color, system-following day/night themes, rounded cards, Material buttons, and a `TextInputLayout`.
- Added system-bar inset padding to the main `ScrollView`, so the process-status card remains below the status bar when Android enforces edge-to-edge drawing.
- Reduced the user flow to process status, start/stop controls, background keep-alive guidance, and one Being connection field.
- A connection must be an `http://` or `https://` Loom link with a `token` query parameter. Invalid values show a Toast and are never written.
- Saving writes only the private app workspace and `connect_link`, then preserves the existing stop/start restart behavior.

## Removed delivery guard files

- `app/src/main/java/town/beings/portal/DeliveryGuard.kt`
- `app/src/test/java/town/beings/portal/DeliveryGuardTest.kt`

The WorkManager dependency, recording permissions, delivery scan control, delivery configuration inputs, delivery strings, and delivery configuration serialization were also removed.

## Decisions

- **Style:** Material 3 XML was selected to keep the existing View-based application while providing Material cards, buttons, text input, and automatic dark-theme support.
- **Insets:** The root `ScrollView` installs `setOnApplyWindowInsetsListener` and applies the system-bar insets as padding. This retains edge-to-edge compatibility without letting content overlap the status bar.
- **Configuration:** The desktop Portal source confirms that `connect_link` is the configuration key and that the relay is derived from the Loom link. The Android app writes `workspace` as an absolute directory beneath `filesDir`, never a host workspace or Termux path.
