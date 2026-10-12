# TASK-005 Decisions

- DeliveryGuard is a file delivery component only. It scans files, calculates MD5, transfers bytes, records delivery state, and schedules retries. It does not inspect recording contents or perform ASR, analysis, or CRM work.
- The ledger is `filesDir/delivery_ledger.json`. An entry is keyed by absolute path plus MD5 and contains `discovered`, `uploading`, `uploaded`, or `failed`, with attempt count and last error. An `uploaded` entry is never sent again.
- A successful HTTP PUT is the completion boundary. The app does not wait for a downstream acknowledgement from a workspace or processing system.
- The selected MVP transport is `HttpPutTransport`. `transport_url` is the required endpoint configuration; `transport_bearer_token` is optional and is sent only as an HTTP bearer credential. The file name and hexadecimal MD5 are sent in `X-Delivery-File-Name` and `X-Delivery-Md5` headers.
- `source_dir` defaults to `/storage/emulated/0/Recordings`. The MainActivity configuration screen writes both required configuration keys into `portal.toml`.
- Delivery runs are one-time WorkManager jobs with a connected-network constraint. There is no periodic work. A failed run enqueues the next one-time job with 30 seconds, 60 seconds, 120 seconds, and so on, capped at 10 minutes. This manual scheduling is used because WorkManager's built-in exponential-backoff maximum cannot be configured down to the required 10 minutes.
- A corrupt ledger is renamed to `delivery_ledger.json.corrupt-<timestamp>` and replaced by an empty ledger, so future scans can continue.
