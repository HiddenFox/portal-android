package town.beings.portal

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Moves recording files to a configured relay without applying business logic to their contents. */
interface DeliveryTransport {
    fun put(file: File, md5: String): Result<Unit>
}

class HttpPutTransport(
    private val endpoint: String,
    private val bearerToken: String? = null,
) : DeliveryTransport {
    override fun put(file: File, md5: String): Result<Unit> = runCatching {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("X-Delivery-Md5", md5)
            setRequestProperty("X-Delivery-File-Name", file.name)
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            file.inputStream().use { input -> connection.outputStream.use { input.copyTo(it) } }
            check(connection.responseCode in 200..299) { "HTTP PUT returned ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}

enum class DeliveryState { DISCOVERED, UPLOADING, UPLOADED, FAILED }

data class DeliveryLedgerEntry(
    val path: String,
    val md5: String,
    val state: DeliveryState,
    val attempts: Int = 0,
    val lastError: String? = null,
) {
    val key: String get() = "$path:$md5"
}

class DeliveryLedger(
    private val file: File,
    private val onLedgerRebuilt: () -> Unit = {},
) {
    private val entries = linkedMapOf<String, DeliveryLedgerEntry>()

    init {
        load()
    }

    @Synchronized
    fun entry(path: String, md5: String): DeliveryLedgerEntry? = entries["$path:$md5"]

    @Synchronized
    fun isUploaded(path: String, md5: String): Boolean = entry(path, md5)?.state == DeliveryState.UPLOADED

    @Synchronized
    fun markDiscovered(path: String, md5: String) {
        if (isUploaded(path, md5)) return
        write(DeliveryLedgerEntry(path, md5, DeliveryState.DISCOVERED, entry(path, md5)?.attempts ?: 0))
    }

    @Synchronized
    fun markUploading(path: String, md5: String) {
        if (isUploaded(path, md5)) return
        val previous = requireNotNull(entry(path, md5)) { "File must be discovered before upload" }
        write(previous.copy(state = DeliveryState.UPLOADING, lastError = null))
    }

    @Synchronized
    fun markUploaded(path: String, md5: String) {
        val previous = requireNotNull(entry(path, md5)) { "File must be discovered before upload" }
        write(previous.copy(state = DeliveryState.UPLOADED, lastError = null))
    }

    @Synchronized
    fun markFailed(path: String, md5: String, error: String) {
        val previous = requireNotNull(entry(path, md5)) { "File must be discovered before upload" }
        write(previous.copy(state = DeliveryState.FAILED, attempts = previous.attempts + 1, lastError = error))
    }

    @Synchronized
    private fun write(entry: DeliveryLedgerEntry) {
        entries[entry.key] = entry
        persist()
    }

    private fun load() {
        if (!file.isFile) return
        try {
            DeliveryLedgerJson.decode(file.readText()).forEach { entries[it.key] = it }
        } catch (error: Exception) {
            val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            if (!file.renameTo(backup)) file.copyTo(backup, overwrite = true).also { file.delete() }
            persist()
            onLedgerRebuilt()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(DeliveryLedgerJson.encode(entries.values))
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

}

/** Small JSON codec kept JVM-testable without an Android JSON implementation. */
object DeliveryLedgerJson {
    fun encode(entries: Collection<DeliveryLedgerEntry>): String = entries.joinToString(
        prefix = "{\"entries\":[",
        postfix = "]}",
    ) { entry ->
        "{\"path\":\"${escape(entry.path)}\",\"md5\":\"${escape(entry.md5)}\",\"state\":\"${entry.state.name.lowercase()}\",\"attempts\":${entry.attempts},\"last_error\":${entry.lastError?.let { "\"${escape(it)}\"" } ?: "null"}}"
    }

    fun decode(json: String): List<DeliveryLedgerEntry> {
        val trimmed = json.trim()
        require(trimmed.startsWith("{\"entries\":[") && trimmed.endsWith("]}")) { "Ledger is not valid JSON" }
        val content = trimmed.removePrefix("{\"entries\":[").removeSuffix("]}")
        if (content.isBlank()) return emptyList()
        val matches = ENTRY.findAll(content).toList()
        require(matches.joinToString(",") { it.value } == content) { "Ledger entries could not be decoded" }
        return matches.map { match ->
            DeliveryLedgerEntry(
                path = unescape(match.groupValues[1]),
                md5 = unescape(match.groupValues[2]),
                state = DeliveryState.valueOf(match.groupValues[3].uppercase()),
                attempts = match.groupValues[4].toInt(),
                lastError = match.groupValues[5].takeIf { it != "null" }?.removeSurrounding("\"")?.let(::unescape),
            )
        }
    }

    private val ENTRY = Regex("""\{"path":"((?:\\.|[^"])*)","md5":"((?:\\.|[^"])*)","state":"(discovered|uploading|uploaded|failed)","attempts":(\d+),"last_error":(null|"(?:\\.|[^"])*")\}""")

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    private fun unescape(value: String): String = value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
}

data class DeliveryRunResult(val failedAttempts: Int?)

class DeliveryGuard(
    private val sourceDirectory: File,
    private val ledger: DeliveryLedger,
    private val transport: DeliveryTransport,
) {
    fun run(): DeliveryRunResult {
        if (!sourceDirectory.isDirectory) {
            return DeliveryRunResult(null)
        }
        var greatestFailureAttempts: Int? = null
        sourceDirectory.walkTopDown().filter { it.isFile }.forEach { file ->
            val path = file.absolutePath
            val md5 = file.md5()
            if (ledger.isUploaded(path, md5)) return@forEach
            ledger.markDiscovered(path, md5)
            ledger.markUploading(path, md5)
            transport.put(file, md5).fold(
                onSuccess = { ledger.markUploaded(path, md5) },
                onFailure = { error ->
                    ledger.markFailed(path, md5, error.message ?: error.javaClass.simpleName)
                    greatestFailureAttempts = maxOf(greatestFailureAttempts ?: 0, ledger.entry(path, md5)?.attempts ?: 1)
                },
            )
        }
        return DeliveryRunResult(greatestFailureAttempts)
    }

    private fun File.md5(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class DeliveryWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): WorkResult {
        val config = PortalConfigStore(File(applicationContext.filesDir, PORTAL_CONFIG_NAME)).readDeliveryConfig()
        val endpoint = config.transportUrl
        if (endpoint == null) {
            Log.w(TAG, "Delivery scan skipped because transport_url is not configured")
            return WorkResult.success()
        }
        val result = DeliveryGuard(
            sourceDirectory = File(config.sourceDir),
            ledger = DeliveryLedger(File(applicationContext.filesDir, LEDGER_FILE_NAME)) {
                Log.w(TAG, "Delivery ledger was corrupt; it was backed up and rebuilt")
            },
            transport = HttpPutTransport(endpoint, config.transportBearerToken),
        ).run()
        result.failedAttempts?.let {
            Log.w(TAG, "Delivery run failed; retry scheduled after attempt=$it")
            DeliveryWorkScheduler.enqueueRetry(applicationContext, it)
        } ?: Log.i(TAG, "Delivery run completed")
        return WorkResult.success()
    }

    private companion object {
        const val TAG = "DeliveryWorker"
        const val PORTAL_CONFIG_NAME = "portal.toml"
        const val LEDGER_FILE_NAME = "delivery_ledger.json"
    }
}

object DeliveryWorkScheduler {
    private const val UNIQUE_WORK_NAME = "delivery-guard"
    private const val INITIAL_BACKOFF_SECONDS = 30L
    private const val MAX_BACKOFF_SECONDS = 10 * 60L

    fun enqueueScan(context: Context) = enqueue(context, 0)

    fun enqueueRetry(context: Context, attempts: Int) = enqueue(context, retryDelaySeconds(attempts))

    internal fun retryDelaySeconds(attempts: Int): Long = min(
        INITIAL_BACKOFF_SECONDS * (1L shl min((attempts - 1).coerceAtLeast(0), 5)),
        MAX_BACKOFF_SECONDS,
    )

    private fun enqueue(context: Context, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<DeliveryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        Log.i("DeliveryWorkScheduler", "Delivery work enqueued with delaySeconds=$delaySeconds")
    }
}
