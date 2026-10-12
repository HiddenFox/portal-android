# TASK-003 build and verification note

## Required build verification

Run the following from the repository root when a JDK is available:

```powershell
.\\gradlew.bat test
.\\gradlew.bat assembleDebug
```

## Current environment status

The Gradle test task was not run in this environment because no JDK is installed. The source and
unit tests were added but have not received compiler or runtime verification here.

## Device verification

1. Install the debug APK and open Portal.
2. Enter a relay connect credential and, if needed, an MCP authentication token, then save. Saving
   restarts Portal so the new configuration is loaded.
3. Confirm `filesDir/portal.toml` contains `connect`, optional `portal_mcp_token`, and
   `workspace = "./workspace"`; it must not contain `token`.
4. Start Portal and confirm the UI reports the process state, PID, and restart attempt. A RUNNING
   status means the child process is running and does not confirm a relay handshake.
5. Force enough abnormal child exits to reach FAILED, then tap Start and verify Portal is stopped
   before it is started again.
