# TASK-002 decisions

## Upstream source checked

The requested path `D:\Workspace\code\heart-portal` is absent on this workstation. The
available worktree is `D:\Workspace\code\portal-desktop\heart-portal`, confirmed on branch
`android-termux-support`. Its `portal/src/main.rs` and `portal/src/config.rs` were used as the
source of truth.

## CLI and configuration

- The launch argument is `--config <path>` (short form `-c` is also accepted). A positional
  config path is also supported, but Android uses the explicit long form.
- `status` is a subcommand, not a daemon health protocol. Android supervises the child process
  directly and does not invoke it.
- A relay credential is represented by `connect = "..."`, not `token`. The CLI equivalent is
  `--connect`; `PORTAL_CONNECT_LINK` is a supported environment override.
- The MCP TCP authentication value is `portal_mcp_token`, with `PORTAL_MCP_TOKEN` as an
  environment override.
- The recommended workspace key is `workspace = "./workspace"`. The supported nested alias is
  `[security] workspace_root = "./workspace"`; the flat key has precedence.

TASK-002 only launches the configured binary and creates `filesDir/workspace`. TASK-003 should
write `connect`, `portal_mcp_token` when applicable, and `workspace = "./workspace"` to the
private `portal.toml`; it must not invent a `token` key.

## Lifecycle policy

An exit status of zero is a clean stop and is not restarted. A non-zero exit, including a launch
failure, is retried after 5 seconds. The initial launch is not counted; at most three restart
attempts are made, after which the service reports `FAILED` and stops itself. Wake and high-
performance Wi-Fi locks remain held while running or waiting to restart, then release on a clean
stop, permanent failure, or service destruction.

The status broadcast reports Android's directly observable child-process state and PID. The
Portal executable does not expose its asynchronous relay handshake to this service, so `RUNNING`
does not claim that the relay has completed a connection. TASK-003 may display it as process
status; relay-specific status requires an explicit Portal status interface in a later task.
