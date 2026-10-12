# TASK-001 build and verification notes

This workstation does not currently expose a JDK, Android SDK, or NDK. The Gradle wrapper
can be checked with `./gradlew --version`, but APK assembly and `aapt` verification must run
on an Android build machine.

Required toolchain:

- JDK 17
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- Android SDK Command-line Tools (latest)
- Android NDK r27 (`27.2.12479018`); this task packages a prebuilt binary and does not invoke
  the NDK, but it is required for the agreed Android build environment.

On PowerShell, after setting `ANDROID_SDK_ROOT` and adding its `cmdline-tools/latest/bin` to
`PATH`, install and verify with:

```powershell
sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;36.0.0" "ndk;27.2.12479018"
./gradlew --version
./gradlew assembleDebug
$apk = "app/build/outputs/apk/debug/app-debug.apk"
jar tf $apk | Select-String "lib/arm64-v8a/libportal.so"
Expand-Archive $apk -DestinationPath build/apk-check -Force
(Get-FileHash build/apk-check/lib/arm64-v8a/libportal.so -Algorithm SHA256).Hash
& "$env:ANDROID_SDK_ROOT/build-tools/36.0.0/aapt.exe" dump badging $apk
```

Expected SHA-256 for the APK entry is
`996d9f88ef9b0e8305605ea99a05bdaae5f54da6be86e4e95f50c89b6721a070`. The `aapt` output
must identify package `town.beings.portal` and list `INTERNET`, `FOREGROUND_SERVICE`, and
`WAKE_LOCK`.

Human-device acceptance remains separate: install the debug APK on VIVO V2366HA (Android 16)
and confirm that it starts without crashing after TASK-003 declares the launcher activity.
