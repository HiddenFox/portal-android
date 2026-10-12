# TASK-005 Build and Verification Note

## Verification status

`git diff --check` completed without whitespace errors. `./gradlew.bat testDebugUnitTest` was attempted but Gradle could not start because `JAVA_HOME` is unset and `java` is not on `PATH`; Android compilation and unit-test execution therefore did not run.

## Required local verification

1. Install a JDK compatible with Android Gradle Plugin 8.13.2 (JDK 17 or newer) and set `JAVA_HOME`.
2. Run `./gradlew testDebugUnitTest` from the repository root.
3. Run `./gradlew assembleDebug`.
4. On a device, save `source_dir` and `transport_url`, tap **Scan and deliver**, and verify that the relay receives a PUT with the expected MD5 header.
5. Tap **Scan and deliver** again and verify that an uploaded entry is not resent. Temporarily make the relay fail and verify ledger attempts increase and retries are scheduled with the bounded delay.
