# TASK-002 build and verification notes

## Local status

This workstation has no JDK available to Gradle. `assembleDebug` and Android unit tests therefore
cannot run locally. `kotlinc` is not an adequate substitute because `PortalService` depends on the
Android framework and the Android Gradle Plugin supplies its compile classpath.

The implementation received code-review-level verification: manifest declarations match the
service class; the foreground-service permission and `dataSync` type match the three-argument
`startForeground` call; lifecycle decisions are isolated in `PortalProcessController`; and the
unit test uses a fake launcher/process plus a deterministic scheduler.

## Required build-machine verification

Use the JDK 17 / Android SDK Platform 36 environment documented in `001-build-note.md`:

```powershell
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Before device testing, install the resulting debug APK. Verify that starting the service creates
`filesDir/workspace`, launches `nativeLibraryDir/libportal.so --config filesDir/portal.toml`, and
emits the package-scoped `town.beings.portal.action.PORTAL_STATUS` broadcast. Kill a running
Portal child three times after the initial start to confirm 5-second retries and permanent failure
after the third restart; also confirm that an explicit service stop does not restart the child.
